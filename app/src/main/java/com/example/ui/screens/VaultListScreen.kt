package com.example.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.VaultSortOption
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import com.example.core.VaultGrouping
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.VaultCrypto
import com.example.model.ApiSecretPayload
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import com.example.ui.VaultNavTab
import com.example.ui.VaultViewModel
import com.example.ui.components.TotpLiveBadge
import com.example.ui.components.VaultTypeIcon
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.DangerRed
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay

@Composable
fun VaultListScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.filteredItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedType by viewModel.selectedTypeFilter.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val clockTicker by viewModel.clockTicker.collectAsState()
    val selectionActive by viewModel.selectionActive.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val groupByType by viewModel.groupByType.collectAsState()
    val allVaultItems by viewModel.allItems.collectAsState()

    var showCreateMenu by remember { mutableStateOf(false) }
    var showTotpSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBulkDelete by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionActive) { viewModel.clearSelection() }
    val sortOption by viewModel.sortOption.collectAsState()
    val listState = rememberLazyListState()

    // One UI large-title behaviour: the title collapses into a compact bar as the
    // content scrolls, and the pinned controls stay put.
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 56
        }
    }
    val titleSize by animateFloatAsState(if (collapsed) 20f else 34f, label = "vaultTitleSize")
    val headerPadding by animateDpAsState(if (collapsed) 6.dp else 14.dp, label = "vaultHeaderPad")
    val statusAlpha by animateFloatAsState(if (collapsed) 0f else 1f, label = "vaultStatusAlpha")

    // A shorter filtered list must start at the top: keeping the scroll offset
    // was the "category changed but the screen still shows something else" bug.
    LaunchedEffect(searchQuery, selectedType, favoritesOnly, sortOption) {
        listState.scrollToItem(0)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectionActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit selection",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.setSelectedFavorite(true) },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.testTag("bulk_favorite_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Mark selected as favourite",
                            tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { if (selectedIds.isNotEmpty()) showBulkDelete = true },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.testTag("bulk_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete selected",
                            tint = if (selectedIds.isNotEmpty()) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = headerPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Vault",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = titleSize.sp,
                            lineHeight = (titleSize * 1.15f).sp,
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(statusAlpha)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(CyberEmerald)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${items.size} ${if (items.size == 1) "item" else "items"} · AES-256 · unlocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showCreateMenu = true },
                    modifier = Modifier.size(40.dp).testTag("add_item_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add item",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(40.dp).testTag("sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Sort and view options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        VaultSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    viewModel.setSortOption(option)
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (option == sortOption) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Group by type") },
                            onClick = {
                                viewModel.setGroupByType(!groupByType)
                                showSortMenu = false
                            },
                            trailingIcon = {
                                if (groupByType) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("2FA codes") },
                            onClick = {
                                showTotpSheet = true
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Select items") },
                            onClick = {
                                viewModel.enterSelectionMode()
                                showSortMenu = false
                            }
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.lockVault() },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("quick_lock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock vault",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                }
            }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("vault_search_input"),
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // All Chip
                FilterChip(
                    selected = selectedType == null && !favoritesOnly,
                    onClick = {
                        viewModel.setTypeFilter(null)
                        if (favoritesOnly) viewModel.toggleFavoritesFilter()
                    },
                    label = { Text("All", fontSize = 12.sp) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedLabelColor = CyberEmerald
                    )
                )

                // Favorites Chip
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { viewModel.toggleFavoritesFilter() },
                    leadingIcon = {
                        Icon(
                            imageVector = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = { Text("Favorites", fontSize = 12.sp) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedLabelColor = CyberEmerald
                    )
                )

                // Types Chips
                for (type in VaultType.values()) {
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { viewModel.setTypeFilter(if (selectedType == type) null else type) },
                        label = { Text(type.displayName, fontSize = 12.sp) },
                        shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedLabelColor = CyberEmerald
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Items List
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val emptyTitle = when {
                            searchQuery.isNotEmpty() -> "No matches for \"$searchQuery\""
                            favoritesOnly -> "No favorites yet"
                            selectedType != null -> "No ${selectedType?.displayName?.lowercase()} yet"
                            else -> "Your vault is empty"
                        }
                        val emptyBody = when {
                            searchQuery.isNotEmpty() -> "Try a different term, or use type:login, is:weak or tag:work."
                            favoritesOnly -> "Tap the heart on any item to pin it here."
                            selectedType != null -> "Add an item of this type with the + button."
                            else -> "Tap the + button below to securely store your logins, cards, and secrets."
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = emptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = emptyBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (searchQuery.isNotEmpty() || selectedType != null || favoritesOnly) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearFilters()
                                    viewModel.setSearchQuery("")
                                },
                                shape = CircleShape
                            ) {
                                Text("Clear filters", fontSize = 13.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { showCreateMenu = true },
                                    shape = CircleShape,
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add item", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.setTab(VaultNavTab.GENERATOR) },
                                    shape = CircleShape,
                                ) {
                                    Text("Generator", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp)
                ) {
                    if (groupByType) {
                        VaultGrouping.group(items).forEach { section ->
                            item(key = "h-${section.type.name}") {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 8.dp)
                                )
                            }
                            item(key = "c-${section.type.name}") {
                                OneUiVaultCard(
                                    items = section.items,
                                    clockTicker = clockTicker,
                                    selectionActive = selectionActive,
                                    selectedIds = selectedIds,
                                    onTapItem = { item ->
                                        if (selectionActive) viewModel.toggleSelection(item.id) else viewModel.viewItem(item)
                                    },
                                    onLongItem = { item ->
                                        if (selectionActive) viewModel.toggleSelection(item.id)
                                        else viewModel.enterSelectionMode(item.id)
                                    },
                                    onToggleFavorite = { item -> viewModel.toggleFavorite(item) },
                                    onCopyPrimary = { item, label, value ->
                                        viewModel.copySecurely(label, value, isSensitive = true, itemId = item.id)
                                    },
                                    onCopyTotp = { item, code ->
                                        viewModel.copySecurely("2FA code", code, isSensitive = true, itemId = item.id)
                                    },
                                )
                            }
                        }
                    } else {
                        item(key = "all") {
                            OneUiVaultCard(
                                items = items,
                                clockTicker = clockTicker,
                                selectionActive = selectionActive,
                                selectedIds = selectedIds,
                                onTapItem = { item ->
                                    if (selectionActive) viewModel.toggleSelection(item.id) else viewModel.viewItem(item)
                                },
                                onLongItem = { item ->
                                    if (selectionActive) viewModel.toggleSelection(item.id)
                                    else viewModel.enterSelectionMode(item.id)
                                },
                                onToggleFavorite = { item -> viewModel.toggleFavorite(item) },
                                onCopyPrimary = { item, label, value ->
                                    viewModel.copySecurely(label, value, isSensitive = true, itemId = item.id)
                                },
                                onCopyTotp = { item, code ->
                                    viewModel.copySecurely("2FA code", code, isSensitive = true, itemId = item.id)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showBulkDelete) {
            AlertDialog(
                onDismissRequest = { showBulkDelete = false },
                title = { Text("Delete ${selectedIds.size} selected?") },
                text = {
                    Text(
                        "These encrypted records are removed from this device permanently. " +
                            "This cannot be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSelectedItems()
                            showBulkDelete = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDelete = false }) { Text("Cancel") }
                }
            )
        }

        if (showTotpSheet) {
            TotpDashboardDialog(
                items = allVaultItems,
                clockTicker = clockTicker,
                onCopy = { item, code ->
                    viewModel.copySecurely("2FA code", code, isSensitive = true, itemId = item.id)
                },
                onDismiss = { showTotpSheet = false },
            )
        }

        // Type Selection Modal Bottom Sheet / Dialog
        if (showCreateMenu) {
            CreateTypeSelectorDialog(
                onSelect = { type ->
                    showCreateMenu = false
                    viewModel.startCreateItem(type)
                },
                onDismiss = { showCreateMenu = false }
            )
        }
    }
}

@Composable
private fun OneUiVaultCard(
    items: List<VaultItem>,
    clockTicker: Long,
    selectionActive: Boolean,
    selectedIds: Set<String>,
    onTapItem: (VaultItem) -> Unit,
    onLongItem: (VaultItem) -> Unit,
    onToggleFavorite: (VaultItem) -> Unit,
    onCopyPrimary: (VaultItem, String, String) -> Unit,
    onCopyTotp: (VaultItem, String) -> Unit,
) {
    // One UI groups rows into one generously rounded container with hairline
    // dividers instead of a stack of separate cards.
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            items.forEachIndexed { index, item ->
                OneUiVaultRow(
                    item = item,
                    clockTicker = clockTicker,
                    selectionActive = selectionActive,
                    selected = item.id in selectedIds,
                    onClick = { onTapItem(item) },
                    onLongClick = { onLongItem(item) },
                    onToggleFavorite = { onToggleFavorite(item) },
                    onCopyPrimary = onCopyPrimary,
                    onCopyTotp = onCopyTotp,
                )
                if (index != items.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 68.dp)
                    )
                }
            }
        }
    }
}

