package kz.astana.afisha.data

import android.content.Context
import kz.astana.afisha.BuildConfig
import kz.astana.afisha.domain.City
import kz.astana.afisha.domain.EventCard
import kz.astana.afisha.domain.Interest
import kz.astana.afisha.domain.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads events/places from bundled assets/events.json, optional remote JSON feed
 * (BuildConfig.EVENTS_FEED_URL), and a disk cache. Remote items override bundled by id.
 */
object FeedRepository {
    private val mutex = Mutex()
    private val cache = AtomicReference<ParsedFeed?>(null)

    data class ParsedFeed(
        val events: List<EventCard>,
        val places: List<EventCard>,
        val updatedAt: String?,
        val source: String,
    )

    fun peek(): ParsedFeed? = cache.get()

    suspend fun ensureLoaded(context: Context): ParsedFeed = mutex.withLock {
        cache.get()?.let { return it }
        val app = context.applicationContext
        val bundled = readAsset(app) ?: ParsedFeed(emptyList(), emptyList(), null, "empty")
        val cachedDisk = readDisk(app)
        val remote = fetchRemote()
        val merged = merge(bundled, cachedDisk, remote)
        writeDisk(app, merged)
        cache.set(merged)
        merged
    }

    suspend fun refresh(context: Context): ParsedFeed = mutex.withLock {
        val app = context.applicationContext
        val bundled = readAsset(app) ?: ParsedFeed(emptyList(), emptyList(), null, "empty")
        val remote = fetchRemote()
        val merged = merge(bundled, null, remote)
        writeDisk(app, merged)
        cache.set(merged)
        merged
    }

    fun forCity(feed: ParsedFeed, city: City, places: Boolean): List<EventCard> {
        val base = if (places) feed.places else feed.events
        val cityItems = base.filter { it.cityId == city.id }
        if (cityItems.isNotEmpty()) return cityItems
        // Fallback to Fake* templates for cities without feed rows.
        return if (places) FakePlacesRepository.forCity(city) else FakeEventsRepository.forCity(city)
    }

    fun forProfile(feed: ParsedFeed, profile: UserProfile, city: City, places: Boolean, hidden: Set<String>): List<EventCard> =
        forCity(feed, city, places).filter { it.interest in profile.interests && it.id !in hidden }

    fun findById(feed: ParsedFeed, city: City, id: String): EventCard? =
        (forCity(feed, city, places = false) + forCity(feed, city, places = true)).firstOrNull { it.id == id }

    private fun merge(bundled: ParsedFeed, disk: ParsedFeed?, remote: ParsedFeed?): ParsedFeed {
        fun byId(items: List<EventCard>) = items.associateBy { it.id }.toMutableMap()
        val ev = byId(bundled.events)
        val pl = byId(bundled.places)
        disk?.events?.forEach { ev[it.id] = it }
        disk?.places?.forEach { pl[it.id] = it }
        remote?.events?.forEach { ev[it.id] = it }
        remote?.places?.forEach { pl[it.id] = it }
        val source = when {
            remote != null -> "remote"
            disk != null -> "disk"
            else -> "assets"
        }
        return ParsedFeed(
            events = ev.values.toList(),
            places = pl.values.toList(),
            updatedAt = remote?.updatedAt ?: disk?.updatedAt ?: bundled.updatedAt,
            source = source,
        )
    }

    private fun readAsset(context: Context): ParsedFeed? = runCatching {
        context.assets.open("events.json").bufferedReader().use { parse(it.readText(), "assets") }
    }.getOrNull()

    private fun diskFile(context: Context) = File(context.filesDir, "events_feed.json")

    private fun readDisk(context: Context): ParsedFeed? = runCatching {
        val f = diskFile(context)
        if (!f.exists()) return null
        parse(f.readText(), "disk")
    }.getOrNull()

    private fun writeDisk(context: Context, feed: ParsedFeed) {
        // Persist last successful remote/bundled merge as JSON array envelope for next cold start.
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
        return out
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
            price = o.optString("price").ifBlank { null },
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
        )
    }

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
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }
}
