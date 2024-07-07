package com.nightlynexus.transparentwidget

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.icu.text.Collator
import android.icu.util.ULocale
import android.os.Build.VERSION.SDK_INT
import androidx.annotation.WorkerThread
import java.util.Locale
import kotlin.concurrent.Volatile

internal class DisplayableApp(
  val packageInfo: PackageInfo,
  val label: String,
  val firstLetter: CharSequence,
  val launchIntent: Intent?,
  val displayActivities: List<DisplayableActivity>
) {
  @Volatile var icon: Drawable? = null
  var expanded = false

  class DisplayableActivity(
    val activityInfo: ActivityInfo,
    val firstLetter: String
  ) {
    val shortName = run {
      /*
       * Return the class name, either fully qualified or in a shortened form
       * (with a leading '.') if it is a suffix of the package.
       */
      val packageName = activityInfo.packageName
      val className = activityInfo.name
      if (className.startsWith(packageName)) {
        val packageNameLength: Int = packageName.length
        val classNameLength: Int = className.length
        if (classNameLength > packageNameLength && className[packageNameLength] == '.') {
          return@run className.substring(packageNameLength, classNameLength)
        }
      }
      return@run className
    }
    @Volatile var icon: Drawable? = null
    @Volatile var label: String? = null

    @WorkerThread fun loadIcon(
      packageManager: PackageManager
    ): Drawable {
      val loadedIcon = activityInfo.loadIcon(packageManager)
      icon = loadedIcon
      return loadedIcon
    }

    @WorkerThread fun loadLabel(
      packageManager: PackageManager
    ): String {
      val loadedLabel = activityInfo.loadLabel(packageManager).toString()
      label = loadedLabel
      return loadedLabel
    }
  }

  @WorkerThread fun loadIcon(
    packageManager: PackageManager
  ): Drawable {
    val loadedIcon = packageInfo.applicationInfo.loadIcon(packageManager)
    icon = loadedIcon
    return loadedIcon
  }
}

@WorkerThread internal fun PackageManager.loadDisplayableApps(): List<DisplayableApp> {
  val installedPackages = getInstalledPackages(PackageManager.GET_ACTIVITIES)
  val locale = Locale.getDefault()
  val displayableApps = ArrayList<DisplayableApp>()
  for (packageInfo in installedPackages) {
    val appLabel = packageInfo.applicationInfo.loadLabel(this).toString()
    val launchIntent = getLaunchIntentForPackage(packageInfo.packageName)
    val packageActivities = packageInfo.activities
    if (packageActivities == null) {
      check(launchIntent == null)
    } else {
      check(packageActivities.isNotEmpty())
      val firstLetter = if (appLabel.isEmpty()) {
        ""
      } else {
        appLabel.substring(
          0,
          appLabel.offsetByCodePoints(0, 1)
        ).run {
          if (launchIntent == null) {
            lowercase(locale)
          } else {
            uppercase(locale)
          }
        }
      }
      val activities = ArrayList<DisplayableApp.DisplayableActivity>()
      for (packageActivity in packageActivities) {
        if (packageActivity.exported) {
          activities += DisplayableApp.DisplayableActivity(
            packageActivity,
            firstLetter
          )
        }
      }
      if (activities.isEmpty()) {
        check(launchIntent == null)
      } else {
        displayableApps += DisplayableApp(
          packageInfo,
          appLabel,
          firstLetter,
          launchIntent,
          activities
        )
      }
    }
  }
  val collator = Collator.getInstance(ULocale.getDefault()).apply {
    strength = Collator.PRIMARY
  }
  displayableApps.sortWith { displayableApp1, displayableApp2 ->
    if (displayableApp1.launchIntent != null && displayableApp2.launchIntent == null) {
      return@sortWith -1
    }
    if (displayableApp1.launchIntent == null && displayableApp2.launchIntent != null) {
      return@sortWith 1
    }
    val caseInsensitiveAppNameComparison = collator.compare(
      displayableApp1.label,
      displayableApp2.label
    )
    if (caseInsensitiveAppNameComparison != 0) {
      return@sortWith caseInsensitiveAppNameComparison
    }
    return@sortWith displayableApp1.packageInfo.packageName.compareTo(
      displayableApp2.packageInfo.packageName
    )
  }
  return displayableApps
}

@WorkerThread
internal fun PackageManager.getActivityInfo(componentName: ComponentName): ActivityInfo {
  return if (SDK_INT >= 33) {
    getActivityInfo(
      componentName,
      PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong())
    )
  } else {
    getActivityInfo(
      componentName,
      PackageManager.GET_META_DATA
    )
  }
}
