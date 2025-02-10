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
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      // This happens in production on some Chinese devices.
      // When does this happen? How do we get the appWidgetId? What behavior does this now have?
      // Hopefully, these devices try launching the configuration activity again.
      finish()
      return
    }
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
