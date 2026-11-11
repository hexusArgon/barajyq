package kz.barajyq.app.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kz.barajyq.app.R
import kz.barajyq.app.domain.Interest

val QueryOptionsSaver = listSaver<FeedQueryOptions, String>(save = { it.encode() }, restore = { FeedQueryOptions.decode(it) })

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedTools(
    options: FeedQueryOptions, onChange: (FeedQueryOptions) -> Unit,
    places: Boolean,
    categories: List<Interest>,
) {
    var dialog by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val searchLabel = stringResource(if (places) R.string.tools_search_places else R.string.tools_search_events)
    val filterLabel = if (options.filterCount == 0) stringResource(R.string.tools_filters)
        else stringResource(R.string.tools_active_filters, options.filterCount)
    val sortDescription = stringResource(R.string.tools_current_sort, stringResource(sortLabel(options.sort)))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = options.query, onValueChange = { onChange(options.copy(query = it)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).widthIn(min = 0.dp).heightIn(min = 48.dp)
                .semantics { contentDescription = searchLabel },
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).widthIn(min = 0.dp)) {
                        if (options.query.isEmpty()) Text(searchLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        innerTextField()
                    }
                    if (options.query.isNotEmpty()) IconButton(
                        onClick = { onChange(options.copy(query = "")) }, modifier = Modifier.size(48.dp),
                    ) { Icon(Icons.Filled.Clear, stringResource(R.string.tools_clear_search)) }
                }
            },
        )
        IconButton(onClick = { focusManager.clearFocus(); dialog = "filters" }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Tune, filterLabel,
                tint = if (options.filterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { focusManager.clearFocus(); dialog = "sort" }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Sort, sortDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (dialog == "filters") {
        FilterSheet(options, categories, places, onDismiss = { dialog = "" }, onApply = { onChange(it); dialog = "" })
    } else if (dialog == "sort") {
        AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(stringResource(R.string.tools_sort)) },
            text = { Column {
                (if (places) listOf(FeedSort.ORIGINAL, FeedSort.NAME) else listOf(FeedSort.NEAREST, FeedSort.NAME)).forEach { sort ->
                    ChoiceRow(stringResource(sortLabel(sort)), options.sort == sort) { onChange(options.copy(sort = sort)); dialog = "" }
                }
                if (!places) Text(stringResource(R.string.tools_sort_date_hint), style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text(stringResource(R.string.tools_cancel)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(options: FeedQueryOptions, categories: List<Interest>, places: Boolean,
    onDismiss: () -> Unit, onApply: (FeedQueryOptions) -> Unit) {
    var draft by rememberSaveable(stateSaver = QueryOptionsSaver) { mutableStateOf(options) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tools_filters), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.tools_scope_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.tools_categories), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tools_categories_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (categories + draft.categories).distinct().sortedBy { it.ordinal }.forEach { category ->
                    FilterChip(selected = category in draft.categories,
                        onClick = { draft = draft.copy(categories = if (category in draft.categories) draft.categories - category else draft.categories + category) },
                        modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(category.labelRes)) })
                }
            }
            if (!places) {
                Text(stringResource(R.string.tools_date), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateFilter.entries.forEach { date ->
                        FilterChip(selected = draft.date == date, onClick = { draft = draft.copy(date = date) },
                            modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(dateLabel(date))) })
                    }
                }
                Text(stringResource(R.string.tools_date_hint), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.tools_price), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriceFilter.entries.forEach { price ->
                    FilterChip(selected = draft.price == price, onClick = { draft = draft.copy(price = price) },
                        modifier = Modifier.heightIn(min = 48.dp), label = { Text(stringResource(priceLabel(price))) })
                }
            }
            Text(stringResource(R.string.tools_price_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { draft = draft.resetFilters() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.tools_reset_filters)) }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.tools_cancel)) }
                Button(onClick = { onApply(draft) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.tools_apply)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun sortLabel(value: FeedSort) = when (value) {
    FeedSort.ORIGINAL -> R.string.tools_sort_original
    FeedSort.NEAREST -> R.string.tools_sort_nearest
    FeedSort.NAME -> R.string.tools_sort_name
}
private fun priceLabel(value: PriceFilter) = when (value) {
    PriceFilter.ALL -> R.string.tools_all_prices
    PriceFilter.FREE -> R.string.tools_free
    PriceFilter.PAID -> R.string.tools_paid
    PriceFilter.UNKNOWN -> R.string.tools_price_unknown
}
private fun dateLabel(value: DateFilter) = when (value) {
    DateFilter.ANY -> R.string.tools_any_date
    DateFilter.TODAY -> R.string.tools_today
    DateFilter.TOMORROW -> R.string.tools_tomorrow
    DateFilter.WEEKEND -> R.string.tools_weekend
    DateFilter.NEXT_7_DAYS -> R.string.tools_next_7
    DateFilter.NEXT_30_DAYS -> R.string.tools_next_30
}
