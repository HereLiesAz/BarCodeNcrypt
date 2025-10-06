package com.hereliesaz.barcodencrypt.util

import android.content.Context

object SettingsManager {
    private const val ASSOCIATIONS_PREFS_NAME = "app_settings_associations"
    private const val ASSOCIATED_PACKAGE_NAMES_KEY = "associated_package_names"

    fun loadAssociatedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(ASSOCIATIONS_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(ASSOCIATED_PACKAGE_NAMES_KEY, emptySet()) ?: emptySet()
    }

    fun addAssociatedApp(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(ASSOCIATIONS_PREFS_NAME, Context.MODE_PRIVATE)
        val currentApps = loadAssociatedApps(context).toMutableSet()
        if (currentApps.add(packageName)) {
            prefs.edit().putStringSet(ASSOCIATED_PACKAGE_NAMES_KEY, currentApps).apply()
        }
    }

    fun removeAssociatedApp(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(ASSOCIATIONS_PREFS_NAME, Context.MODE_PRIVATE)
        val currentApps = loadAssociatedApps(context).toMutableSet()
        if (currentApps.remove(packageName)) {
            prefs.edit().putStringSet(ASSOCIATED_PACKAGE_NAMES_KEY, currentApps).apply()
        }
    }
}