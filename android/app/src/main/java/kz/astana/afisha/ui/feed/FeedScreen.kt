package kz.astana.afisha.ui.feed

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kz.astana.afisha.ui.calendar.openCalendarEditor
import kz.astana.afisha.R
import kz.astana.afisha.data.FeedRepository
import kz.astana.afisha.data.SessionStore
import kz.astana.afisha.domain.EventCard
import kz.astana.afisha.domain.EventSchedule
import kz.astana.afisha.domain.Localize
import kz.astana.afisha.domain.AppLanguage
import kz.astana.afisha.ui.common.EmptyState
import kz.astana.afisha.ui.common.icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onOpenSettings: () -> Unit,
    onChangeCity: () -> Unit,
    onOpenEvent: (String) -> Unit = {},
    /** Id hidden from the detail screen: show the same "Hidden · Undo" snackbar here. */
    hiddenFromDetail: String? = null,
    onHiddenFromDetailShown: () -> Unit = {},
) {
    val profile by SessionStore.profile.collectAsState()
    val city by SessionStore.city.collectAsState()
    val lang by SessionStore.language.collectAsState()
    val hidden by SessionStore.hidden.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    var feed by remember { mutableStateOf(FeedRepository.peek()) }
    LaunchedEffect(Unit) {
        feed = FeedRepository.ensureLoaded(context)
    }
    var eventOptions by rememberSaveable(stateSaver = QueryOptionsSaver) { mutableStateOf(FeedQueryOptions.EVENTS_DEFAULT) }
    var placeOptions by rememberSaveable(stateSaver = QueryOptionsSaver) { mutableStateOf(FeedQueryOptions.PLACES_DEFAULT) }
    // One "today" per screen visit (UTC+5): past events are dropped and day groups are relative to it.
    val today = remember { EventSchedule.today() }
    val options = if (tab == 0) eventOptions else placeOptions
    fun updateOptions(next: FeedQueryOptions) { if (tab == 0) eventOptions = next else placeOptions = next }
    fun resetTools() {
        updateOptions(options.resetFilters().copy(query = ""))
    }
    val baseItems = profile?.let { p ->
        city?.let { c ->
            feed?.let { f ->
                FeedRepository.forProfile(f, p, c, places = tab != 0, hidden = hidden).filter { it.interest !in p.exclusions }
            }.orEmpty()
        }
    }.orEmpty()
    val items = FeedQuery.apply(baseItems, options, lang, city?.id.orEmpty(),
        (profile?.interests.orEmpty() - profile?.exclusions.orEmpty()), hidden, places = tab != 0, today = today)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hiddenMsg = stringResource(R.string.feed_hidden)
    val undoLabel = stringResource(R.string.undo)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    fun hideWithUndo(id: String) {
        SessionStore.hideEvent(id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(hiddenMsg, undoLabel, withDismissAction = true)
            if (r == SnackbarResult.ActionPerformed) SessionStore.unhideEvent(id)
        }
    }
    LaunchedEffect(hiddenFromDetail) {
        hiddenFromDetail?.let { id ->
            onHiddenFromDetailShown() // clears the key; the snackbar runs in the screen scope so it isn't cancelled
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(hiddenMsg, undoLabel, withDismissAction = true)
                if (r == SnackbarResult.ActionPerformed) SessionStore.unhideEvent(id)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BottomSection(tab == 0, Icons.Filled.Event, stringResource(R.string.tab_events),
                            Modifier.weight(1f)) { tab = 0 }
                        BottomSection(tab == 1, Icons.Filled.Explore, stringResource(R.string.tab_places),
                            Modifier.weight(1f)) { tab = 1 }
                    }
                }
            }
        },
        topBar = {
            Column {
                // Equal side reservations keep the city centered on the SCREEN, not between unequal actions.
                Box(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 48.dp).padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.28f))
                    Surface(onClick = onChangeCity, color = Color.Transparent,
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.44f).heightIn(min = 48.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(4.dp))
                            Text(city?.displayName(lang).orEmpty(), style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.settings_title))
                    }
                }
                key(tab) {
                    FeedTools(options = options, onChange = ::updateOptions, places = tab != 0,
                        categories = baseItems.map { it.interest }.distinct())
                }
            }
        },
    ) { pad ->
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad)) {
                EmptyState(
                    icon = if (tab == 0) Icons.Outlined.CalendarMonth else Icons.Filled.Explore,
                    title = stringResource(if (options.isNarrowed) R.string.tools_no_results else R.string.empty_title),
                    body = stringResource(if (options.isNarrowed) R.string.tools_no_results_hint else if (tab == 0) R.string.empty_events else R.string.empty_places),
                ) {
                    FilledTonalButton(onClick = { if (options.isNarrowed) resetTools() else onOpenSettings() }) {
                        Icon(Icons.Filled.Tune, null, Modifier.size(18.dp))
                        Text(stringResource(if (options.isNarrowed) R.string.tools_reset_all else R.string.empty_action), Modifier.padding(start = 8.dp))
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp),
            ) {
                item(key = "count") {
                    Text(
                        stringResource(if (tab == 0) R.string.feed_count_events else R.string.feed_count_places, items.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
                // Sorted by date → day groups ("Сегодня", "Завтра", …); other orders → one plain list.
                val groups: List<Pair<DaySection?, List<EventCard>>> =
                    if (tab == 0 && options.sort == FeedSort.NEAREST) FeedQuery.sections(items, today) else listOf(null to items)
                groups.forEachIndexed { groupIndex, (section, cards) ->
                    if (section != null) item(key = "section-${section.name}", contentType = "section") {
                        SectionHeader(stringResource(sectionLabel(section)), first = groupIndex == 0, modifier = Modifier.animateItem())
                    }
                    itemsIndexed(cards, key = { _, event -> event.id }, contentType = { _, _ -> "card" }) { index, event ->
                        EventCardView(
                            event = event,
                            lang = lang,
                            whenText = Localize.eventWhen(event, lang, today),
                            soon = FeedQuery.section(event, today).let { it == DaySection.TODAY || it == DaySection.TOMORROW },
                            modifier = Modifier.animateItem().padding(top = if (index == 0) 0.dp else 16.dp),
                            onOpen = { onOpenEvent(event.id) },
                            onHide = { hideWithUndo(event.id) },
                            onPlan = { openCalendarEditor(context, event, lang) },
                            onRoute = { open2Gis(context, event, city?.id, city?.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, first: Boolean, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = if (first) 4.dp else 20.dp, bottom = 8.dp))
}

private fun sectionLabel(section: DaySection) = when (section) {
    DaySection.TODAY -> R.string.section_today
    DaySection.TOMORROW -> R.string.section_tomorrow
    DaySection.THIS_WEEK -> R.string.section_this_week
    DaySection.LATER -> R.string.section_later
    DaySection.ONGOING -> R.string.section_ongoing
    DaySection.UNDATED -> R.string.section_undated
}

@Composable
private fun BottomSection(selected: Boolean, icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f, fill = false), maxLines = 2,
                style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCardView(
    event: EventCard,
    lang: AppLanguage?,
    whenText: String,
    soon: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onPlan: () -> Unit,
    onRoute: () -> Unit,
) {
    // Tap anywhere on the card → in-app detail screen (the browser opens only from there).
    ElevatedCard(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(event.interest.icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(event.interest.labelRes), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onHide, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.VisibilityOff, stringResource(R.string.not_interested), Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(event.title(lang), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!event.isPlace && whenText.isNotBlank()) {
                    InfoLine(Icons.Filled.Event, whenText, emphasize = true, highlight = soon)
                }
                InfoLine(Icons.Filled.Place, event.place(lang), secondary = event.address.takeIf { it.isNotBlank() && it != event.placeRu })
            }

            val description = previewText(event.description(lang), event.title(lang))
            if (description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event.workingHours.isNotBlank()) Pill(Icons.Filled.Schedule, Localize.hours(event.workingHours, lang))
                event.price?.let { Pill(Icons.Filled.Payments, Localize.price(it, lang)) }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRoute, modifier = Modifier.weight(1f).height(48.dp), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Filled.Directions, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.route_2gis), Modifier.padding(start = 6.dp), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(onClick = onOpen, modifier = Modifier.weight(1f).height(48.dp),
                    shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.details),
                        Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.source, event.sourceName), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onPlan) { Text(stringResource(R.string.plan_event)) }
            }
        }
    }
}

@Composable
internal fun InfoLine(icon: ImageVector, text: String, secondary: String? = null, emphasize: Boolean = false, highlight: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(20.dp).padding(top = 1.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text, style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            secondary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun Pill(icon: ImageVector, text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Two-line card preview: imported posts usually repeat the title as their first line, so leading
 * lines that only restate the title are skipped (the full text stays on the detail screen).
 */
internal fun previewText(description: String, title: String): String {
    fun key(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
    val t = key(title)
    if (t.isEmpty()) return description.trim()
    val rest = description.lineSequence()
        .dropWhile { line -> key(line).let { k -> k.isEmpty() || k == t || (k.length >= 8 && t.startsWith(k)) } }
        .joinToString("\n").trim()
    return rest.ifBlank { description.trim() }
}

/** Text fallback for 2GIS when an item has no coordinates: "place, address, city". */
internal fun routeQuery(event: EventCard, cityName: String?): String =
    listOf(event.placeRu, event.address, cityName.orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(", ")

internal fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** 2GIS links for the "Маршрут" button: (app deep link, web fallback). */
internal fun twoGisUrls(event: EventCard, cityId: String?, cityName: String?): Pair<String, String> {
    val slug = cityId?.takeIf { it.isNotBlank() } ?: "astana"
    val lat = event.lat
    val lon = event.lon
    return if (lat != null && lon != null) {
        val point = "$lon,$lat"
        // Official 2GIS deep link: destination point (lon,lat) + display name after the "╎" separator,
        // start = current location. Fills "Куда поедем" in the route panel.
        val name = Uri.encode(event.placeRu.ifBlank { event.address })
        "dgis://2gis.ru/routeSearch/rsType/car/to/$point\u254E$name" to
            "https://2gis.kz/$slug/directions/points/%7C${Uri.encode(point)}"
    } else {
        val q = Uri.encode(routeQuery(event, cityName))
        "dgis://2gis.ru/search/$q" to "https://2gis.kz/$slug/search/$q"
    }
}

internal fun open2Gis(context: Context, event: EventCard, cityId: String?, cityName: String?) {
    val (app, web) = twoGisUrls(event, cityId, cityName)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) { }
    }
}
