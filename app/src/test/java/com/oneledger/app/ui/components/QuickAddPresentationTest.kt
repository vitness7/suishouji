package com.oneledger.app.ui.components

import com.oneledger.app.data.local.AccountEntity
import com.oneledger.app.data.local.CategoryEntity
import com.oneledger.app.domain.model.AccountType
import com.oneledger.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickAddPresentationTest {
    @Test
    fun categoryTreeKeepsRootsSortedAndGroupsChildren() {
        val categories = listOf(
            category(id = "child-b", name = "水果", parentId = "food", order = 1),
            category(id = "shopping", name = "购物", order = 1),
            category(id = "food", name = "餐饮", order = 0),
            category(id = "child-a", name = "三餐", parentId = "food", order = 0),
        )

        val tree = quickAddCategoryTree(categories)

        assertEquals(listOf("food", "shopping"), tree.roots.map { it.id })
        assertEquals(listOf("child-a", "child-b"), tree.childrenOf("food").map { it.id })
    }

    @Test
    fun categoryTreeLeavesOrphanVisible() {
        val orphan = category(id = "orphan", name = "导入分类", parentId = "missing")

        val tree = quickAddCategoryTree(listOf(orphan))

        assertEquals(listOf("orphan"), tree.roots.map { it.id })
    }

    @Test
    fun categoryTreeAlwaysPlacesOtherLastForExpenseAndIncome() {
        listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
            val tree = quickAddCategoryTree(
                listOf(
                    category(id = "$type-other", name = "其他", order = 0, type = type),
                    category(id = "$type-first", name = "首项", order = 10, type = type),
                    category(id = "$type-second", name = "次项", order = 20, type = type),
                ),
            )

            assertEquals(
                listOf("$type-first", "$type-second", "$type-other"),
                tree.roots.map { it.id },
            )
        }
    }

    @Test
    fun expandingAnotherCategoryReplacesTheCurrentOneAndSameTapCollapses() {
        assertEquals(
            "food",
            toggledQuickAddCategory(null, tappedCategoryId = "food", hasChildren = true),
        )
        assertEquals(
            "transport",
            toggledQuickAddCategory("food", tappedCategoryId = "transport", hasChildren = true),
        )
        assertNull(
            toggledQuickAddCategory("transport", tappedCategoryId = "transport", hasChildren = true),
        )
    }

    @Test
    fun categoryWithoutChildrenCollapsesTheExpandedPanel() {
        assertNull(
            toggledQuickAddCategory(
                expandedCategoryId = "food",
                tappedCategoryId = "shopping",
                hasChildren = false,
            ),
        )
    }

    @Test
    fun accountSearchMatchesNameSubtitleAndType() {
        val accounts = listOf(
            account(id = "wallet", name = "小钱包", subtitle = "支付宝", type = AccountType.BANK),
            account(id = "cash", name = "现金", subtitle = "随身钱包", type = AccountType.CASH),
        )

        assertEquals(listOf("wallet"), filteredQuickAddAccounts(accounts, "支付").map { it.id })
        assertEquals(listOf("cash"), filteredQuickAddAccounts(accounts, "cash").map { it.id })
        assertEquals(accounts, filteredQuickAddAccounts(accounts, "  "))
    }

    private fun category(
        id: String,
        name: String,
        parentId: String? = null,
        order: Int = 0,
        type: String = TransactionType.EXPENSE,
    ) = CategoryEntity(
        id = id,
        bookId = "book",
        name = name,
        transactionType = type,
        iconKey = id,
        colorHex = 0,
        parentId = parentId,
        sortOrder = order,
        isSystem = true,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun account(
        id: String,
        name: String,
        subtitle: String,
        type: String,
    ) = AccountEntity(
        id = id,
        bookId = "book",
        name = name,
        subtitle = subtitle,
        type = type,
        colorHex = 0,
        openingBalanceMinor = 0,
        includeInNetWorth = true,
        sortOrder = 0,
        createdAt = 0,
        updatedAt = 0,
    )
}
