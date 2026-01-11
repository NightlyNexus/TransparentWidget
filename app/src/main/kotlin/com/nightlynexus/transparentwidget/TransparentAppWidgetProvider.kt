package com.nightlynexus.transparentwidget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
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
      componentName: ComponentName?
    ) {
      if (componentName == null) {
        updateAndFade(
          context,
          appWidgetManager,
          appWidgetId,
          componentName = null,
          icon = null,
          label = null
        )
      } else {
        val packageManager = context.packageManager
        executor.execute {
          val activityInfo = try {
            packageManager.getActivityInfo(componentName)
          } catch (e: PackageManager.NameNotFoundException) {
            // The app is not installed anymore.
            storage.setComponent(appWidgetId, null)
            null
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
              componentName,
              icon,
              label
            )
          }
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
      componentName: ComponentName?,
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
              componentName,
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
      componentName: ComponentName?,
      icon: Bitmap?,
      label: String?
    ) {
      val views = RemoteViews(context.packageName, R.layout.transparent_app_widget)
      views.setInt(android.R.id.background, "setBackgroundColor", backgroundColor)
      views.setInt(R.id.widget_icon, "setAlpha", alpha)
      val intent = Intent().apply {
        component = componentName
      }
      views.setImageViewBitmap(R.id.widget_icon, icon)
      if (label == null) {
        views.setContentDescription(
          R.id.widget_icon,
          null
        )
      } else {
        views.setContentDescription(
          R.id.widget_icon,
          context.getString(R.string.activity_icon_content_description, label)
        )
      }
      val pendingIntent =
        PendingIntent.getActivity(
          context,
          0,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
      views.setOnClickPendingIntent(android.R.id.background, pendingIntent)
      appWidgetManager.updateAppWidget(appWidgetId, views)
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
    componentName: ComponentName?,
    asyncPendingResult: PendingResult,
    pendingCount: AtomicInteger
  ) {
    val backgroundColorEnd = context.getColor(R.color.widget_background_end)
    if (componentName == null) {
      update(
        context,
        appWidgetManager,
        appWidgetId,
        backgroundColorEnd,
        alpha = 0,
        componentName = null,
        icon = null,
        label = null
      )
      if (pendingCount.decrementAndGet() == 0) {
        asyncPendingResult.finish()
      }
    } else {
      val packageManager = context.packageManager
      executor.execute {
        val activityInfo = try {
          packageManager.getActivityInfo(componentName)
        } catch (e: PackageManager.NameNotFoundException) {
          // The app is not installed anymore.
          storage.setComponent(appWidgetId, null)
          null
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
          componentName,
          icon,
          label
        )
        if (pendingCount.decrementAndGet() == 0) {
          asyncPendingResult.finish()
        }
      }
    }
  }

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
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
        appWidgetIdsToComponentsStorage.getComponentName(appWidgetId),
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
      appWidgetIdsToComponentsStorage.getComponentName(appWidgetId)
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
  }
}
