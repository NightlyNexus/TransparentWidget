package com.nightlynexus.transparentwidget

import android.content.Intent
import android.os.Parcel
import android.os.Parcelable

private fun Parcel.writeBooleanCompat(value: Boolean) {
  return writeByte(if (value) 1 else 0)
}

private fun Parcel.readBooleanCompat(): Boolean {
  return readByte() == 1.toByte()
}

internal fun Parcel.writeNullableIntent(value: Intent?, flags: Int) {
  if (value == null) {
    writeBooleanCompat(false)
    return
  }
  writeBooleanCompat(true)
  return value.writeToParcel(this, flags)
}

internal fun Parcel.readNullableIntent(): Intent? {
  if (!readBooleanCompat()) {
    return null
  }
  return Intent.CREATOR.createFromParcel(this)
}

internal fun <T: Parcelable> Parcel.writeParcelableListCompat(
  value: List<T>,
  flags: Int
) {
  writeInt(value.size)
  for (i in value.indices) {
    value[i].writeToParcel(this, flags)
  }
}

internal fun <T: Parcelable> Parcel.readParcelableListCompat(
  creator: Parcelable.Creator<T>
): List<T> {
  val size = readInt()
  val list = ArrayList<T>(size)
  for (i in 1..size) {
    list += creator.createFromParcel(this)
  }
  return list
}

internal fun <T: Parcelable> Parcel.writeNullableParcelableListCompat(
  value: List<T>?,
  flags: Int
) {
  if (value == null) {
    writeBooleanCompat(false)
    return
  }
  writeBooleanCompat(true)
  writeParcelableListCompat(value, flags)
}

internal fun <T: Parcelable> Parcel.readNullableParcelableListCompat(
  creator: Parcelable.Creator<T>
): List<T>? {
  if (!readBooleanCompat()) {
    return null
  }
  return readParcelableListCompat(creator)
}
