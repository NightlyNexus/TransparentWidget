package com.nightlynexus.transparentwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.nightlynexus.transparentwidget.ClickAction.Companion.toClickAction
import com.nightlynexus.transparentwidget.controller.Controller
import com.nightlynexus.transparentwidget.controller.ControllerState
import com.nightlynexus.transparentwidget.controller.StateStack

class ConfigurationActivity : AppCompatActivity() {
  private val keyStack = "${ConfigurationActivity::class.java}" +
    ".stack"
  private val keyCurrentControllerState = "${ConfigurationActivity::class.java}" +
    ".currentControllerState"
  private val keyUrlSelectionDialogSavedState = "${ConfigurationActivity::class.java}" +
    ".urlSelectionDialogSavedState"
  private lateinit var appWidgetIdsToComponentsStorage: AppWidgetIdsToComponentsStorage
  private lateinit var dependencies: Map<Any, Any>
  private lateinit var onBackPressedCallback: OnBackPressedCallback
  private lateinit var stateStack: StateStack
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

    val contentView = findViewById<ViewGroup>(android.R.id.content)

    val onClickedActionSelectedListener =
      object : AllAppsController.OnClickedActionSelectedListener {
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

    dependencies = mutableMapOf<Any, Any>().apply {
      put(
        AllAppsController.OnClickedActionSelectedListener::class.java,
        onClickedActionSelectedListener
      )
    }

    val urlSelectionDialogSavedState: SparseArray<Parcelable>?
    val onBackPressedCallbackEnabled: Boolean
    if (savedInstanceState == null) {
      urlSelectionDialogSavedState = null

      stateStack = StateStack(
        dependencies,
        contentView,
        index = 0,
        stack = emptyList(),
        controllerState = null
      )
      stateStack.push(AllAppsController.Factory())
      onBackPressedCallbackEnabled = false
    } else {
      urlSelectionDialogSavedState = savedInstanceState.getSparseParcelableArray(
        keyUrlSelectionDialogSavedState
      )

      val stack: List<ControllerState<Controller<Parcelable>, Parcelable>>
      val controllerState: ControllerState<Controller<Parcelable>, Parcelable>
      if (SDK_INT >= 33) {
        @Suppress("UNCHECKED_CAST")
        stack = savedInstanceState.getParcelableArrayList(
          keyStack,
          ControllerState::class.java
        ) as List<ControllerState<Controller<Parcelable>, Parcelable>>
        @Suppress("UNCHECKED_CAST")
        controllerState = savedInstanceState.getParcelable(
          keyCurrentControllerState,
          ControllerState::class.java
        ) as ControllerState<Controller<Parcelable>, Parcelable>
      } else {
        @Suppress("DEPRECATION")
        stack = savedInstanceState.getParcelableArrayList(
          keyStack
        )!!
        @Suppress("DEPRECATION")
        controllerState = savedInstanceState.getParcelable(
          keyCurrentControllerState
        )!!
      }
      stateStack = StateStack(
        dependencies,
        contentView,
        index = 0,
        stack,
        controllerState
      )
      onBackPressedCallbackEnabled = stack.isNotEmpty()
    }

    onBackPressedCallback = object : OnBackPressedCallback(onBackPressedCallbackEnabled) {
      override fun handleOnBackPressed() {
        stateStack.pop()
        onBackPressedCallback.isEnabled = !stateStack.isEmpty()
      }
    }
    onBackPressedDispatcher.addCallback(onBackPressedCallback)

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
    outState.putParcelableArrayList(keyStack, stateStack.getStack())
    outState.putParcelable(keyCurrentControllerState, stateStack.saveCurrent())
    if (urlSelectionDialog.isShowing()) {
      outState.putSparseParcelableArray(
        keyUrlSelectionDialogSavedState,
        urlSelectionDialog.saveState()
      )
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    stateStack.getCurrentController()!!.onDestroy()
    urlSelectionDialog.dismiss()
  }
}
