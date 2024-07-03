package com.nightlynexus.transparentwidget

import android.content.ComponentName
import android.content.SharedPreferences

internal class AppWidgetIdsToComponentsStorage(private val sharedPreferences: SharedPreferences) {
  fun setComponent(appWidgetId: Int, componentName: ComponentName?) {
    val value = if (componentName == null) {
      null
    } else {
      "${componentName.packageName}\n${componentName.className}"
    }
    sharedPreferences.edit().putString(appWidgetId.toString(), value).apply()
  }

  fun remove(appWidgetId: Int) {
    sharedPreferences.edit().remove(appWidgetId.toString()).apply()
  }

  fun getComponentName(appWidgetId: Int): ComponentName? {
    val value = sharedPreferences.getString(appWidgetId.toString(), null) ?: return null
    val index = value.indexOf('\n')
    if (index == -1) {
      throw IllegalStateException(value)
    }
    val packageName = value.substring(0, index)
    val className = value.substring(index + 1)
    return ComponentName(packageName, className)
  }
}
