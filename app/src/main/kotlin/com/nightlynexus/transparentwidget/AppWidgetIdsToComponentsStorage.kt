package com.nightlynexus.transparentwidget

import android.content.ComponentName
import android.content.SharedPreferences
import androidx.core.content.edit

internal class AppWidgetIdsToComponentsStorage(private val sharedPreferences: SharedPreferences) {
  fun setComponent(appWidgetId: Int, componentName: ComponentName?) {
    val value = if (componentName == null) {
      null
    } else {
      "${componentName.packageName}\n${componentName.className}"
    }
    sharedPreferences.edit { putString(appWidgetId.toString(), value) }
  }

  fun remove(appWidgetId: Int) {
    sharedPreferences.edit { remove(appWidgetId.toString()) }
  }

  fun getComponentName(appWidgetId: Int): ComponentName? {
    val key = appWidgetId.toString()
    val value = sharedPreferences.getString(key, null) ?: return null
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
