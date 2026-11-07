package kz.astana.afisha.ui.city

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kz.astana.afisha.R
import kz.astana.afisha.data.SessionStore
import kz.astana.afisha.domain.City
import kz.astana.afisha.domain.KazakhstanCities
import kz.astana.afisha.ui.common.EmptyState
import kz.astana.afisha.ui.common.SectionTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CitySelectScreen(onSelected: (City) -> Unit, onBack: (() -> Unit)? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    val current by SessionStore.city.collectAsState()
    val lang by SessionStore.language.collectAsState()
    val list = KazakhstanCities.all.filter { it.matches(query) }
        .sortedBy { it.displayName(lang) }
    fun pick(c: City) { SessionStore.setCity(c); onSelected(c) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.city_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.city_subtitle), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.city_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
                if (query.isBlank()) {
                    item {
                        SectionTitle(stringResource(R.string.city_popular))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) {
                            KazakhstanCities.popular.forEach { c ->
                                val sel = c.id == current?.id
                                AssistChip(
                                    onClick = { pick(c) },
                                    label = { Text(c.displayName(lang)) },
                                    leadingIcon = { Icon(if (sel) Icons.Filled.Check else Icons.Filled.LocationCity, null,
                                        Modifier.padding(0.dp)) },
                                    colors = if (sel) AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ) else AssistChipDefaults.assistChipColors(),
                                )
                            }
                        }
                        SectionTitle(stringResource(R.string.city_all))
                    }
                }
                if (list.isEmpty()) {
                    item {
                        EmptyState(Icons.Filled.SearchOff, stringResource(R.string.city_nothing), stringResource(R.string.city_nothing_body))
                    }
                }
                items(list, key = { it.id }) { c ->
                    val sel = c.id == current?.id
                    Surface(
                        onClick = { pick(c) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        ListItem(
                            headlineContent = { Text(c.displayName(lang), style = MaterialTheme.typography.titleMedium) },
                            leadingContent = {
                                Icon(Icons.Filled.LocationCity, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingContent = { if (sel) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
