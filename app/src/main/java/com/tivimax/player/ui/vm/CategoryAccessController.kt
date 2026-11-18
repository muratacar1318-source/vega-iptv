package com.tivimax.player.ui.vm

import com.tivimax.player.data.model.LiveCategory
import com.tivimax.player.data.model.SeriesCategory
import com.tivimax.player.data.model.VodCategory
import com.tivimax.player.data.preferences.UserPreferences
import com.tivimax.player.ui.AdultFilter
import com.tivimax.player.ui.ParentalPin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * G-054: Ebeveyn kilidi ve kategori yönetimi (MainViewModel'den taşındı; mantık değişmedi).
 * Kategori listeleri MainViewModel'de yaşar ve kurucudan verilir (başlatma sırası için).
 */
class CategoryAccessController(
    private val scope: CoroutineScope,
    private val preferences: UserPreferences,
    private val liveCategories: StateFlow<List<LiveCategory>>,
    private val vodCategories: StateFlow<List<VodCategory>>,
    private val seriesCategories: StateFlow<List<SeriesCategory>>
) {
    // Kategori Yönetimi & Ebeveyn Kilidi (MainViewModel'den taşındı)
    val hiddenCategories = preferences.lockedCategoriesFlow.stateIn(scope, SharingStarted.Eagerly, emptySet())
    val pinnedCategories = preferences.pinnedCategoriesFlow.stateIn(scope, SharingStarted.Eagerly, emptySet())
    val isParentalUnlocked = MutableStateFlow(false)
    val parentalPin = preferences.parentalPinFlow.stateIn(scope, SharingStarted.Eagerly, "")

    // G-052: Engellenen kategori kimlikleri = kullanıcının gizledikleri ∪ (kilit kapalıyken) +18 olanlar.
    // İçerik akışları bu kümeyle süzülür; böylece "Tümü" listesi, ekran içi arama ve ana sayfa da korunur.
    val blockedLiveCategoryIds: StateFlow<Set<String>> =
        combine(liveCategories, hiddenCategories, isParentalUnlocked) { cats, hidden, unlocked ->
            AdultFilter.blockedIds(cats.map { it.categoryId to it.categoryName }, hidden, unlocked)
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())
    val blockedVodCategoryIds: StateFlow<Set<String>> =
        combine(vodCategories, hiddenCategories, isParentalUnlocked) { cats, hidden, unlocked ->
            AdultFilter.blockedIds(cats.map { it.categoryId to it.categoryName }, hidden, unlocked)
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())
    val blockedSeriesCategoryIds: StateFlow<Set<String>> =
        combine(seriesCategories, hiddenCategories, isParentalUnlocked) { cats, hidden, unlocked ->
            AdultFilter.blockedIds(cats.map { it.categoryId to it.categoryName }, hidden, unlocked)
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Listede +18 kategori var mı? (Kilit düğmesi yalnız o zaman gösterilir; kilit açıkken de var sayılır.) */
    val hasAdultLiveCategory: StateFlow<Boolean> = liveCategories
        .map { cats -> cats.any { AdultFilter.isAdult(it.categoryName) } }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val hasAdultVodCategory: StateFlow<Boolean> = vodCategories
        .map { cats -> cats.any { AdultFilter.isAdult(it.categoryName) } }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val hasAdultSeriesCategory: StateFlow<Boolean> = seriesCategories
        .map { cats -> cats.any { AdultFilter.isAdult(it.categoryName) } }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun isAdultCategory(name: String): Boolean = AdultFilter.isAdult(name)

    val visibleLiveCategories: StateFlow<List<LiveCategory>> = combine(liveCategories, hiddenCategories, pinnedCategories, isParentalUnlocked) { cats, hidden, pinned, unlocked ->
        cats.filter { cat ->
            if (cat.categoryId == "all") return@filter true
            if (hidden.contains(cat.categoryId)) return@filter false
            if (!unlocked && isAdultCategory(cat.categoryName)) return@filter false
            true
        }.sortedWith(
            compareByDescending<LiveCategory> { it.categoryId == "all" }
                .thenByDescending { pinned.contains(it.categoryId) }
        )
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val visibleVodCategories: StateFlow<List<VodCategory>> = combine(vodCategories, hiddenCategories, pinnedCategories, isParentalUnlocked) { cats, hidden, pinned, unlocked ->
        cats.filter { cat ->
            if (cat.categoryId == "all") return@filter true
            if (hidden.contains(cat.categoryId)) return@filter false
            if (!unlocked && isAdultCategory(cat.categoryName)) return@filter false
            true
        }.sortedWith(
            compareByDescending<VodCategory> { it.categoryId == "all" }
                .thenByDescending { pinned.contains(it.categoryId) }
        )
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val visibleSeriesCategories: StateFlow<List<SeriesCategory>> = combine(seriesCategories, hiddenCategories, pinnedCategories, isParentalUnlocked) { cats, hidden, pinned, unlocked ->
        cats.filter { cat ->
            if (cat.categoryId == "all") return@filter true
            if (hidden.contains(cat.categoryId)) return@filter false
            if (!unlocked && isAdultCategory(cat.categoryName)) return@filter false
            true
        }.sortedWith(
            compareByDescending<SeriesCategory> { it.categoryId == "all" }
                .thenByDescending { pinned.contains(it.categoryId) }
        )
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun toggleCategoryHidden(categoryId: String, isHidden: Boolean) {
        scope.launch {
            preferences.setCategoryHidden(categoryId, isHidden)
        }
    }

    fun setAllCategoriesHidden(categoryIds: Collection<String>, isHidden: Boolean) {
        scope.launch {
            preferences.setAllCategoriesHidden(categoryIds, isHidden)
        }
    }

    fun toggleCategoryPinned(categoryId: String) {
        scope.launch {
            preferences.toggleCategoryPinned(categoryId)
        }
    }

    fun unlockParental(pin: String): Boolean {
        if (ParentalPin.canUnlock(parentalPin.value, pin)) {
            isParentalUnlocked.value = true
            return true
        }
        return false
    }

    /** PIN henüz belirlenmemişken ilk PIN'i kaydeder ve kilidi açar. Geçersizse false döner. */
    fun createParentalPin(pin: String, repeat: String): Boolean {
        if (parentalPin.value.isNotEmpty() || ParentalPin.validateNewPin(pin, repeat) != null) return false
        setParentalPin(pin)
        isParentalUnlocked.value = true
        return true
    }

    fun lockParental() {
        isParentalUnlocked.value = false
    }

    fun setParentalPin(newPin: String) {
        scope.launch {
            preferences.setParentalPin(newPin)
        }
    }

    fun toggleCategoryHidden(categoryId: String) {
        scope.launch {
            preferences.toggleCategoryHidden(categoryId)
        }
    }
}
