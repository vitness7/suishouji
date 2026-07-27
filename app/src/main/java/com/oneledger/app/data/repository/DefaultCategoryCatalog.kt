package com.oneledger.app.data.repository

import com.oneledger.app.data.local.CategoryEntity
import com.oneledger.app.domain.model.TransactionType

/**
 * OneLedger's built-in category catalog.
 *
 * IDs are stable so an existing installation can receive missing categories with INSERT IGNORE
 * without replacing user edits or touching categories that are already present.
 */
internal fun defaultExpenseCategories(
    bookId: String,
    now: Long,
): List<CategoryEntity> = buildList {
    add(expense("expense-food", "餐饮", "food", 0xFFFF6B5F, 0, bookId, now))
    add(expense("expense-shopping", "购物", "shopping", 0xFFFFA94D, 1, bookId, now))
    add(expense("expense-clothes", "服饰", "clothes", 0xFF9B7BFF, 2, bookId, now))
    add(expense("expense-daily", "日用", "daily", 0xFF57D3A2, 3, bookId, now))
    add(expense("expense-digital", "数码", "digital", 0xFF29B6F6, 4, bookId, now))
    add(expense("expense-beauty", "美妆", "beauty", 0xFFFF7AA2, 5, bookId, now))
    add(expense("expense-skincare", "护肤", "skincare", 0xFF9B7BFF, 6, bookId, now))
    add(expense("expense-software", "应用软件", "software", 0xFF4D7CFE, 7, bookId, now))
    add(expense("expense-housing", "住房", "housing", 0xFF57D3A2, 8, bookId, now))
    add(expense("expense-transport", "交通", "transport", 0xFF4D7CFE, 9, bookId, now))
    add(expense("expense-fun", "娱乐", "entertainment", 0xFFFFC247, 10, bookId, now))
    add(expense("expense-health", "医疗", "medical", 0xFFFF7AA2, 11, bookId, now))
    add(expense("expense-communication", "通讯", "communication", 0xFF29B6F6, 12, bookId, now))
    add(expense("expense-car", "汽车", "car", 0xFF4D7CFE, 13, bookId, now))
    add(expense("expense-study", "学习", "study", 0xFF6C8CFF, 14, bookId, now))
    add(expense("expense-office", "办公", "office", 0xFF8992A3, 15, bookId, now))
    add(expense("expense-sport", "运动", "sport", 0xFF57D3A2, 16, bookId, now))
    add(expense("expense-social", "社交", "social", 0xFFFFA94D, 17, bookId, now))
    add(expense("expense-relationship", "人情", "relationship", 0xFFFF7AA2, 18, bookId, now))
    add(expense("expense-child", "育儿", "child", 0xFFFFC247, 19, bookId, now))
    add(expense("expense-pet", "宠物", "pet", 0xFF9B7BFF, 20, bookId, now))
    add(expense("expense-travel", "旅行", "travel", 0xFF29B6F6, 21, bookId, now))
    add(expense("expense-vacation", "度假", "vacation", 0xFF57D3A2, 22, bookId, now))
    add(expense("expense-wine", "烟酒", "wine", 0xFFFFA94D, 23, bookId, now))
    add(expense("expense-lottery", "彩票", "lottery", 0xFFFF7AA2, 24, bookId, now))
    add(expense("expense-other", "其他", "other", 0xFF8992A3, 25, bookId, now))

    add(expenseChild("expense-food-meals", "三餐", "meal-three", "expense-food", 0, 0xFFFF6B5F, bookId, now))
    add(expenseChild("expense-food-snack", "零食", "snack", "expense-food", 1, 0xFFFF6B5F, bookId, now))
    add(expenseChild("expense-food-fruit", "水果", "fruit", "expense-food", 2, 0xFFFF6B5F, bookId, now))
    add(expenseChild("expense-food-vegetable", "蔬菜", "vegetable", "expense-food", 3, 0xFFFF6B5F, bookId, now))

    add(expenseChild("expense-housing-rent", "房租", "rent", "expense-housing", 0, 0xFF57D3A2, bookId, now))
    add(expenseChild("expense-housing-mortgage", "房贷", "mortgage", "expense-housing", 1, 0xFF57D3A2, bookId, now))
    add(expenseChild("expense-housing-property", "物业", "property", "expense-housing", 2, 0xFF57D3A2, bookId, now))
    add(expenseChild("expense-housing-utilities", "水电燃气", "utilities", "expense-housing", 3, 0xFF57D3A2, bookId, now))

    add(expenseChild("expense-transport-public", "公交地铁", "public-transit", "expense-transport", 0, 0xFF4D7CFE, bookId, now))
    add(expenseChild("expense-transport-taxi", "打车", "taxi", "expense-transport", 1, 0xFF4D7CFE, bookId, now))
    add(expenseChild("expense-transport-train", "火车", "train", "expense-transport", 2, 0xFF4D7CFE, bookId, now))
    add(expenseChild("expense-transport-flight", "飞机", "flight", "expense-transport", 3, 0xFF4D7CFE, bookId, now))

    add(expenseChild("expense-health-medicine", "药品", "medicine", "expense-health", 0, 0xFFFF7AA2, bookId, now))
    add(expenseChild("expense-health-clinic", "门诊", "clinic", "expense-health", 1, 0xFFFF7AA2, bookId, now))
    add(expenseChild("expense-health-hospital", "住院", "hospital", "expense-health", 2, 0xFFFF7AA2, bookId, now))
    add(expenseChild("expense-health-checkup", "体检", "checkup", "expense-health", 3, 0xFFFF7AA2, bookId, now))

    add(expenseChild("expense-study-course", "课程", "course", "expense-study", 0, 0xFF6C8CFF, bookId, now))
    add(expenseChild("expense-study-books", "书籍", "books", "expense-study", 1, 0xFF6C8CFF, bookId, now))
    add(expenseChild("expense-study-exam", "考试", "exam", "expense-study", 2, 0xFF6C8CFF, bookId, now))
    add(expenseChild("expense-study-stationery", "文具", "stationery", "expense-study", 3, 0xFF6C8CFF, bookId, now))

    add(expenseChild("expense-relationship-red-packet", "红包", "gift-money", "expense-relationship", 0, 0xFFFF7AA2, bookId, now))
    add(expenseChild("expense-relationship-gift", "礼物", "gift", "expense-relationship", 1, 0xFFFF7AA2, bookId, now))
    add(expenseChild("expense-relationship-treat", "请客", "treat", "expense-relationship", 2, 0xFFFF7AA2, bookId, now))
    add(expenseChild("expense-relationship-family", "孝敬", "family", "expense-relationship", 3, 0xFFFF7AA2, bookId, now))
}

