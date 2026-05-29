package com.nightlynexus.transparentwidget.controller

import android.os.Parcelable
import android.view.View
import android.view.ViewGroup

interface Controller<S : Parcelable> {
  interface Factory<V : Controller<S>, S : Parcelable> : Parcelable {
    fun create(
      dependencies: Map<Any, Any>,
      parentView: ViewGroup,
      savedState: S?
    ): V
  }

  fun saveState(): S? {
    return null
  }

  fun getView(): View

  fun onDestroy()
}
