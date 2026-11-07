package kz.astana.afisha.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kz.astana.afisha.R
import kz.astana.afisha.data.SessionStore
import kz.astana.afisha.domain.Interest
import kz.astana.afisha.ui.common.icon

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestPicker(selected: Set<Interest>, onToggle: (Interest) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Interest.entries.forEach { i ->
            val sel = i in selected
            FilterChip(
                selected = sel,
                onClick = { onToggle(i) },
                label = { Text(stringResource(i.labelRes), modifier = Modifier.padding(vertical = 10.dp)) },
                leadingIcon = {
                    Icon(if (sel) Icons.Filled.Check else i.icon, null, Modifier.size(FilterChipDefaults.IconSize))
                },
                shape = MaterialTheme.shapes.medium,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var interests by rememberSaveable { mutableStateOf(SessionStore.profile.value?.interests.orEmpty().toList()) }
    val set = interests.toSet()

    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Button(
                    enabled = set.isNotEmpty(),
                    onClick = { SessionStore.saveOnboarding(set); onDone() },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                ) {
                    Text(
                        if (set.isEmpty()) stringResource(R.string.onboarding_pick_one)
                        else stringResource(R.string.onboarding_continue, set.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            InterestPicker(
                selected = set,
                onToggle = { i -> interests = if (i in set) interests - i else interests + i },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
