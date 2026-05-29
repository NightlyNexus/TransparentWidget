package com.nightlynexus.transparentwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.nightlynexus.transparentwidget.ClickAction.Companion.toClickAction

class ConfigurationActivity : AppCompatActivity() {
  private val urlSelectionDialogSavedStateKey = "${ConfigurationActivity::class.java}" +
    ".urlSelectionDialogSavedStateKey"
  private lateinit var appWidgetIdsToComponentsStorage: AppWidgetIdsToComponentsStorage
  private lateinit var urlSelectionDialog: UrlSelectionDialog

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
    val resultValue = Intent().putExtra(
      AppWidgetManager.EXTRA_APPWIDGET_ID,
      appWidgetId
    )
    setResult(RESULT_CANCELED, resultValue)

    setContentView(R.layout.configuration_activity)
    val appsList = findViewById<AllAppsListView>(R.id.apps_list)
    appsList.setOnComponentNameClickedListener(
      object : AllAppsListView.OnClickedActionSelectedListener {
        override fun onUrlSelectionSelected() {
          urlSelectionDialog.show()
        }

        override fun onDoNothingSelected() {
          saveClickActionAndFinish(
            appWidgetId,
            ClickAction.DoNothing,
            resultValue
          )
        }

        override fun onComponentNameSelected(componentName: ComponentName) {
          saveClickActionAndFinish(
            appWidgetId,
            componentName.toClickAction(),
            resultValue
          )
        }
      }
    )

    val urlSelectionDialogSavedState: SparseArray<Parcelable>?
    if (savedInstanceState == null) {
      urlSelectionDialogSavedState = null
    } else {
      urlSelectionDialogSavedState = savedInstanceState.getSparseParcelableArray(
        urlSelectionDialogSavedStateKey
      )
    }

    urlSelectionDialog = UrlSelectionDialog(
      this,
      object : UrlSelectionDialog.OnUrlSelectedListener {
        override fun onUrlSelected(url: String) {
          val intent = Intent(Intent.ACTION_VIEW)
          intent.data = url.toUri()
          val clickAction = ClickAction.Activity(intent)
          saveClickActionAndFinish(
            appWidgetId,
            clickAction,
            resultValue
          )
        }
      },
      urlSelectionDialogSavedState
    )

    if (urlSelectionDialogSavedState != null) {
      urlSelectionDialog.show()
    }
  }

  private fun saveClickActionAndFinish(
    appWidgetId: Int,
    clickAction: ClickAction,
    resultValue: Intent
  ) {
    appWidgetIdsToComponentsStorage.setClickAction(
      appWidgetId,
      clickAction
    )
    TransparentAppWidgetProvider.updateAndFade(
      this,
      appWidgetIdsToComponentsStorage,
      AppWidgetManager.getInstance(this),
      appWidgetId,
      clickAction
    )
    setResult(RESULT_OK, resultValue)
    finish()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    if (urlSelectionDialog.isShowing()) {
      outState.putSparseParcelableArray(
        urlSelectionDialogSavedStateKey,
        urlSelectionDialog.saveState()
      )
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    urlSelectionDialog.dismiss()
  }
}
