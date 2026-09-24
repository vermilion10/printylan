package com.printylan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.printylan.R
import com.printylan.driver.DriverId
import com.printylan.model.PortStatus
import com.printylan.ui.theme.PrintylanTheme
import com.printylan.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintersScreen(
    printers: List<PrinterCardState>,
    widthSizeClass: WindowWidthSizeClass,
    onOpenPrintSettings: () -> Unit,
    onAllowAccess: (localId: String) -> Unit,
    onDriverSelected: (localId: String, DriverId?) -> Unit,
    onPrintFile: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    // The FAB shows its label at the top of the list and shrinks to its icon once scrolled.
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val margin = Spacing.margin(widthSizeClass)
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPrintFile,
                expanded = fabExpanded,
                icon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                text = { Text(stringResource(R.string.print_file)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + margin,
                top = innerPadding.calculateTopPadding() + Spacing.xs,
                end = innerPadding.calculateEndPadding(layoutDirection) + margin,
                // Room for the FAB (56dp plus its 16dp margin) so it never covers the last card.
                bottom = innerPadding.calculateBottomPadding() + Spacing.xl + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "setup") { SetupCard(onOpenPrintSettings, Modifier.contentWidth()) }
            item(key = "header") {
                Text(
                    text = stringResource(R.string.section_printers),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .contentWidth()
                        .padding(top = Spacing.xs)
                        .semantics { heading() },
                )
            }
            if (printers.isEmpty()) {
                item(key = "empty") { EmptyState(Modifier.contentWidth()) }
            } else {
                items(printers, key = { it.localId }) { printer ->
                    PrinterCard(
                        state = printer,
                        onAllowAccess = { onAllowAccess(printer.localId) },
                        onDriverSelected = { onDriverSelected(printer.localId, it) },
                        modifier = Modifier.contentWidth(),
                    )
                }
            }
        }
    }
}

private fun Modifier.contentWidth() = widthIn(max = Spacing.maxContentWidth).fillMaxWidth()

@Composable
private fun SetupCard(onOpenPrintSettings: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xxs))
            Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.sm))
            Button(onClick = onOpenPrintSettings, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.setup_action))
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Usb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Spacing.xl),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PrinterCard(
    state: PrinterCardState,
    onAllowAccess: () -> Unit,
    onDriverSelected: (DriverId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier) {
        Column(Modifier.padding(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        imageVector = Icons.Outlined.Print,
                        contentDescription = null,
                        modifier = Modifier.padding(Spacing.xs),
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.usb_ids, state.vendorId, state.productId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                StatusLabel(state)
            }
            Spacer(Modifier.height(Spacing.sm))
            if (state.hasAccess) {
                DriverPicker(state.detectedDriver, state.overrideDriver, state.commandSet, onDriverSelected)
            } else {
                Text(
                    text = stringResource(R.string.access_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                FilledTonalButton(onClick = onAllowAccess, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.access_action))
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(state: PrinterCardState) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when {
        !state.hasAccess -> Triple(R.string.status_no_access, colors.surfaceContainerHighest, colors.onSurfaceVariant)
        state.probing -> Triple(R.string.status_checking, colors.surfaceContainerHighest, colors.onSurfaceVariant)
        else -> statusStyle(state.portStatus) ?: return
    }
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

@Composable
private fun statusStyle(status: PortStatus?): Triple<Int, Color, Color>? {
    val colors = MaterialTheme.colorScheme
    return when {
        status == null -> null
        status.paperEmpty -> Triple(R.string.status_out_of_paper, colors.errorContainer, colors.onErrorContainer)
        status.error -> Triple(R.string.status_error, colors.errorContainer, colors.onErrorContainer)
        !status.selected -> Triple(R.string.status_offline, colors.surfaceContainerHighest, colors.onSurfaceVariant)
        else -> Triple(R.string.status_ready, colors.secondaryContainer, colors.onSecondaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverPicker(
    detected: DriverId?,
    override: DriverId?,
    commandSet: List<String>,
    onSelected: (DriverId?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val autoLabel = detected?.let { stringResource(R.string.language_auto, it.displayName) }
        ?: stringResource(R.string.language_auto_none)
    val unsupported = detected == null && override == null
    val reported = commandSet.takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.language_reported, it.joinToString(", ")) }
    val supporting = listOfNotNull(
        stringResource(R.string.language_unsupported).takeIf { unsupported },
        reported,
    ).joinToString("\n").ifEmpty { null }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = override?.displayName ?: autoLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.language_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = if (supporting != null) {
                { Text(supporting) }
            } else {
                null
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(autoLabel) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            DriverId.entries.forEach { driver ->
                DropdownMenuItem(
                    text = { Text(driver.displayName) },
                    onClick = {
                        onSelected(driver)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrintersScreenPreview() {
    PrintylanTheme(dynamicColor = false) {
        PrintersScreen(
            printers = listOf(
                PrinterCardState(
                    localId = "usb-03f0-002a",
                    name = "HP LaserJet P1102",
                    vendorId = 0x03f0,
                    productId = 0x002a,
                    hasAccess = true,
                    probing = false,
                    portStatus = PortStatus(paperEmpty = false, selected = true, error = false),
                    detectedDriver = DriverId.PCL,
                    overrideDriver = null,
                    commandSet = listOf("PJL", "PCL"),
                ),
                PrinterCardState(
                    localId = "usb-04f9-0042",
                    name = "Brother HL-L2350DW",
                    vendorId = 0x04f9,
                    productId = 0x0042,
                    hasAccess = false,
                    probing = false,
                    portStatus = null,
                    detectedDriver = null,
                    overrideDriver = null,
                    commandSet = emptyList(),
                ),
            ),
            widthSizeClass = WindowWidthSizeClass.Compact,
            onOpenPrintSettings = {},
            onAllowAccess = {},
            onDriverSelected = { _, _ -> },
            onPrintFile = {},
        )
    }
}