private fun vaultItemSubtitle(item: VaultItem): String = when (val p = item.payload) {
    is LoginPayload -> p.username.ifEmpty { p.url }
    is CardPayload -> if (p.cardNumber.length >= 4) "•••• ${p.cardNumber.takeLast(4)}" else p.brand
    is ApiSecretPayload -> "${p.environment} · ${p.serviceName}"
    is CryptoPayload -> "${p.network} · ${if (p.publicAddress.length > 10) p.publicAddress.take(6) + "…" + p.publicAddress.takeLast(4) else p.walletName}"
    is NotePayload -> p.content.lines().firstOrNull() ?: "Encrypted note"
    is IdentityPayload -> "${p.idType} · ${p.fullName}"
}

private fun vaultItemCopyTarget(item: VaultItem): Pair<String, String> = when (val p = item.payload) {
    is LoginPayload -> "Password" to p.password
    is CardPayload -> "Card number" to p.cardNumber
    is ApiSecretPayload -> "API key" to p.apiKey.ifEmpty { p.secretToken }
    is CryptoPayload -> "Public address" to p.publicAddress
    is NotePayload -> "Note" to p.content
    is IdentityPayload -> "ID number" to p.idNumber
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OneUiVaultRow(
    item: VaultItem,
    clockTicker: Long,
    selectionActive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopyPrimary: (VaultItem, String, String) -> Unit,
    onCopyTotp: (VaultItem, String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("vault_item_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VaultTypeIcon(item.type)
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = vaultItemSubtitle(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!selectionActive && item.type == VaultType.LOGIN) {
                val login = item.payload as LoginPayload
                val totp = if (login.totpSecret.isNotBlank()) {
                    VaultCrypto.generateTotp(login.totpSecret, clockTicker)
                } else null
                if (totp != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                onClickLabel = "Copy 2FA code",
                            ) { onCopyTotp(item, totp.code) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2FA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val formatted = if (totp.code.length == 6) "${totp.code.substring(0, 3)} ${totp.code.substring(3)}" else totp.code
                        Text(
                            text = formatted,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${totp.remainingSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy 2FA code for ${item.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        if (selectionActive) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        } else {
            if (item.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            val (copyLabel, copyValue) = vaultItemCopyTarget(item)
            if (copyValue.isNotEmpty()) {
                IconButton(
                    onClick = { onCopyPrimary(item, copyLabel, copyValue) },
                    modifier = Modifier.size(32.dp).testTag("quick_copy_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $copyLabel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CreateTypeSelectorDialog(
    onSelect: (VaultType) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "New item",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose the type of record to encrypt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                for (type in VaultType.values()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(type) }
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VaultTypeIcon(type = type)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val desc = when (type) {
                                VaultType.LOGIN -> "Websites, apps, usernames & 2FA keys"
                                VaultType.CARD -> "Credit & debit cards, CVV & PINs"
                                VaultType.API_SECRET -> "API keys, access tokens & backend secrets"
                                VaultType.CRYPTO_WALLET -> "Seed phrases, private keys & wallets"
                                VaultType.NOTE -> "Encrypted notes & private text"
                                VaultType.IDENTITY -> "Passports, IDs & legal documents"
                            }
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
