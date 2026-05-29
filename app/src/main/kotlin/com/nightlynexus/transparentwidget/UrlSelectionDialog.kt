package com.nightlynexus.transparentwidget

import android.content.Context
import android.os.Parcelable
import android.util.Patterns
import android.util.SparseArray
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

internal class UrlSelectionDialog(
  context: Context,
  private val onUrlSelectedListener: OnUrlSelectedListener,
  savedState: SparseArray<Parcelable>?
) {
  interface OnUrlSelectedListener {
    fun onUrlSelected(url: String)
  }

  private val dialog = AlertDialog.Builder(context)
    .setView(R.layout.url_selection)
    .create()
  private var invalidUrlTextViewAnimator: ViewPropertyAnimator? = null

  init {
    dialog.create()
    val window = dialog.window!!
    window.setDimAmount(0.7f)
    val urlEditText = dialog.findViewById<TextView>(R.id.url)!!
    val invalidUrlTextView = dialog.findViewById<View>(R.id.url_selection_invalid_url)!!
    val doneButton = dialog.findViewById<View>(R.id.done)!!
    urlEditText.filters = arrayOf(PlainTextInputFilter())
    doneButton.setOnClickListener {
      val text = urlEditText.text
      val validUrl = Patterns.WEB_URL.matcher(text).matches()
      if (validUrl) {
        val url = if (!text.startsWith("http")) {
          "https://$text"
        } else {
          text.toString()
        }
        onUrlSelectedListener.onUrlSelected(url)
      } else {
        animateInvalidUrlTextView(invalidUrlTextView)
      }
    }
    if (savedState != null) {
      val decorView = window.decorView
      decorView.restoreHierarchyState(savedState)
    }
  }

  fun isShowing(): Boolean {
    return dialog.isShowing
  }

  fun show() {
    dialog.show()
  }

  fun dismiss() {
    dialog.dismiss()
  }

  fun saveState(): SparseArray<Parcelable> {
    val decorView = dialog.window!!.decorView
    val viewState = SparseArray<Parcelable>()
    decorView.saveHierarchyState(viewState)
    return viewState
  }

  private val invalidUrlTextViewAnimatorFadeInDurationMillis = 400L
  private val invalidUrlTextViewAnimatorFadeOutStartDelayDurationMillis = 600L
  private val invalidUrlTextViewAnimatorFadeOutDurationMillis = 600L

  private fun animateInvalidUrlTextView(invalidUrlTextView: View) {
    invalidUrlTextViewAnimator?.cancel()
    val currentAlpha = invalidUrlTextView.alpha
    val fadeInDuration = ((1 - currentAlpha) * invalidUrlTextViewAnimatorFadeInDurationMillis)
      .toLong()
    invalidUrlTextViewAnimator = invalidUrlTextView.animate()
      .alpha(1f)
      .setDuration(fadeInDuration)
      .setStartDelay(0L)
      .withEndAction {
        invalidUrlTextViewAnimator = invalidUrlTextView.animate()
          .alpha(0f)
          .setDuration(invalidUrlTextViewAnimatorFadeOutDurationMillis)
          .setStartDelay(invalidUrlTextViewAnimatorFadeOutStartDelayDurationMillis)
      }
  }
}
