package com.oneledger.app.data.repository

import com.oneledger.app.ui.components.quickAddCategoryTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoryCatalogTest {
    @Test
    fun expenseCatalogContainsScreenshotRootsAndSixExpandableGroups() {
        val categories = defaultExpenseCategories(bookId = "book", now = 1)
        val tree = quickAddCategoryTree(categories)

        assertEquals(
            listOf(
                "餐饮", "购物", "服饰", "日用", "数码",
                "美妆", "护肤", "应用软件", "住房", "交通",
                "娱乐", "医疗", "通讯", "汽车", "学习",
                "办公", "运动", "社交", "人情", "育儿",
                "宠物", "旅行", "度假", "烟酒", "彩票",
                "其他",
            ),
            tree.roots.map { it.name },
        )
        assertEquals(
            setOf(
                "expense-food",
                "expense-housing",
                "expense-transport",
                "expense-health",
                "expense-study",
                "expense-relationship",
            ),
            tree.childrenByParent.keys,
        )
        assertTrue(tree.childrenByParent.values.all { it.size == 4 })
    }

    @Test
    fun incomeCatalogContainsAllRequestedCategories() {
        assertEquals(
            listOf(
                "工资", "奖金", "加班", "福利", "公积金", "红包",
                "兼职", "副业", "退税", "投资", "意外收入", "其他",
            ),
            defaultIncomeCategories(bookId = "book", now = 1).map { it.name },
        )
    }

    @Test
    fun catalogHasStableUniqueIdsNamesAndValidParentReferences() {
        val categories = defaultExpenseCategories("book", 1) + defaultIncomeCategories("book", 1)
        val ids = categories.map { it.id }
        val namesByType = categories.groupBy { it.transactionType }.mapValues { (_, values) ->
            values.map { it.name }
        }

        assertEquals(ids.size, ids.distinct().size)
        assertTrue(namesByType.values.all { names -> names.size == names.distinct().size })
        assertTrue(categories.filter { it.parentId != null }.all { it.parentId in ids })
        assertTrue(categories.all { it.bookId == "book" && it.isSystem })
    }
}
