package com.oneledger.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.ui.graphics.vector.ImageVector
import com.oneledger.app.data.local.AccountEntity
import com.oneledger.app.data.local.CategoryEntity

internal data class QuickAddCategoryTree(
    val roots: List<CategoryEntity>,
    val childrenByParent: Map<String, List<CategoryEntity>>,
) {
    fun childrenOf(categoryId: String): List<CategoryEntity> = childrenByParent[categoryId].orEmpty()
}

/**
 * Builds a presentation-only hierarchy. Orphaned children remain visible as roots so a damaged or
 * partially imported category list never makes an otherwise selectable category disappear.
 */
internal fun quickAddCategoryTree(categories: List<CategoryEntity>): QuickAddCategoryTree {
    val categoryIds = categories.asSequence().map { it.id }.toSet()
    val roots = categories
        .filter { it.parentId == null || it.parentId !in categoryIds }
        .sortedWith(quickAddCategoryComparator)
    val children = categories
        .filter { it.parentId in categoryIds }
        .groupBy { requireNotNull(it.parentId) }
        .mapValues { (_, values) ->
            values.sortedWith(quickAddCategoryComparator)
        }
    return QuickAddCategoryTree(roots = roots, childrenByParent = children)
}

private val quickAddCategoryComparator =
    compareBy<CategoryEntity> { if (it.name.trim() == "其他") 1 else 0 }
        .thenBy(CategoryEntity::sortOrder)
        .thenBy(CategoryEntity::name)

internal fun toggledQuickAddCategory(
    expandedCategoryId: String?,
    tappedCategoryId: String,
    hasChildren: Boolean,
): String? = when {
    !hasChildren -> null
    expandedCategoryId == tappedCategoryId -> null
    else -> tappedCategoryId
}

internal fun filteredQuickAddAccounts(
    accounts: List<AccountEntity>,
    query: String,
): List<AccountEntity> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return accounts
    return accounts.filter { account ->
        account.name.contains(keyword, ignoreCase = true) ||
            account.subtitle.contains(keyword, ignoreCase = true) ||
            account.type.contains(keyword, ignoreCase = true)
    }
}

/**
 * Quick Add uses one outlined icon family. The name fallback keeps imported/user-defined categories
 * visually coherent even when their iconKey came from an older catalog.
 */
