package com.nightlynexus.transparentwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ConfigurationActivity : AppCompatActivity() {
  private lateinit var appWidgetIdsToComponentsStorage: AppWidgetIdsToComponentsStorage

  override fun onCreate(savedInstanceState: Bundle?) {
    appWidgetIdsToComponentsStorage =
      (application as TransparentWidgetApp).appWidgetIdsToComponentsStorage
    super.onCreate(savedInstanceState)

    val extras = intent.extras!!
    val appWidgetId = extras.getInt(
      AppWidgetManager.EXTRA_APPWIDGET_ID,
      AppWidgetManager.INVALID_APPWIDGET_ID
    )
    check(appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID)
    val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    setResult(RESULT_CANCELED, resultValue)

    setContentView(R.layout.configuration_activity)
    val appsList = findViewById<AllAppsListView>(R.id.apps_list)
    appsList.setOnComponentNameClickedListener(
      object : AllAppsListView.OnComponentNameClickedListener {
        override fun onComponentNameClicked(componentName: ComponentName?) {
          appWidgetIdsToComponentsStorage.setComponent(appWidgetId, componentName)
          TransparentAppWidgetProvider.updateAndFade(
            this@ConfigurationActivity,
            appWidgetIdsToComponentsStorage,
            AppWidgetManager.getInstance(this@ConfigurationActivity),
            appWidgetId,
            componentName
          )
          setResult(RESULT_OK, resultValue)
          finish()
        }
      })
  }
}
