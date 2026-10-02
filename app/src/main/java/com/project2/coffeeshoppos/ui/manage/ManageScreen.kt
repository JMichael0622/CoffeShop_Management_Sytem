package com.project2.coffeeshoppos.ui.manage

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.IconBadge
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.theme.CoffeeShopPOSTheme

/** Hub for everything that is not a daily task: menu setup, backups, security. */
@Composable
fun ManageScreen(
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenAddOns: () -> Unit,
    viewModel: ManageViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val status by viewModel.databaseStatus.collectAsStateWithLifecycle()
    val menuItems = listOf(
        ManageItem(
            "Products & categories", "Menu items, sizes, prices, availability", R.drawable.ic_cafe,
            onClick = onOpenMenu
        ),
        ManageItem("Add-ons", "Extras like an extra shot or oat milk", R.drawable.ic_discount, onClick = onOpenAddOns),
    )
    ManageContent(status = status, menuItems = menuItems, onBack = onBack)
}

/** A row in the hub. Rows without [onClick] are not built yet and show [availableIn] instead. */
private data class ManageItem(
    val title: String,
    val subtitle: String,
    @param:DrawableRes val icon: Int,
    val availableIn: String = "",
    val onClick: (() -> Unit)? = null,
)

private val inventoryItems = listOf(
    ManageItem("Recipes", "Ingredients used by each product", R.drawable.ic_nav_inventory, "Phase 4"),
)
private val dataItems = listOf(
    ManageItem("Export reports", "Save CSV or PDF files", R.drawable.ic_nav_reports, "Phase 8"),
    ManageItem("Backup & restore", "Save a copy of all shop data", R.drawable.ic_receipt, "Phase 8"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageContent(status: DatabaseStatus, menuItems: List<ManageItem>, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Manage shop") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionHeader("Menu") }
            item { ManageGroup(menuItems) }
            item { SectionHeader("Inventory") }
            item { ManageGroup(inventoryItems) }
            item { SectionHeader("Data") }
            item { ManageGroup(dataItems) }
            item { SectionHeader("Database") }
            item { DatabaseStatusCard(status) }
        }
    }
}

@Composable
private fun ManageGroup(items: List<ManageItem>) {
    CoffeeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier)
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(icon = item.icon)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.onClick != null) {
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Not built yet, so the row is not clickable and says when it arrives.
                        StatusChip(item.availableIn)
                    }
                }
            }
        }
    }
}

@Composable
private fun DatabaseStatusCard(status: DatabaseStatus) {
    CoffeeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (status) {
                DatabaseStatus.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Opening database…", style = MaterialTheme.typography.bodyMedium)
                }

                is DatabaseStatus.Error -> {
                    Text(
                        "Database error",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(status.message, style = MaterialTheme.typography.bodySmall)
                }

                is DatabaseStatus.Ready -> {
                    Text(
                        "Stored on this phone · schema v${status.schemaVersion}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    CountRow("Products", status.products)
                    CountRow("Ingredients", status.ingredients)
                    CountRow("Orders", status.orders)
                    CountRow("Expenses", status.expenses)
                }
            }
        }
    }
}

@Composable
private fun CountRow(label: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = count.toString(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF5EC, heightDp = 900)
@Composable
private fun ManageContentPreview() {
    CoffeeShopPOSTheme(darkTheme = false) {
        ManageContent(
            status = DatabaseStatus.Ready(1, products = 0, ingredients = 0, orders = 0, expenses = 0),
            menuItems = listOf(ManageItem("Products & categories", "Menu items", R.drawable.ic_cafe, onClick = {})),
            onBack = {}
        )
    }
}
