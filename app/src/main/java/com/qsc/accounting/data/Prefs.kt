package com.qsc.accounting.data

import android.content.Context
import com.qsc.accounting.data.model.StatisticsMode

/** 本地偏好设置；MVP 只有统计方式与「是否含未到账计划」两项，其余沿用插件默认。 */
class Prefs(context: Context) {

    private val store = context.applicationContext.getSharedPreferences("accounting.settings", Context.MODE_PRIVATE)

    var statisticsMode: StatisticsMode
        get() = StatisticsMode.fromWire(store.getString(KEY_MODE, null))
        set(value) = store.edit().putString(KEY_MODE, value.wire).apply()

    var includePending: Boolean
        get() = store.getBoolean(KEY_INCLUDE_PENDING, true)
        set(value) = store.edit().putBoolean(KEY_INCLUDE_PENDING, value).apply()

    var seeded: Boolean
        get() = store.getBoolean(KEY_SEEDED, false)
        set(value) = store.edit().putBoolean(KEY_SEEDED, value).apply()

    /** 收藏的分类 ID。只是本地偏好，不写进账本，也不进备份。 */
    var favoriteCategories: Set<Long>
        get() = store.getStringSet(KEY_FAVORITES, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            .orEmpty()
        set(value) = store.edit()
            .putStringSet(KEY_FAVORITES, value.map { it.toString() }.toSet())
            .apply()

    // ---------------------------------------------------------------- 通知

    var notificationsEnabled: Boolean
        get() = store.getBoolean(KEY_NOTIFY_ENABLED, false)
        set(value) = store.edit().putBoolean(KEY_NOTIFY_ENABLED, value).apply()

    /** 首次启动是否已经问过通知权限，避免每次开屏都弹。 */
    var permissionPromptShown: Boolean
        get() = store.getBoolean(KEY_PERMISSION_PROMPT, false)
        set(value) = store.edit().putBoolean(KEY_PERMISSION_PROMPT, value).apply()

    var dailyEnabled: Boolean
        get() = store.getBoolean(KEY_DAILY_ENABLED, false)
        set(value) = store.edit().putBoolean(KEY_DAILY_ENABLED, value).apply()

    var dailyHour: Int
        get() = store.getInt(KEY_DAILY_HOUR, 21)
        set(value) = store.edit().putInt(KEY_DAILY_HOUR, value).apply()

    var dailyMinute: Int
        get() = store.getInt(KEY_DAILY_MINUTE, 0)
        set(value) = store.edit().putInt(KEY_DAILY_MINUTE, value).apply()

    var monthlyEnabled: Boolean
        get() = store.getBoolean(KEY_MONTHLY_ENABLED, false)
        set(value) = store.edit().putBoolean(KEY_MONTHLY_ENABLED, value).apply()

    var monthlyDay: Int
        get() = store.getInt(KEY_MONTHLY_DAY, 1)
        set(value) = store.edit().putInt(KEY_MONTHLY_DAY, value).apply()

    var monthlyHour: Int
        get() = store.getInt(KEY_MONTHLY_HOUR, 9)
        set(value) = store.edit().putInt(KEY_MONTHLY_HOUR, value).apply()

    var monthlyMinute: Int
        get() = store.getInt(KEY_MONTHLY_MINUTE, 0)
        set(value) = store.edit().putInt(KEY_MONTHLY_MINUTE, value).apply()

    private companion object {
        const val KEY_MODE = "statistics_mode"
        const val KEY_INCLUDE_PENDING = "amortization_include_pending"
        const val KEY_SEEDED = "defaults_seeded"
        const val KEY_FAVORITES = "favorite_categories"
        const val KEY_NOTIFY_ENABLED = "notifications_enabled"
        const val KEY_PERMISSION_PROMPT = "permission_prompt_shown"
        const val KEY_DAILY_ENABLED = "notify_daily_enabled"
        const val KEY_DAILY_HOUR = "notify_daily_hour"
        const val KEY_DAILY_MINUTE = "notify_daily_minute"
        const val KEY_MONTHLY_ENABLED = "notify_monthly_enabled"
        const val KEY_MONTHLY_DAY = "notify_monthly_day"
        const val KEY_MONTHLY_HOUR = "notify_monthly_hour"
        const val KEY_MONTHLY_MINUTE = "notify_monthly_minute"
    }
}
