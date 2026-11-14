package kz.barajyq.app.data

import android.content.Context
import kz.barajyq.app.BuildConfig
import kz.barajyq.app.domain.City
import kz.barajyq.app.domain.EventCard
import kz.barajyq.app.domain.EventSchedule
import kz.barajyq.app.domain.Interest
import kz.barajyq.app.domain.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads events/places from bundled assets/events.json and the remote JSON feed (BuildConfig.EVENTS_FEED_URL,
 * GitHub Pages of hexusArgon/barajyq-feed). The newest complete feed wins AS A WHOLE: an event removed on the
 * server disappears from the app too, the bundled copy is only the offline/first-start fallback.
 * The remote feed is re-fetched when the app comes back to the foreground and the last fetch is older than
 * [STALE_AFTER_MS], so a feed update reaches every installed app without a new release.
 *
 * The disk cache stores only the last successful REMOTE payload (offline start). It is ignored when
 * the bundled feed of a newer app version is fresher, so an app update always shows its own data
 * (older versions cached the merged bundled feed, which hid every data fix shipped in an update).
 */
object FeedRepository {
    private val mutex = Mutex()
    private val cache = AtomicReference<ParsedFeed?>(null)
    /** Wall-clock time of the last remote fetch attempt (successful or not); 0 = never. */
    @Volatile private var lastFetchAt = 0L
    private const val STALE_AFTER_MS = 15 * 60 * 1000L

    data class ParsedFeed(
        val events: List<EventCard>,
        val places: List<EventCard>,
        val updatedAt: String?,
        val source: String,
    )

    fun peek(): ParsedFeed? = cache.get()?.let(::sanitize)

    private fun sanitize(feed: ParsedFeed) = feed.copy(
        events = EventContentPolicy.clean(feed.events),
        places = EventContentPolicy.clean(feed.places),
    )

    suspend fun ensureLoaded(context: Context): ParsedFeed = mutex.withLock {
        cache.get()?.let { return sanitize(it) }
        load(context.applicationContext)
    }

    suspend fun refresh(context: Context): ParsedFeed = mutex.withLock { load(context.applicationContext) }

    /** Foreground refresh: re-fetches only when the last fetch is older than 15 min; null = nothing new to show. */
    suspend fun refreshIfStale(context: Context): ParsedFeed? = mutex.withLock {
        if (cache.get() != null && System.currentTimeMillis() - lastFetchAt < STALE_AFTER_MS) return null
        load(context.applicationContext)
    }

    // JSON parsing of ~2000 events is too heavy for the main thread, so loading always runs on Default/IO.
    private suspend fun load(app: Context): ParsedFeed = withContext(Dispatchers.Default) {
        val bundled = readAsset(app) ?: ParsedFeed(emptyList(), emptyList(), null, "empty")
        lastFetchAt = System.currentTimeMillis()
        val remote = fetchRemote()?.takeIf { it.events.isNotEmpty() && !isNewer(bundled, it) }
        if (remote != null) writeDisk(app, remote)
        val cachedRemote = if (remote == null) readDisk(app)?.takeIf { isNewer(it, bundled) } else null
        val merged = merge(bundled, cachedRemote, remote)
        cache.set(merged)
        merged
    }

    /** Remote cache wins only when its `updatedAt` is strictly later than the bundled feed's. */
    private fun isNewer(candidate: ParsedFeed, bundled: ParsedFeed): Boolean {
        fun stamp(f: ParsedFeed) = f.updatedAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        val a = stamp(candidate) ?: return false
        val b = stamp(bundled) ?: return true
        return a > b
    }

    fun forCity(feed: ParsedFeed, city: City, places: Boolean): List<EventCard> {
        val base = EventContentPolicy.clean(if (places) feed.places else feed.events)
        val cityItems = base.filter { it.cityId == city.id }
        if (cityItems.isNotEmpty()) return cityItems
        // Fallback to Fake* templates for cities without feed rows.
        return EventContentPolicy.clean(if (places) FakePlacesRepository.forCity(city) else FakeEventsRepository.forCity(city))
    }

    fun forProfile(feed: ParsedFeed, profile: UserProfile, city: City, places: Boolean, hidden: Set<String>): List<EventCard> =
        forCity(feed, city, places).filter { it.interest in profile.interests && it.id !in hidden }

    fun findById(feed: ParsedFeed, city: City, id: String): EventCard? =
        (forCity(feed, city, places = false) + forCity(feed, city, places = true)).firstOrNull { it.id == id }

    /** The newest complete feed replaces the bundled one entirely (places fall back to bundled if it has none). */
    private fun merge(bundled: ParsedFeed, disk: ParsedFeed?, remote: ParsedFeed?): ParsedFeed {
        fun byId(items: List<EventCard>) = items.associateBy { it.id }.toMutableMap()
        val base = remote ?: disk ?: bundled
        val ev = byId(base.events)
        val pl = byId(base.places.ifEmpty { bundled.places })
        val source = when {
            remote != null -> "remote"
            disk != null -> "disk"
            else -> "assets"
        }
        return ParsedFeed(
            events = EventContentPolicy.clean(ev.values.toList()),
            places = EventContentPolicy.clean(pl.values.toList()),
            updatedAt = remote?.updatedAt ?: disk?.updatedAt ?: bundled.updatedAt,
            source = source,
        )
    }

