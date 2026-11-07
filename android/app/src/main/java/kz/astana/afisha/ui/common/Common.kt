package kz.astana.afisha.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kz.astana.afisha.domain.Interest

val Interest.icon: ImageVector
    get() = when (this) {
        Interest.CONCERTS -> Icons.Filled.MusicNote
        Interest.THEATER -> Icons.Filled.TheaterComedy
        Interest.EXHIBITIONS -> Icons.Filled.Museum
        Interest.CINEMA -> Icons.Filled.Movie
        Interest.STANDUP -> Icons.Filled.Mic
        Interest.BARS -> Icons.Filled.LocalBar
        Interest.SPORT -> Icons.Filled.SportsSoccer
        Interest.OUTDOOR -> Icons.AutoMirrored.Filled.DirectionsRun
        Interest.IT -> Icons.Filled.Computer
        Interest.KIDS -> Icons.Filled.ChildCare
        Interest.FESTIVALS -> Icons.Filled.Festival
        Interest.FREE -> Icons.Filled.MoneyOff
    }

/** Round tonal badge with an icon — used in heroes and empty states. */
@Composable
fun IconBadge(icon: ImageVector, size: Int = 72, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size((size / 2).dp))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, 88)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}
