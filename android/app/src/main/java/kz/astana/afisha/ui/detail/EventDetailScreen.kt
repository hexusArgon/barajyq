package kz.astana.afisha.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import kz.astana.afisha.ui.feed.CategoryBadge
import kz.astana.afisha.ui.feed.EventCover
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kz.astana.afisha.ui.calendar.openCalendarEditor
import kz.astana.afisha.R
import kz.astana.afisha.data.FeedRepository
import kz.astana.afisha.data.SessionStore
import kz.astana.afisha.domain.EventCard
import kz.astana.afisha.domain.Localize
import kz.astana.afisha.ui.common.EmptyState
import kz.astana.afisha.ui.common.icon
import kz.astana.afisha.ui.feed.InfoLine
import kz.astana.afisha.ui.feed.Pill
import kz.astana.afisha.ui.feed.open2Gis
import kz.astana.afisha.ui.feed.openUrl

/**
 * In-app description of an event or a place: everything from the feed card plus the full text,
 * so the user doesn't have to leave for the browser. The source opens only from the explicit button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventDetailScreen(eventId: String, onBack: () -> Unit, onHidden: (String) -> Unit) {
    val city by SessionStore.city.collectAsState()
    val lang by SessionStore.language.collectAsState()
    val context = LocalContext.current
    var event by remember(eventId, city) { mutableStateOf<EventCard?>(null) }
    LaunchedEffect(eventId, city) {
        val feed = FeedRepository.ensureLoaded(context)
        event = city?.let { c -> FeedRepository.findById(feed, c, eventId) }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val saved by SessionStore.saved.collectAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        stringResource(if (event?.isPlace == true) R.string.place_description else R.string.event_description),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    event?.let { item ->
                        IconButton(onClick = { SessionStore.hideEvent(item.id); onHidden(item.id) }) {
                            Icon(Icons.Filled.VisibilityOff, stringResource(R.string.not_interested))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        // Actions stay pinned at the bottom while the description scrolls.
        bottomBar = {
            event?.let { e ->
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(onClick = { openUrl(context, e.sourceUrl) }, modifier = Modifier.weight(1f).height(48.dp),
                                shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                                Text(stringResource(if (e.isPlace) R.string.open_source else R.string.open_tickets_source),
                                    Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalIconButton(onClick = { open2Gis(context, e, city?.id, city?.name) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Directions, stringResource(R.string.route_2gis))
                            }
                            if (!e.isPlace) FilledTonalIconButton(onClick = { openCalendarEditor(context, e, lang) },
                                modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.Event, stringResource(R.string.plan_event))
                            }
                            val isSaved = e.id in saved
                            FilledTonalIconButton(onClick = { SessionStore.toggleSaved(e.id) }, modifier = Modifier.size(48.dp)) {
                                Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    stringResource(if (isSaved) R.string.unsave else R.string.save))
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        val e = event
        if (e == null) {
            Column(Modifier.fillMaxSize().padding(pad)) {
                EmptyState(icon = Icons.Outlined.Info, title = stringResource(R.string.empty_title),
                    body = stringResource(R.string.detail_not_found)) {
                    FilledTonalButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                }
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Cover with the category badge
            Box(Modifier.clip(MaterialTheme.shapes.large)) {
                EventCover(e, 210.dp)
                CategoryBadge(e.interest, Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            Text(e.title(lang), style = MaterialTheme.typography.headlineSmall)

            // Facts: when / where / hours / price / source
            ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val whenText = if (e.isPlace) "" else Localize.eventWhen(e, lang)
                    if (whenText.isNotBlank()) InfoLine(Icons.Filled.Event, whenText, emphasize = true)
                    InfoLine(Icons.Filled.Place, e.place(lang),
                        secondary = e.address.takeIf { it.isNotBlank() && it != e.placeRu })
                    if (e.workingHours.isNotBlank()) InfoLine(Icons.Filled.Schedule, Localize.hours(e.workingHours, lang))
                    e.price?.let { InfoLine(Icons.Filled.Payments, Localize.price(it, lang)) }
                    Text(stringResource(R.string.opens_in_browser, e.sourceName), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Full description
            val description = e.description(lang)
            if (description.isNotBlank()) {
                Column {
                    Text(
                        stringResource(if (e.isPlace) R.string.place_description else R.string.event_description),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