    private fun readAsset(context: Context): ParsedFeed? = runCatching {
        context.assets.open("events.json").bufferedReader().use { parse(it.readText(), "assets") }
    }.getOrNull()

    // v2: remote-only cache. The v1 file ("events_feed.json") held a merged bundled copy and is deleted.
    private fun diskFile(context: Context) = File(context.filesDir, "events_feed_remote.json").also {
        File(context.filesDir, "events_feed.json").takeIf { old -> old.exists() }?.delete()
    }

    private fun readDisk(context: Context): ParsedFeed? = runCatching {
        val f = diskFile(context)
        if (!f.exists()) return null
        parse(f.readText(), "disk")
    }.getOrNull()

    private fun writeDisk(context: Context, feed: ParsedFeed) {
        // Persist the last successful remote payload for the next offline cold start.
        runCatching {
            val root = JSONObject()
            root.put("version", 1)
            root.put("updatedAt", feed.updatedAt)
            root.put("events", JSONArray(feed.events.map { toJson(it) }))
            root.put("places", JSONArray(feed.places.map { toJson(it) }))
            diskFile(context).writeText(root.toString())
        }
    }

    private suspend fun fetchRemote(): ParsedFeed? = withContext(Dispatchers.IO) {
        val url = BuildConfig.EVENTS_FEED_URL.trim()
        if (url.isEmpty()) return@withContext null
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            conn.inputStream.bufferedReader().use { parse(it.readText(), "remote") }
        }.getOrNull()
    }

    private fun parse(raw: String, source: String): ParsedFeed {
        val root = JSONObject(raw)
        val updatedAt = root.optString("updatedAt").ifBlank { null }
        val events = root.optJSONArray("events").toCards()
        val places = root.optJSONArray("places").toCards()
        return ParsedFeed(events, places, updatedAt, source)
    }

    private fun JSONArray?.toCards(): List<EventCard> {
        if (this == null) return emptyList()
        val out = ArrayList<EventCard>(length())
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            out += fromJson(o)
        }
        return EventContentPolicy.clean(out)
    }

    private fun fromJson(o: JSONObject): EventCard {
        val interestName = o.optString("interest", Interest.FREE.name)
        val interest = runCatching { Interest.valueOf(interestName) }.getOrDefault(Interest.FREE)
        return EventCard(
            id = o.getString("id"),
            cityId = o.optString("cityId", "astana"),
            titleRu = o.optString("titleRu"),
            titleKk = o.optString("titleKk"),
            titleEn = o.optString("titleEn"),
            startsAt = o.optString("startsAt"),
            placeRu = o.optString("placeRu"),
            placeKk = o.optString("placeKk"),
            placeEn = o.optString("placeEn"),
            address = o.optString("address"),
            interest = interest,
            price = if (o.isNull("price")) null else EventContentPolicy.normalizePrice(o.optString("price")),
            whyMatches = "Интерес: ${interest.labelRu.lowercase()}",
            sourceUrl = o.optString("sourceUrl"),
            sourceName = o.optString("sourceName"),
            isPlace = o.optBoolean("isPlace", false),
            workingHours = o.optString("workingHours"),
            descriptionRu = o.optString("descriptionRu"),
            descriptionKk = o.optString("descriptionKk"),
            descriptionEn = o.optString("descriptionEn"),
            lat = o.optDoubleOrNull("lat"),
            lon = o.optDoubleOrNull("lon"),
            schedule = scheduleOf(o),
            imageUrl = o.optStringOrNull("imageUrl")?.takeIf { it.startsWith("https://") },
        )
    }

    private fun scheduleOf(o: JSONObject): EventSchedule? {
        if (o.optBoolean("isPlace", false)) return null
        val dates = o.optJSONArray("dates")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } } }.orEmpty()
        return EventSchedule.of(o.optStringOrNull("dateFrom"), o.optStringOrNull("dateTo"), dates, o.optStringOrNull("time"))
            ?: EventSchedule.fromIso(o.optString("startsAt"))
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).ifBlank { null }

    private fun toJson(e: EventCard): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("cityId", e.cityId)
        put("interest", e.interest.name)
        put("titleRu", e.titleRu); put("titleKk", e.titleKk); put("titleEn", e.titleEn)
        put("startsAt", e.startsAt)
        put("placeRu", e.placeRu); put("placeKk", e.placeKk); put("placeEn", e.placeEn)
        put("address", e.address)
        put("price", e.price)
        put("sourceUrl", e.sourceUrl)
        put("sourceName", e.sourceName)
        put("workingHours", e.workingHours)
        put("descriptionRu", e.descriptionRu)
        put("descriptionKk", e.descriptionKk)
        put("descriptionEn", e.descriptionEn)
        put("isPlace", e.isPlace)
        if (e.lat != null) put("lat", e.lat) else put("lat", JSONObject.NULL)
        if (e.lon != null) put("lon", e.lon) else put("lon", JSONObject.NULL)
        e.imageUrl?.let { put("imageUrl", it) }
        e.schedule?.let { sc ->
            put("dateFrom", sc.from.toString()); put("dateTo", sc.to.toString())
            put("dates", JSONArray(sc.dates.map { it.toString() }))
            put("time", sc.time?.toString() ?: JSONObject.NULL)
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }
}
