package com.nightlynexus.transparentwidget

import android.app.Application

internal class TransparentWidgetApp : Application() {
  lateinit var appWidgetIdsToComponentsStorage: AppWidgetIdsToComponentsStorage

  override fun onCreate() {
    super.onCreate()
    val sharedPreferences = getSharedPreferences(
      "app_widget_ids_to_components",
      MODE_PRIVATE
    )
    appWidgetIdsToComponentsStorage = AppWidgetIdsToComponentsStorage(sharedPreferences)

    val migrator = AppWidgetIdsToComponentsStorageMigrator(sharedPreferences)
    migrator.migrateIfNeeded()
  }
}