internal fun defaultIncomeCategories(
    bookId: String,
    now: Long,
): List<CategoryEntity> = listOf(
    income("income-salary", "工资", "salary", 0xFF57D3A2, 0, bookId, now),
    income("income-bonus", "奖金", "bonus", 0xFFFFC247, 1, bookId, now),
    income("income-overtime", "加班", "overtime", 0xFF4D7CFE, 2, bookId, now),
    income("income-benefit", "福利", "benefit", 0xFFFF7AA2, 3, bookId, now),
    income("income-fund", "公积金", "provident-fund", 0xFF29B6F6, 4, bookId, now),
    income("income-red-packet", "红包", "red-packet", 0xFFFF6B5F, 5, bookId, now),
    income("income-part-time", "兼职", "part-time", 0xFF9B7BFF, 6, bookId, now),
    income("income-side-business", "副业", "side-business", 0xFF57D3A2, 7, bookId, now),
    income("income-tax-refund", "退税", "tax-refund", 0xFF4D7CFE, 8, bookId, now),
    income("income-invest", "投资", "investment", 0xFF29B6F6, 9, bookId, now),
    income("income-windfall", "意外收入", "windfall", 0xFFFFA94D, 10, bookId, now),
    income("income-other", "其他", "other", 0xFF8992A3, 11, bookId, now),
)

private fun expense(
    id: String,
    name: String,
    icon: String,
    color: Long,
    order: Int,
    bookId: String,
    now: Long,
) = category(id, name, TransactionType.EXPENSE, icon, color, null, order, bookId, now)

private fun expenseChild(
    id: String,
    name: String,
    icon: String,
    parentId: String,
    order: Int,
    color: Long,
    bookId: String,
    now: Long,
) = category(id, name, TransactionType.EXPENSE, icon, color, parentId, order, bookId, now)

private fun income(
    id: String,
    name: String,
    icon: String,
    color: Long,
    order: Int,
    bookId: String,
    now: Long,
) = category(id, name, TransactionType.INCOME, icon, color, null, order, bookId, now)

private fun category(
    id: String,
    name: String,
    type: String,
    icon: String,
    color: Long,
    parentId: String?,
    order: Int,
    bookId: String,
    now: Long,
) = CategoryEntity(
    id = id,
    bookId = bookId,
    name = name,
    transactionType = type,
    iconKey = icon,
    colorHex = color,
    parentId = parentId,
    sortOrder = order,
    isSystem = true,
    createdAt = now,
    updatedAt = now,
)
