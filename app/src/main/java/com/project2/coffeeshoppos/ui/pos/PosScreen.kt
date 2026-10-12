package com.project2.coffeeshoppos.ui.pos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.repository.shortOrderNumber
import com.project2.coffeeshoppos.domain.CartMath
import com.project2.coffeeshoppos.domain.Discount
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PosProduct
import com.project2.coffeeshoppos.domain.PricedCartLine
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.ConfirmDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.util.formatPeso

/** The POS tab: pick items, review the cart, take payment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    onOpenOrders: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    onOpenMenuSetup: () -> Unit,
    viewModel: PosViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var editingDiscount by rememberSaveable { mutableStateOf(false) }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)
    BackHandler(enabled = state.showCart) { viewModel.closeCart() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.showCart) "Current order" else "New order") },
                navigationIcon = {
                    if (state.showCart) {
                        IconButton(onClick = viewModel::closeCart) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to menu")
                        }
                    }
                },
                actions = {
                    if (state.showCart) {
                        TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                    } else {
                        TextButton(onClick = onOpenOrders) {
                            BadgedBox(badge = { if (state.activeOrderCount > 0) Badge { Text("${state.activeOrderCount}") } }) {
                                Icon(painterResource(R.drawable.ic_receipt), contentDescription = null)
                            }
                            Text("Orders", modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (!state.showCart && state.cart.isNotEmpty()) {
                CartBar(itemCount = state.itemCount, totalCentavos = state.totalCentavos, onClick = viewModel::openCart)
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.showCart -> CartContent(
                state = state,
                viewModel = viewModel,
                onEditDiscount = { editingDiscount = true },
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
            )
            else -> MenuContent(state, viewModel, onOpenMenuSetup, Modifier.padding(innerPadding))
        }
    }

    state.editor?.let { editor ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeEditor,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ItemEditor(editor, viewModel)
        }
    }

    if (editingDiscount) {
        DiscountDialog(
            current = state.checkout.discount,
            currentReason = state.checkout.discountReason,
            onApply = { discount, reason -> viewModel.onDiscount(discount, reason); editingDiscount = false },
            onDismiss = { editingDiscount = false }
        )
    }

    CustomerPickerDialog(state, viewModel)

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear this order?",
            message = "All items, the discount, customer and payment details will be removed.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = { confirmClear = false; viewModel.clearCart() },
            onDismiss = { confirmClear = false }
        )
    }

    state.placed?.let { placed ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPlaced,
            title = { Text("Order ${shortOrderNumber(placed.orderNumber)} placed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total ${formatPeso(placed.totalCentavos)}", style = MaterialTheme.typography.titleMedium)
                    placed.changeCentavos?.let {
                        Text("Change: ${formatPeso(it)}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    state.placedLoyaltyNote?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissPlaced) { Text("Next order") } },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPlaced(); onOpenOrder(placed.orderId) }) { Text("View order") }
            }
        )
    }
}

// ---------- Menu ----------

@Composable
private fun MenuContent(state: PosUiState, viewModel: PosViewModel, onOpenMenuSetup: () -> Unit, modifier: Modifier) {
    if (state.menu.products.isEmpty()) {
        Box(modifier.fillMaxSize().padding(16.dp)) {
            CoffeeCard(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        icon = R.drawable.ic_cafe,
                        title = "Nothing to sell yet",
                        message = "Add products with prices, or mark sold-out items as available again."
                    )
                    OutlinedButton(onClick = onOpenMenuSetup, modifier = Modifier.padding(bottom = 16.dp)) { Text("Set up the menu") }
                }
            }
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(selected = state.categoryId == null, onClick = { viewModel.onCategoryChange(null) }, label = { Text("All") })
            }
            val usedCategories = state.menu.products.mapNotNull { it.categoryId }.toSet()
            items(state.menu.categories.filter { it.first in usedCategories }, key = { it.first }) { (id, name) ->
                FilterChip(selected = state.categoryId == id, onClick = { viewModel.onCategoryChange(id) }, label = { Text(name) })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.visibleProducts, key = { it.id }) { product ->
                ProductTile(product, onClick = { viewModel.onProductTap(product) })
            }
        }
    }
}

@Composable
private fun ProductTile(product: PosProduct, onClick: () -> Unit) {
    val prices = product.sizes.map { it.priceCentavos }
    CoffeeCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.clickable(onClick = onClick).heightIn(min = 96.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (prices.min() == prices.max()) formatPeso(prices.min()) else "from ${formatPeso(prices.min())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (product.sizes.size > 1) {
                Text(
                    "${product.sizes.size} sizes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CartBar(itemCount: Int, totalCentavos: Long, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 56.dp)
        ) {
            Text("$itemCount " + if (itemCount == 1) "item" else "items", modifier = Modifier.weight(1f))
            Text("Review · ${formatPeso(totalCentavos)}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------- Item sheet ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemEditor(editor: ItemEditorState, viewModel: PosViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(editor.product.name, style = MaterialTheme.typography.titleLarge)
        if (editor.product.sizes.size > 1) {
            SectionHeader("Size")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                editor.product.sizes.forEach { size ->
                    FilterChip(
                        selected = editor.sizeId == size.id,
                        onClick = { viewModel.onEditorSize(size.id) },
                        label = { Text("${size.name} · ${formatPeso(size.priceCentavos)}") }
                    )
                }
            }
        }
        if (editor.product.addOns.isNotEmpty()) {
            SectionHeader("Add-ons")
            editor.product.addOns.forEach { addOn ->
                val count = editor.addOns[addOn.id] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(addOn.name, style = MaterialTheme.typography.bodyLarge)
                        Text("+${formatPeso(addOn.priceCentavos)} each", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    QuantityStepper(
                        value = count,
                        onValueChange = { viewModel.onEditorAddOn(addOn.id, it) },
                        label = addOn.name,
                        min = 0,
                        max = 10
                    )
                }
            }
        }
        OutlinedTextField(
            value = editor.note,
            onValueChange = viewModel::onEditorNote,
            label = { Text("Note (optional)") },
            placeholder = { Text("e.g. less ice, no sugar") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quantity", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            QuantityStepper(value = editor.quantity, onValueChange = viewModel::onEditorQuantity, label = "quantity")
        }
        Button(onClick = viewModel::confirmEditor, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(
                (if (editor.editingKey == null) "Add to order" else "Update item") + " · " + formatPeso(editor.totalCentavos),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

// ---------- Cart and checkout ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartContent(state: PosUiState, viewModel: PosViewModel, onEditDiscount: () -> Unit, modifier: Modifier) {
    val checkout = state.checkout
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CoffeeCard(Modifier.fillMaxWidth()) {
                Column {
                    state.cart.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CartLineRow(
                            line = line,
                            onEdit = { viewModel.onEditLine(line.line.key) },
                            onQuantity = { viewModel.onLineQuantity(line.line.key, it) }
                        )
                    }
                }
            }
        }
        if (state.rules.isEnabled || state.customer != null) {
            item { LoyaltyCartCard(state, viewModel) }
        }
        item {
            CoffeeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TotalRow("Subtotal", formatPeso(state.subtotalCentavos))
                    if (state.rewardCentavos > 0) TotalRow("Loyalty free drink", "−${formatPeso(state.rewardCentavos)}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Discount", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (checkout.discount != Discount.None) {
                                Text(
                                    listOfNotNull(
                                        (checkout.discount as? Discount.Percent)?.let { "${it.percent}%" },
                                        checkout.discountReason
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (state.discountCentavos > 0) {
                            Text("−${formatPeso(state.discountCentavos)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = onEditDiscount) { Text(if (checkout.discount == Discount.None) "Add" else "Edit") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row {
                        Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(formatPeso(state.totalCentavos), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = checkout.note,
                onValueChange = viewModel::onNote,
                label = { Text("Order note (optional)") },
                placeholder = { Text("e.g. customer name, table 4") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { SectionHeader("Payment") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { method ->
                    FilterChip(selected = checkout.method == method, onClick = { viewModel.onMethod(method) }, label = { Text(method.label) })
                }
            }
        }
        item {
            if (checkout.method == PaymentMethod.CASH) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = checkout.cashText,
                        onValueChange = viewModel::onCashText,
                        label = { Text("Cash received") },
                        prefix = { Text("₱") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CartMath.suggestedCash(state.totalCentavos).forEachIndexed { index, amount ->
                            SuggestionChip(
                                onClick = { viewModel.onQuickCash(amount) },
                                label = { Text(if (index == 0) "Exact" else formatPeso(amount)) }
                            )
                        }
                    }
                    val change = state.changeCentavos
                    when {
                        change != null -> Text(
                            "Change: ${formatPeso(change)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        state.cashCentavos != null -> Text(
                            "Short by ${formatPeso(state.totalCentavos - state.cashCentavos!!)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = checkout.reference,
                    onValueChange = viewModel::onReference,
                    label = { Text("Reference no. (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            CoffeeCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .clickable { viewModel.onCompleteNow(!checkout.completeNow) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Hand over now", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Mark completed right away instead of adding it to the order queue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = checkout.completeNow, onCheckedChange = viewModel::onCompleteNow)
                }
            }
        }
        item {
            Button(
                onClick = viewModel::charge,
                enabled = !state.isPlacing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(
                    if (state.isPlacing) "Placing order…" else "Charge ${formatPeso(state.totalCentavos)}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun CartLineRow(line: PricedCartLine, onEdit: () -> Unit, onQuantity: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            line.addOns.forEach {
                Text(
                    "+ ${if (it.quantityPerItem > 1) "${it.quantityPerItem}× " else ""}${it.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (line.line.note.isNotBlank()) {
                Text("“${line.line.note}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (line.unavailableReason != null) {
                Text(line.unavailableReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text(formatPeso(line.lineTotalCentavos), style = MaterialTheme.typography.bodyMedium)
            }
        }
        // Minus at 1 removes the line.
        QuantityStepper(value = line.line.quantity, onValueChange = onQuantity, label = line.title, min = 0)
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
