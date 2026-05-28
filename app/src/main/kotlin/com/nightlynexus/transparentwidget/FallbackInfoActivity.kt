package com.nightlynexus.transparentwidget

import android.app.Activity

class FallbackInfoActivity : Activity() {
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    if (hasFocus) {
      promptAddWidgetAndFinish(useFallback = false)
    }
  }
}
