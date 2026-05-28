package com.nightlynexus.transparentwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ConfigurationActivity : AppCompatActivity() {
  private lateinit var appWidgetIdsToComponentsStorage: AppWidgetIdsToComponentsStorage

  override fun onCreate(savedInstanceState: Bundle?) {
    appWidgetIdsToComponentsStorage =
      (application as TransparentWidgetApp).appWidgetIdsToComponentsStorage
    super.onCreate(savedInstanceState)

    val extras = intent.extras
    if (extras == null) {
      // This happened on a 2025 Moto G running SDK 36.
      // There is nothing to do without the appwidget id from the intent extras, though.
      return
    }
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
      object : AllAppsListView.OnClickedActionSelectedListener {
        override fun onClickActionSelected(clickAction: ClickAction) {
          appWidgetIdsToComponentsStorage.setClickAction(
            appWidgetId,
            clickAction
          )
          TransparentAppWidgetProvider.updateAndFade(
            this@ConfigurationActivity,
            appWidgetIdsToComponentsStorage,
            AppWidgetManager.getInstance(this@ConfigurationActivity),
            appWidgetId,
            clickAction
          )
          setResult(RESULT_OK, resultValue)
          finish()
        }
      })
  }
}
