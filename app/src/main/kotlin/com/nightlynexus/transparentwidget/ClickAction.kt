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

  class Uninstalled(val invalid: HasIntent) : ClickAction(5)

  companion object {
    fun ComponentName.toClickAction(): ClickAction {
      val intent = Intent()
      intent.component = this
      return Activity(intent)
    }

    fun decodeClickAction(value: String?): ClickAction {
      if (value == null) {
        return Malformed
      }
      return decodeClickAction(value, startIndex = 0)
    }

    private fun decodeClickAction(value: String, startIndex: Int): ClickAction {
      val index = value.indexOf('\n', startIndex)
      if (index == -1) {
        val ordinal = value.toIntOrNull()
        return when (ordinal) {
          null -> Malformed
          3 -> DoNothing
          4 -> Malformed
          else -> Malformed
        }
      }
      val ordinal = value.substring(startIndex, index).toIntOrNull()
      if (ordinal == null) {
        return Malformed
      }
      if (ordinal == 5) {
        if (startIndex != 0) {
          // Uninstalled's invalid ClickAction/HasInput is only one depth.
          // Avoid bad input causing a stack overflow.
          return Malformed
        }
        val innerClickAction = decodeClickAction(value, startIndex = index + 1)
        if (innerClickAction !is HasIntent) {
          return Malformed
        }
        return Uninstalled(invalid = innerClickAction)
      }
      val uri = value.substring(startIndex = index + 1)
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
    if (this is Uninstalled) {
      val invalidOrdinal = (invalid as ClickAction).ordinal
      val invalidUri = invalid.intent.toUri(Intent.URI_INTENT_SCHEME)
      return "$ordinal\n$invalidOrdinal\n$invalidUri"
    }
    return if (this is HasIntent) {
      val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
      "$ordinal\n$uri"
    } else {
      ordinal.toString()
    }
  }
}
