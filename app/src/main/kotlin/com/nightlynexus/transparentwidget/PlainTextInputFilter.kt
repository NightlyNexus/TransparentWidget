package com.nightlynexus.transparentwidget

import android.text.InputFilter
import android.text.Spanned

internal class PlainTextInputFilter : InputFilter {
  override fun filter(
    source: CharSequence,
    start: Int,
    end: Int,
    dest: Spanned,
    dstart: Int,
    dend: Int
  ): CharSequence {
    return source.toString()
  }
}
