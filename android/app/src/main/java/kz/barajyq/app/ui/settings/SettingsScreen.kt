package kz.barajyq.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kz.barajyq.app.R
import kz.barajyq.app.data.SessionStore
import kz.barajyq.app.ui.common.SectionTitle
import kz.barajyq.app.ui.onboarding.InterestPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onChangeCity: () -> Unit,
    onChangeLanguage: () -> Unit,
) {
    val profile by SessionStore.profile.collectAsState()
    val city by SessionStore.city.collectAsState()
    val lang by SessionStore.language.collectAsState()
    val hidden by SessionStore.hidden.collectAsState()
    val interests = profile?.interests.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                SectionTitle(stringResource(R.string.settings_general))
                ElevatedCard(shape = MaterialTheme.shapes.large) {
                    SettingsRow(
                        icon = { Icon(Icons.Filled.Translate, null, tint = MaterialTheme.colorScheme.primary) },
                        title = stringResource(R.string.settings_language),
                        value = lang?.let { "${it.flag}  ${it.nativeName}" } ?: "—",
                        onClick = onChangeLanguage,
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = { Icon(Icons.Filled.LocationCity, null, tint = MaterialTheme.colorScheme.primary) },
                        title = stringResource(R.string.settings_city),
                        value = city?.displayName(lang) ?: "—",
                        onClick = onChangeCity,
                    )
                }
            }

            Column {
                SectionTitle(stringResource(R.string.settings_interests))
                ElevatedCard(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_interests_hint), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        InterestPicker(
                            selected = interests,
                            onToggle = { i ->
                                val next = if (i in interests) interests - i else interests + i
                                if (next.isNotEmpty()) SessionStore.setInterests(next)
                            },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            Column {
                SectionTitle(stringResource(R.string.settings_hidden))
                ElevatedCard(shape = MaterialTheme.shapes.large) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.VisibilityOff, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(stringResource(R.string.settings_hidden_count, hidden.size)) },
                        trailingContent = {
                            TextButton(enabled = hidden.isNotEmpty(), onClick = { SessionStore.clearHidden() }) {
                                Text(stringResource(R.string.settings_restore))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            Column {
                SectionTitle(stringResource(R.string.settings_account))
                ElevatedCard(shape = MaterialTheme.shapes.large) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.AccountCircle, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(if (profile?.googleId == kz.barajyq.app.ui.signin.GUEST_ID) stringResource(R.string.account_guest) else profile?.displayName ?: profile?.email ?: "Google") },
                        supportingContent = { profile?.email?.let { Text(it) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    OutlinedButton(
                        onClick = { SessionStore.signOut(); onSignedOut() },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 16.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null)
                        Text(stringResource(R.string.settings_sign_out), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: @Composable () -> Unit, title: String, value: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = icon,
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(value) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
