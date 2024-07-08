package com.nightlynexus.transparentwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class InfoActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    val appWidgetManager = AppWidgetManager.getInstance(this)
    val callbackIntent = Intent(this, WidgetPinnedReceiver::class.java)
    val successCallback = PendingIntent.getBroadcast(
      this,
      0,
      callbackIntent,
      // Mutable so we can get EXTRA_APPWIDGET_ID in our Intent.
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    val supported = appWidgetManager.requestPinAppWidget(
      ComponentName(
        this,
        TransparentAppWidgetProvider::class.java
      ),
      null,
      successCallback
    )
    if (!supported) {
      Toast.makeText(this, R.string.pin_widget_not_supported, Toast.LENGTH_SHORT).show()
    }

    finish()
  }
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
