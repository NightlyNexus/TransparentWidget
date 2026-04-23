package com.nightlynexus.transparentwidget

import android.content.ComponentName
import android.content.SharedPreferences
import androidx.core.content.edit

internal class AppWidgetIdsToComponentsStorage(private val sharedPreferences: SharedPreferences) {
  fun setComponent(appWidgetId: Int, componentName: ComponentName?) {
    val key = encodeKey(appWidgetId)
    val value = encodeValue(componentName)
    sharedPreferences.edit { putString(key, value) }
  }

  fun remove(appWidgetId: Int) {
    val key = encodeKey(appWidgetId)
    sharedPreferences.edit { remove(key) }
  }

  fun getComponentName(appWidgetId: Int): ComponentName? {
    val key = encodeKey(appWidgetId)
    val value = sharedPreferences.getString(key, null) ?: return null
    return decodeValue(value)
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

  private fun encodeValue(componentName: ComponentName?): String? {
    return if (componentName == null) {
      null
    } else {
      "${componentName.packageName}\n${componentName.className}"
    }
  }

  private fun decodeValue(value: String): ComponentName? {
    val index = value.indexOf('\n')
    if (index == -1) {
      // The user manually edited his SharedPreferences file contents.
      // Make the widget blank as though it has no ComponentName.
      return null
    }
    val packageName = value.substring(0, index)
    val className = value.substring(index + 1)
    return ComponentName(packageName, className)
  }
}
