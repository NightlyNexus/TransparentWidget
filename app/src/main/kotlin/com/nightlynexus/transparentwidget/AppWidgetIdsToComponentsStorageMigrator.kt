package com.nightlynexus.transparentwidget

import android.content.ComponentName
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nightlynexus.transparentwidget.ClickAction.Companion.toClickAction

internal class AppWidgetIdsToComponentsStorageMigrator(
  private val sharedPreferences: SharedPreferences
) {
  fun migrateIfNeeded() {
    val versionKey = "version" // All other keys are appWidgetId integers.
    val version = sharedPreferences.getInt(versionKey, 1)
    if (version == 2) {
      return
    }

    val allKeys = sharedPreferences.all.keys
    sharedPreferences.edit {
      for (key in allKeys) {
        if (key == versionKey) {
          continue
        }
        val oldValue = sharedPreferences.getString(key, null)
        val newValue = if (oldValue == null) {
          null
        } else {
          val componentName = decodeOldValue(oldValue)
          if (componentName == null) {
            ClickAction.Malformed
          } else {
            componentName.toClickAction()
          }.encode()
        }
        putString(key, newValue)
      }

      putInt(versionKey, 2)
    }
  }

  private fun decodeOldValue(value: String): ComponentName? {
    val index = value.indexOf('\n')
    if (index == -1) {
      // The user manually edited his SharedPreferences file contents.
      return null
    }
    val packageName = value.substring(0, index)
    val className = value.substring(index + 1)
    return ComponentName(packageName, className)
  }
}
