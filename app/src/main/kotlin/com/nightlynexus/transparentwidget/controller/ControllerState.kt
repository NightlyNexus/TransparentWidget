package com.nightlynexus.transparentwidget.controller

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray
import androidx.core.util.size

internal class ControllerState<C : Controller<S>, S : Parcelable> internal constructor(
  val factory: Controller.Factory<C, S>,
  val viewState: SparseArray<Parcelable>,
  val savedState: S?
) : Parcelable {
  override fun writeToParcel(dest: Parcel, flags: Int) {
    dest.writeParcelable(factory, flags)
    dest.writeParcelableSparseArray(viewState, flags)
    dest.writeParcelable(savedState, flags)
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Creator<ControllerState<*, *>> {
    override fun createFromParcel(`in`: Parcel): ControllerState<*, *> {
      val classLoader = ControllerState::class.java.classLoader!!
      val factory = `in`.readParcelable<Controller.Factory<Controller<Parcelable>, Parcelable>>(
        classLoader
      )!!
      val viewState = `in`.readParcelableSparseArray(
        classLoader
      )
      val savedState = `in`.readParcelable<Parcelable>(
        classLoader
      )
      return ControllerState(factory, viewState, savedState)
    }

    override fun newArray(size: Int): Array<ControllerState<*, *>?> {
      return arrayOfNulls(size)
    }

    private fun Parcel.readParcelableSparseArray(loader: ClassLoader): SparseArray<Parcelable> {
      var size = readInt()
      val map = SparseArray<Parcelable>(size)
      while (size != 0) {
        val key = readInt()
        val value = readParcelable<Parcelable>(loader)
        map.append(key, value)
        size--
      }
      return map
    }
  }

  private fun Parcel.writeParcelableSparseArray(map: SparseArray<Parcelable>, flags: Int) {
    val size = map.size
    writeInt(size)
    var i = 0
    while (i != size) {
      writeInt(map.keyAt(i))
      writeParcelable(map.valueAt(i), flags)
      i++
    }
  }
}