internal fun quickAddCategoryIcon(category: CategoryEntity): ImageVector {
    val key = category.iconKey.lowercase()
    val name = category.name
    return when {
        key in setOf("food", "meal", "restaurant") || name == "餐饮" -> Icons.Outlined.Restaurant
        key in setOf("meal-three", "meals") || name == "三餐" -> Icons.Outlined.RamenDining
        key in setOf("snack", "cookie") || name == "零食" -> Icons.Outlined.Cookie
        key in setOf("fruit", "vegetable", "produce") || name in setOf("水果", "蔬菜") -> Icons.Outlined.Yard
        key in setOf("shopping", "cart") || name == "购物" -> Icons.Outlined.ShoppingCart
        key in setOf("clothes", "clothing") || name == "服饰" -> Icons.Outlined.Checkroom
        key in setOf("daily", "household") || name == "日用" -> Icons.Outlined.CleaningServices
        key in setOf("digital", "phone") || name == "数码" -> Icons.Outlined.PhoneAndroid
        key in setOf("beauty", "makeup") || name == "美妆" -> Icons.Outlined.FaceRetouchingNatural
        key in setOf("skin", "skincare") || name == "护肤" -> Icons.Outlined.Spa
        key in setOf("app", "software") || name == "应用软件" -> Icons.Outlined.Apps
        key in setOf("home", "housing") || name in setOf("居家", "住房") -> Icons.Outlined.Home
        key in setOf("transport", "traffic") || name == "交通" -> Icons.Outlined.DirectionsCar
        key in setOf("movie", "fun", "entertainment") || name == "娱乐" -> Icons.Outlined.SportsEsports
        key in setOf("health", "medical") || name == "医疗" -> Icons.Outlined.MedicalServices
        key in setOf("communication", "phone-call") || name == "通讯" -> Icons.Outlined.PhoneInTalk
        key in setOf("car", "vehicle") || name == "汽车" -> Icons.Outlined.DirectionsCar
        key in setOf("study", "education") || name == "学习" -> Icons.Outlined.School
        key in setOf("office", "work") || name == "办公" -> Icons.Outlined.WorkOutline
        key in setOf("sport", "fitness") || name == "运动" -> Icons.Outlined.FitnessCenter
        key in setOf("social", "friends") || name == "社交" -> Icons.Outlined.Groups
        key in setOf("relationship", "favor") || name == "人情" -> Icons.Outlined.VolunteerActivism
        key in setOf("child", "parenting") || name == "育儿" -> Icons.Outlined.ChildCare
        key in setOf("pet", "pets") || name == "宠物" -> Icons.Outlined.Pets
        key in setOf("travel", "trip") || name == "旅行" -> Icons.Outlined.Luggage
        key in setOf("vacation", "holiday") || name == "度假" -> Icons.Outlined.BeachAccess
        key in setOf("wine", "tobacco") || name == "烟酒" -> Icons.Outlined.WineBar
        key in setOf("lottery", "ticket") || name == "彩票" -> Icons.Outlined.ConfirmationNumber
        key == "rent" || name == "房租" -> Icons.Outlined.Home
        key == "mortgage" || name == "房贷" -> Icons.Outlined.AccountBalance
        key == "property" || name == "物业" -> Icons.Outlined.Apartment
        key == "utilities" || name == "水电燃气" -> Icons.Outlined.Bolt
        key == "public-transit" || name == "公交地铁" -> Icons.Outlined.DirectionsBus
        key == "taxi" || name == "打车" -> Icons.Outlined.LocalTaxi
        key == "train" || name == "火车" -> Icons.Outlined.Train
        key == "flight" || name == "飞机" -> Icons.Outlined.Flight
        key == "medicine" || name == "药品" -> Icons.Outlined.Medication
        key == "clinic" || name == "门诊" -> Icons.Outlined.MedicalServices
        key == "hospital" || name == "住院" -> Icons.Outlined.LocalHospital
        key == "checkup" || name == "体检" -> Icons.Outlined.MonitorHeart
        key == "course" || name == "课程" -> Icons.Outlined.School
        key == "books" || name == "书籍" -> Icons.AutoMirrored.Outlined.MenuBook
        key == "exam" || name == "考试" -> Icons.AutoMirrored.Outlined.Assignment
        key == "stationery" || name == "文具" -> Icons.Outlined.Edit
        key == "gift-money" -> Icons.Outlined.Payments
        key == "gift" || name == "礼物" -> Icons.Outlined.CardGiftcard
        key == "treat" || name == "请客" -> Icons.Outlined.Restaurant
        key == "family" || name == "孝敬" -> Icons.Outlined.FamilyRestroom

        key in setOf("salary", "payroll") || name == "工资" -> Icons.Outlined.Payments
        key == "bonus" || name == "奖金" -> Icons.Outlined.Redeem
        key in setOf("overtime", "extra-time") || name == "加班" -> Icons.Outlined.MoreTime
        key in setOf("benefit", "welfare") || name == "福利" -> Icons.Outlined.CardGiftcard
        key in setOf("fund", "provident-fund") || name == "公积金" -> Icons.Outlined.AccountBalance
        key in setOf("red-packet", "hongbao") || name == "红包" -> Icons.Outlined.CardGiftcard
        key in setOf("part-time", "side-job") || name == "兼职" -> Icons.Outlined.BusinessCenter
        key in setOf("side-business", "business") || name == "副业" -> Icons.Outlined.Storefront
        key in setOf("tax-refund", "refund") || name == "退税" -> Icons.Outlined.CurrencyExchange
        key in setOf("invest", "investment") || name in setOf("投资", "理财") -> Icons.AutoMirrored.Outlined.ShowChart
        key in setOf("windfall", "unexpected") || name == "意外收入" -> Icons.Outlined.AutoAwesome
        key == "other" || name == "其他" -> Icons.Outlined.MoreHoriz
        else -> Icons.Outlined.MoreHoriz
    }
}
