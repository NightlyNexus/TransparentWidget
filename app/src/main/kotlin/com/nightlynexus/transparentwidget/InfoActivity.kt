package com.nightlynexus.transparentwidget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

class InfoActivity : Activity() {
  override fun onResume() {
    super.onResume()
    promptAddWidgetAndFinish(useFallback = true)
  }
}

internal fun Activity.promptAddWidgetAndFinish(useFallback: Boolean) {
  try {
    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
  } catch (_: ActivityNotFoundException) {
    // Nothing to do.
  }
  val appWidgetManager = AppWidgetManager.getInstance(this)
  val callbackIntent = Intent(this, WidgetPinnedReceiver::class.java)
  val successCallback = PendingIntent.getBroadcast(
    this,
    0,
    callbackIntent,
    // Mutable so we can get EXTRA_APPWIDGET_ID in our Intent.
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
  )
  val supported = try {
    appWidgetManager.requestPinAppWidget(
      ComponentName(
        this,
        TransparentAppWidgetProvider::class.java
      ),
      null,
      successCallback
    )
  } catch (e: IllegalStateException) {
    // Ideally, I want to use windowNoDisplay=true in the theme which requires the Activity to
    // finish before completing onResume, but some devices require me to wait until the window is in
    // focus to call requestPinAppWidget.
    if (useFallback && e.message == "Calling application must have a foreground activity or a " +
      "foreground service") {
      startActivity(Intent(this, FallbackInfoActivity::class.java))
      finish()
      return
    } else {
      throw e
    }
  }

  if (!supported) {
    Toast.makeText(
      this,
      R.string.pin_widget_not_supported,
      Toast.LENGTH_SHORT
    ).show()
  }

  finish()
}

// requestPinAppWidget does not seem to work with PendingIntent.getActivity,
// so we have to do this springboard.
class WidgetPinnedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    context.startActivity(
      Intent(context, ConfigurationActivity::class.java).apply {
        putExtras(intent)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
      }
    )
  }
}

class FallbackInfoActivity : Activity() {
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    if (hasFocus) {
      promptAddWidgetAndFinish(useFallback = false)
    }
  }
}
