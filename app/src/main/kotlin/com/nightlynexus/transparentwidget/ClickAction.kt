package com.nightlynexus.transparentwidget

import android.content.ComponentName
import android.content.Intent
import java.net.URISyntaxException

internal sealed class ClickAction private constructor(private val ordinal: Int) {
  sealed interface HasIntent {
    val intent: Intent
  }

  class Activity(override val intent: Intent) : ClickAction(0), HasIntent

  class Service(override val intent: Intent) : ClickAction(1), HasIntent

  class Broadcast(override val intent: Intent) : ClickAction(2), HasIntent

  object DoNothing : ClickAction(3)

  object Malformed : ClickAction(4)

  object Uninstalled : ClickAction(5)

  companion object {
    fun ComponentName.toClickAction(): ClickAction {
      val intent = Intent().apply {
        component = this@toClickAction
      }
      return Activity(intent)
    }

    fun decodeClickAction(value: String?): ClickAction {
      if (value == null) {
        return Malformed
      }
      val index = value.indexOf('\n')
      if (index == -1) {
        val ordinal = value.toIntOrNull()
        return when (ordinal) {
          null -> Malformed
          3 -> DoNothing
          4 -> Malformed
          5 -> Uninstalled
          else -> Malformed
        }
      }
      val ordinal = value.substring(0, index).toIntOrNull()
      if (ordinal == null) {
        return Malformed
      }
      val uri = value.substring(index + 1)
      val intent = try {
        Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
      } catch (e: URISyntaxException) {
        return Malformed
      }
      return when (ordinal) {
        0 -> Activity(intent)
        1 -> Service(intent)
        2 -> Broadcast(intent)
        else -> Malformed
      }
    }
  }

  fun encode(): String {
    return if (this is HasIntent) {
      val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
      "$ordinal\n$uri"
    } else {
      ordinal.toString()
    }
  }
}
