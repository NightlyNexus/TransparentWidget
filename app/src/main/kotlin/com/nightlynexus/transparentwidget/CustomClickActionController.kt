package com.nightlynexus.transparentwidget

import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import com.nightlynexus.transparentwidget.controller.Controller

internal class CustomClickActionController(
  appWidgetIdsToComponentsStorage: AppWidgetIdsToComponentsStorage,
  onCustomClickActionSelectedListener: OnCustomClickActionSelectedListener,
  appWidgetId: Int,
  parentView: ViewGroup,
  savedState: SavedState?
) : Controller<CustomClickActionController.SavedState> {
  interface OnCustomClickActionSelectedListener {
    fun onCustomClickActionSelected(clickAction: ClickAction)
  }

  private val context = parentView.context
  private val rootView: View

  init {
    val inflater = LayoutInflater.from(context)
    rootView = inflater.inflate(
      R.layout.custom_click_action,
      parentView,
      false
    )
    parentView.requestApplyInsets()

    val uriEditText = rootView.findViewById<TextView>(R.id.uri)
    val activityRadioButton = rootView.findViewById<CompoundButton>(R.id.activity)
    val serviceRadioButton = rootView.findViewById<CompoundButton>(R.id.service)
    val broadcastRadioButton = rootView.findViewById<CompoundButton>(R.id.broadcast)
    val doneButton = rootView.findViewById<View>(R.id.done)

    val existingClickAction: ClickAction?
    if (savedState == null) {
      // Show the existing ClickAction when first entering this screen.
      existingClickAction = appWidgetIdsToComponentsStorage.getClickAction(appWidgetId)
    } else {
      existingClickAction = null
    }

    if (existingClickAction != null) {
      when (existingClickAction) {
        is ClickAction.Activity -> {
          activityRadioButton.isChecked = true
          uriEditText.text = existingClickAction.intent.toUri(0)
        }

        is ClickAction.Service -> {
          serviceRadioButton.isChecked = true
          uriEditText.text = existingClickAction.intent.toUri(0)
        }

        is ClickAction.Broadcast -> {
          broadcastRadioButton.isChecked = true
          uriEditText.text = existingClickAction.intent.toUri(0)
        }

        ClickAction.DoNothing -> {
          // Do nothing.
        }

        ClickAction.Malformed -> {
          // Do nothing.
        }

        is ClickAction.Uninstalled -> {
          when (val invalid = existingClickAction.invalid) {
            is ClickAction.Activity -> {
              activityRadioButton.isChecked = true
              uriEditText.text = invalid.intent.toUri(0)
            }

            is ClickAction.Broadcast -> {
              serviceRadioButton.isChecked = true
              uriEditText.text = invalid.intent.toUri(0)
            }

            is ClickAction.Service -> {
              broadcastRadioButton.isChecked = true
              uriEditText.text = invalid.intent.toUri(0)
            }
          }
        }
      }
    }

    doneButton.setOnClickListener {
      val uri = uriEditText.text.toString()
      val intent = Intent.parseUri(uri, 0)

      val clickAction = if (activityRadioButton.isChecked) {
        ClickAction.Activity(intent)
      } else if (serviceRadioButton.isChecked) {
        ClickAction.Service(intent)
      } else if (broadcastRadioButton.isChecked) {
        ClickAction.Broadcast(intent)
      } else {
        throw AssertionError()
      }

      onCustomClickActionSelectedListener.onCustomClickActionSelected(
        clickAction
      )
    }
  }

  override fun saveState(): SavedState {
    return SavedState()
  }

  override fun getView(): View {
    return rootView
  }

  override fun onDestroy() {
    // Do nothing.
  }

  internal class SavedState() : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
    }

    override fun describeContents(): Int {
      return 0
    }

    companion object CREATOR : Parcelable.Creator<SavedState> {
      override fun createFromParcel(parcel: Parcel): SavedState {
        return SavedState()
      }

      override fun newArray(size: Int): Array<SavedState?> {
        return arrayOfNulls(size)
      }
    }
  }

  internal class Factory(
    private val appWidgetId: Int
  ) : Controller.Factory<CustomClickActionController, SavedState> {
    override fun create(
      dependencies: Map<Any, Any>,
      parentView: ViewGroup,
      savedState: SavedState?
    ): CustomClickActionController {
      val appWidgetIdsToComponentsStorage =
        dependencies[AppWidgetIdsToComponentsStorage::class.java]
          as AppWidgetIdsToComponentsStorage
      val onCustomClickActionSelectedListener =
        dependencies[OnCustomClickActionSelectedListener::class.java]
          as OnCustomClickActionSelectedListener
      return CustomClickActionController(
        appWidgetIdsToComponentsStorage,
        onCustomClickActionSelectedListener,
        appWidgetId,
        parentView,
        savedState
      )
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
      parcel.writeInt(appWidgetId)
    }

    override fun describeContents(): Int {
      return 0
    }

    companion object CREATOR : Parcelable.Creator<Factory> {
      override fun createFromParcel(parcel: Parcel): Factory {
        val appWidgetId = parcel.readInt()
        return Factory(
          appWidgetId
        )
      }

      override fun newArray(size: Int): Array<Factory?> {
        return arrayOfNulls(size)
      }
    }
  }
}
