package com.nightlynexus.transparentwidget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal val executor = Executors.newCachedThreadPool()
private val appWidgetIdsToAnimators = mutableMapOf<Int, ValueAnimator>()

class TransparentAppWidgetProvider : AppWidgetProvider() {
  internal companion object {
    private const val fadeDurationMillis = 2000L

    fun updateAndFade(
      context: Context,
      storage: AppWidgetIdsToComponentsStorage,
      appWidgetManager: AppWidgetManager,
      appWidgetId: Int,
      clickAction: ClickAction
    ) {
      if (clickAction !is ClickAction.HasIntent) {
        updateAndFade(
          context,
          appWidgetManager,
          appWidgetId,
          clickAction,
          icon = null,
          label = null
        )
        return
      }
      val packageManager = context.packageManager
      executor.execute {
        var resultingClickAction = clickAction
        val componentName = clickAction.intent.component
        val activityInfo = if (componentName == null) {
          null
        } else {
          try {
            packageManager.getActivityInfo(componentName)
          } catch (e: PackageManager.NameNotFoundException) {
            // The app is not installed anymore.
            resultingClickAction = ClickAction.Uninstalled
            storage.setClickAction(appWidgetId, ClickAction.Uninstalled)
            null
          }
        }
        val icon: Bitmap?
        val label: String?
        if (activityInfo == null) {
          icon = null
          label = null
        } else {
          icon = activityInfo.loadIcon(packageManager).toBitmapOrNullIfAndOnlyIfEmpty()
          label = activityInfo.loadLabel(packageManager).toString()
        }
        val handler = Handler(Looper.getMainLooper())
        handler.post {
          updateAndFade(
            context,
            appWidgetManager,
            appWidgetId,
            resultingClickAction,
            icon,
            label
          )
        }
      }
    }

    /**
     * You must call this function on a Looper thread because this function uses ValueAnimator.
     */
    private fun updateAndFade(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetId: Int,
      clickAction: ClickAction,
      icon: Bitmap?,
      label: String?
    ) {
      val backgroundColorStart = context.getColor(R.color.widget_background_start)
      val backgroundColorEnd = context.getColor(R.color.widget_background_end)
      val animator = ValueAnimator.ofArgb(backgroundColorStart, backgroundColorEnd)
        .setDuration(fadeDurationMillis)
        .apply {
          addUpdateListener { animation ->
            update(
              context,
              appWidgetManager,
              appWidgetId,
              animation.animatedValue as Int,
              ((1 - animation.animatedFraction) * 255).toInt(),
              clickAction,
              icon,
              label
            )
          }
          addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
              // Do not call onAnimationEnd after canceling the old Animator.
              removeListener(this)
            }

            override fun onAnimationEnd(animation: Animator) {
              appWidgetIdsToAnimators.remove(appWidgetId)
            }
          })
        }
      val oldAnimator = appWidgetIdsToAnimators.put(appWidgetId, animator)
      oldAnimator?.cancel()
      animator.start()
    }

    private fun update(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetId: Int,
      @ColorInt backgroundColor: Int,
      @IntRange(0, 255) alpha: Int,
      clickAction: ClickAction,
      icon: Bitmap?,
      label: String?
    ) {
      val views = RemoteViews(context.packageName, R.layout.transparent_app_widget)
      views.setInt(android.R.id.background, "setBackgroundColor", backgroundColor)
      views.setInt(R.id.widget_icon, "setAlpha", alpha)
      val pendingIntent = if (clickAction is ClickAction.HasIntent) {
        views.setIcon(context, icon, label)

        val requestCode = 0
        val intent = clickAction.intent
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
          PendingIntent.FLAG_IMMUTABLE
        when (clickAction) {
          is ClickAction.Activity -> {
            PendingIntent.getActivity(
              context,
              requestCode,
              intent,
              pendingIntentFlags
            )
          }

          is ClickAction.Service -> {
            PendingIntent.getService(
              context,
              requestCode,
              intent,
              pendingIntentFlags
            )
          }

          is ClickAction.Broadcast -> {
            PendingIntent.getBroadcast(
              context,
              requestCode,
              intent,
              pendingIntentFlags
            )
          }
        }
      } else {
        check(icon == null)
        check(label == null)
        when (clickAction) {
          ClickAction.DoNothing -> {
            views.setNoIcon()
            null
          }

          ClickAction.Malformed, ClickAction.Uninstalled -> {
            views.setNeedsRebindIcon(context)

            val requestCode = 0
            val intent = Intent(context, ConfigurationActivity::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
              PendingIntent.FLAG_IMMUTABLE
            PendingIntent.getActivity(
              context,
              requestCode,
              intent,
              pendingIntentFlags
            )
          }
        }
      }
      views.setOnClickPendingIntent(android.R.id.background, pendingIntent)
      appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun RemoteViews.setIcon(
      context: Context,
      icon: Bitmap?,
      label: String?
    ) {
      setImageViewBitmap(
        R.id.widget_icon,
        icon
      )
      setContentDescription(
        R.id.widget_icon,
        if (label == null) {
          null
        } else {
          context.getString(R.string.activity_icon_content_description, label)
        }
      )
    }

    private fun RemoteViews.setNoIcon() {
      setImageViewBitmap(
        R.id.widget_icon,
        null
      )
      setContentDescription(
        R.id.widget_icon,
        null
      )
    }

    private fun RemoteViews.setNeedsRebindIcon(
      context: Context
    ) {
      setImageViewResource(
        R.id.widget_icon,
        R.drawable.needs_rebind_icon
      )
      setContentDescription(
        R.id.widget_icon,
        context.getText(R.string.needs_rebind_icon_content_description)
      )
    }

    // A Motorola device in the wild seems to have a 0-sized Drawable icon.
    private fun Drawable.toBitmapOrNullIfAndOnlyIfEmpty(): Bitmap? {
      val intrinsicWidth = intrinsicWidth
      val intrinsicHeight = intrinsicHeight
      if (intrinsicWidth == 0 || intrinsicHeight == 0) {
        return null
      }
      return toBitmap(intrinsicWidth, intrinsicHeight)
    }
  }

  // Update the individual appWidgetId from the system-broadcasted update.
  private fun update(
    context: Context,
    storage: AppWidgetIdsToComponentsStorage,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    clickAction: ClickAction,
    asyncPendingResult: PendingResult,
    pendingCount: AtomicInteger
  ) {
    val backgroundColorEnd = context.getColor(R.color.widget_background_end)
    if (clickAction !is ClickAction.HasIntent) {
      update(
        context,
        appWidgetManager,
        appWidgetId,
        backgroundColorEnd,
        alpha = 0,
        clickAction,
        icon = null,
        label = null
      )
      if (pendingCount.decrementAndGet() == 0) {
        asyncPendingResult.finish()
      }
      return
    }
    val packageManager = context.packageManager
    executor.execute {
      var resultingClickAction = clickAction
      val componentName = clickAction.intent.component
      val activityInfo = if (componentName == null) {
        null
      } else {
        try {
          packageManager.getActivityInfo(componentName)
        } catch (e: PackageManager.NameNotFoundException) {
          // The app is not installed anymore.
          resultingClickAction = ClickAction.Uninstalled
          storage.setClickAction(appWidgetId, ClickAction.Uninstalled)
          null
        }
      }
      val icon: Bitmap?
      val label: String?
      if (activityInfo == null) {
        icon = null
        label = null
      } else {
        icon = activityInfo.loadIcon(packageManager).toBitmapOrNullIfAndOnlyIfEmpty()
        label = activityInfo.loadLabel(packageManager).toString()
      }
      update(
        context,
        appWidgetManager,
        appWidgetId,
        backgroundColorEnd,
        0,
        resultingClickAction,
        icon,
        label
      )
      if (pendingCount.decrementAndGet() == 0) {
        asyncPendingResult.finish()
      }
    }
  }

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    if (appWidgetIds.isEmpty()) {
      return
    }
    val appWidgetIdsToComponentsStorage =
      (context.applicationContext as TransparentWidgetApp).appWidgetIdsToComponentsStorage
    val asyncPendingResult = goAsync()
    val pendingCount = AtomicInteger(appWidgetIds.size)
    for (appWidgetId in appWidgetIds) {
      // Don't fade on the system-broadcasted update.
      update(
        context,
        appWidgetIdsToComponentsStorage,
        appWidgetManager,
        appWidgetId,
        appWidgetIdsToComponentsStorage.getClickAction(appWidgetId),
        asyncPendingResult,
        pendingCount
      )
    }
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle
  ) {
    val appWidgetIdsToComponentsStorage =
      (context.applicationContext as TransparentWidgetApp).appWidgetIdsToComponentsStorage
    updateAndFade(
      context,
      appWidgetIdsToComponentsStorage,
      appWidgetManager,
      appWidgetId,
      appWidgetIdsToComponentsStorage.getClickAction(appWidgetId)
    )
  }

  override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    val appWidgetIdsToComponentsStorage =
      (context.applicationContext as TransparentWidgetApp).appWidgetIdsToComponentsStorage
    for (appWidgetId in appWidgetIds) {
      appWidgetIdsToAnimators[appWidgetId]?.cancel()
      appWidgetIdsToComponentsStorage.remove(appWidgetId)
    }
  }

  override fun onEnabled(context: Context) {
  }

  override fun onDisabled(context: Context) {
  }

  override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
    val appWidgetIdsToComponentsStorage =
      (context.applicationContext as TransparentWidgetApp).appWidgetIdsToComponentsStorage
    appWidgetIdsToComponentsStorage.restore(oldWidgetIds, newWidgetIds)
  }
}
