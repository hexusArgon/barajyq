package kz.astana.afisha.ui.feed

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.LocalDate
import kz.astana.afisha.R
import kz.astana.afisha.domain.AppLanguage
import kz.astana.afisha.domain.EventCard
import kz.astana.afisha.domain.Interest
import kz.astana.afisha.domain.Localize
import kz.astana.afisha.ui.common.icon

/** Placeholder tint per category, so cards without a cover still differ at a glance. */
private fun Interest.coverColors(): Pair<Color, Color> = when (this) {
    Interest.CONCERTS -> Color(0xFF6A3FB5) to Color(0xFFB07CF2)
    Interest.THEATER -> Color(0xFF9C2F45) to Color(0xFFE07A8E)
    Interest.EXHIBITIONS -> Color(0xFF8A6A1F) to Color(0xFFE2C26E)
    Interest.CINEMA -> Color(0xFF263B6B) to Color(0xFF6F8ED6)
    Interest.STANDUP -> Color(0xFFB4561F) to Color(0xFFF2A46B)
    Interest.BARS -> Color(0xFF4A2C5E) to Color(0xFFA26BC4)
    Interest.SPORT -> Color(0xFF1F6B3A) to Color(0xFF6FCB8B)
    Interest.OUTDOOR -> Color(0xFF2F6B5E) to Color(0xFF7CCFB9)
    Interest.IT -> Color(0xFF1F4F6B) to Color(0xFF6FB3DB)
    Interest.KIDS -> Color(0xFFB2457A) to Color(0xFFF29AC4)
    Interest.FESTIVALS -> Color(0xFF0E7C70) to Color(0xFF6FD3C4)
    Interest.FREE -> Color(0xFF3F6B1F) to Color(0xFF9CCF6F)
}

/** Cover of a card: the source image, or a category gradient with its icon while loading / on failure. */
@Composable
fun EventCover(event: EventCard, height: Dp, modifier: Modifier = Modifier) {
    var failed by remember(event.imageUrl) { mutableStateOf(false) }
    val (dark, light) = event.interest.coverColors()
    Box(modifier.fillMaxWidth().height(height).background(Brush.linearGradient(listOf(dark, light)))) {
        Icon(event.interest.icon, null, tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.Center).size(height / 3))
        if (event.imageUrl != null && !failed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(event.imageUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Small category label drawn over the cover. */
@Composable
fun CategoryBadge(interest: Interest, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.small, color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White,
        modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(interest.icon, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(interest.labelRes), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Round bookmark toggle over the cover (card and detail screen). */
@Composable
fun SaveButton(saved: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onToggle, shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White,
        modifier = modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                stringResource(if (saved) R.string.unsave else R.string.save), Modifier.size(22.dp))
        }
    }
}

/**
 * Compact feed card: cover, title, one "when · where" block, price and small actions.
 * Tap → detail screen with the full text and the source link.
 */
@Composable
fun CompactEventCard(
    event: EventCard,
    lang: AppLanguage?,
    whenText: String,
    soon: Boolean,
    saved: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onToggleSave: () -> Unit,
    onHide: () -> Unit,
    onPlan: () -> Unit,
    onRoute: () -> Unit,
) {
    ElevatedCard(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Box {
            EventCover(event, 148.dp)
            CategoryBadge(event.interest, Modifier.align(Alignment.BottomStart).padding(10.dp))
            SaveButton(saved, onToggleSave, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 2.dp)) {
            Text(event.title(lang), style = MaterialTheme.typography.titleMedium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 10.dp))
            Spacer(Modifier.height(4.dp))
            val firstLine = if (event.isPlace) Localize.hours(event.workingHours, lang) else whenText
            if (firstLine.isNotBlank()) {
                Text(firstLine, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (soon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 10.dp))
            }
            Text(event.place(lang), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.price?.let { Localize.price(it, lang) }.orEmpty(), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onRoute) {
                    Icon(Icons.Filled.Directions, stringResource(R.string.route_2gis), tint = MaterialTheme.colorScheme.primary)
                }
                if (!event.isPlace) IconButton(onClick = onPlan) {
                    Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.plan_event), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onHide) {
                    Icon(Icons.Filled.VisibilityOff, stringResource(R.string.not_interested),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Grey placeholder card shown while the feed loads. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha",
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    ElevatedCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.alpha(pulse)) {
            Box(Modifier.fillMaxWidth().height(148.dp).background(block))
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(18.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
                Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
                Box(Modifier.fillMaxWidth(0.65f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
            }
        }
    }
}

/** One-tap day picker under the search row: All · Today · Tomorrow · Fri 14 · … (two weeks). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayStrip(selected: LocalDate?, today: LocalDate, lang: AppLanguage?, onSelect: (LocalDate?) -> Unit) {
    val days = remember(today) { (0L until 14L).map { today.plusDays(it) } }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        item(key = "all") {
            FilterChip(selected = selected == null, onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.day_all)) })
        }
        items(days, key = { it.toString() }) { day ->
            FilterChip(selected = selected == day, onClick = { onSelect(if (selected == day) null else day) },
                label = { Text(Localize.dayChip(day, lang, today), maxLines = 1) })
        }
    }
}
