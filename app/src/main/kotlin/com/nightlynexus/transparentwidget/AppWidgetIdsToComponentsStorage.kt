package com.nightlynexus.transparentwidget

import android.content.SharedPreferences
import androidx.core.content.edit
import com.nightlynexus.transparentwidget.ClickAction.Companion.decodeClickAction

internal class AppWidgetIdsToComponentsStorage(
  private val sharedPreferences: SharedPreferences
) {
  fun setClickAction(appWidgetId: Int, clickAction: ClickAction) {
    val key = encodeKey(appWidgetId)
    val value = clickAction.encode()
    sharedPreferences.edit { putString(key, value) }
  }

  fun remove(appWidgetId: Int) {
    val key = encodeKey(appWidgetId)
    sharedPreferences.edit { remove(key) }
  }

  fun getClickAction(appWidgetId: Int): ClickAction {
    val key = encodeKey(appWidgetId)
    val value = sharedPreferences.getString(key, null)
    val clickAction = decodeClickAction(value)
    return clickAction
  }

  fun restore(oldAppWidgetIds: IntArray, newAppWidgetIds: IntArray) {
    require(oldAppWidgetIds.size == newAppWidgetIds.size) {
      "${oldAppWidgetIds.size} != ${newAppWidgetIds.size}"
    }
    val values = ArrayList<String?>(oldAppWidgetIds.size)
    sharedPreferences.edit {
      for (oldAppWidgetId in oldAppWidgetIds) {
        val key = encodeKey(oldAppWidgetId)
        val value = sharedPreferences.getString(key, null)
        values += value
        remove(key)
      }
      for (i in newAppWidgetIds.indices) {
        val newAppWidgetId = newAppWidgetIds[i]
        val key = encodeKey(newAppWidgetId)
        val value = values[i]
        putString(key, value)
      }
    }
  }

  private fun encodeKey(appWidgetId: Int): String {
    return appWidgetId.toString()
  }
}
