package com.example.pdfreader

import android.content.Context
import android.content.SharedPreferences

object SharedPreferencesManager {
    private const val PREFS_NAME = "AppPrefs"
    private const val KEY_LEFT_TO_RIGHT_MODE = "left_to_right_mode"
    private const val KEY_ONE_PAGE_MODE = "one_page_mode"
    private const val KEY_COVER_PAGE_SEPARATE = "cover_page_separate"
    private const val KEY_VERTICAL_SCROLL_MODE = "vertical_scroll_mode"
    private const val KEY_URI = "uri"
    private const val KEY_PAGE_NUMBER = "page_number"
    private const val KEY_RESOLUTION = "resolution"
    private const val KEY_LANDSCAPE_ORIENTATION = "landscape_orientation"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isLeftToRightMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LEFT_TO_RIGHT_MODE, true)
    }

    fun setLeftToRightMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LEFT_TO_RIGHT_MODE, enabled).apply()
    }

    fun isOnePageMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONE_PAGE_MODE, true)
    }

    fun setOnePageMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ONE_PAGE_MODE, enabled).apply()
    }

    fun isCoverPageSeparate(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_COVER_PAGE_SEPARATE, true)
    }

    fun setCoverPageSeparate(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_COVER_PAGE_SEPARATE, enabled).apply()
    }

    fun isVerticalScrollMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VERTICAL_SCROLL_MODE, false)
    }

    fun setVerticalScrollMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VERTICAL_SCROLL_MODE, enabled).apply()
    }

    fun loadUri(context: Context): String? {
        return getPrefs(context).getString(KEY_URI, null)
    }

    fun saveUri(context: Context, uri: String) {
        getPrefs(context).edit().putString(KEY_URI, uri).apply()
    }

    fun loadPageNumber(context: Context): Int {
        return getPrefs(context).getInt(KEY_PAGE_NUMBER, 0)
    }

    fun savePageNumber(context: Context, pageNumber: Int) {
        getPrefs(context).edit().putInt(KEY_PAGE_NUMBER, pageNumber).apply()
    }

    fun getResolution(context: Context): String? {
        return getPrefs(context).getString(KEY_RESOLUTION, "LOW")
    }

    fun setResolution(context: Context, resolution: String) {
        getPrefs(context).edit().putString(KEY_RESOLUTION, resolution).apply()
    }

    fun isLandscapeOrientation(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LANDSCAPE_ORIENTATION, false)
    }

    fun setLandscapeOrientation(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LANDSCAPE_ORIENTATION, enabled).apply()
    }


}