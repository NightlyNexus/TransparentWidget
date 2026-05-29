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
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.WorkerThread
import java.util.Locale
import kotlin.concurrent.Volatile

internal class DisplayableApp(
  val packageInfo: PackageInfo,
  val label: String,
  val firstLetter: String,
  val launchIntent: Intent?,
  val displayActivities: List<DisplayableActivity>
) : Parcelable {
  @Volatile var icon: Drawable? = null
  var expanded = false

  class DisplayableActivity(
    val activityInfo: ActivityInfo,
    val firstLetter: String
  ) : Parcelable {
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

    override fun writeToParcel(parcel: Parcel, flags: Int) {
      activityInfo.writeToParcel(parcel, flags)
      parcel.writeString(firstLetter)
    }

    override fun describeContents(): Int {
      return 0
    }

    companion object CREATOR : Parcelable.Creator<DisplayableActivity> {
      override fun createFromParcel(parcel: Parcel): DisplayableActivity {
        val activityInfo = ActivityInfo.CREATOR.createFromParcel(parcel)
        val firstLetter = parcel.readString()!!
        return DisplayableActivity(
          activityInfo,
          firstLetter
        )
      }

      override fun newArray(size: Int): Array<DisplayableActivity?> {
        return arrayOfNulls(size)
      }
    }
  }

  @WorkerThread fun loadIcon(
    packageManager: PackageManager
  ): Drawable {
    val loadedIcon = packageInfo.applicationInfo!!.loadIcon(packageManager)
    icon = loadedIcon
    return loadedIcon
  }

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    packageInfo.writeToParcel(parcel, flags)
    parcel.writeString(label)
    parcel.writeString(firstLetter)
    parcel.writeNullableIntent(launchIntent, flags)
    parcel.writeParcelableListCompat(displayActivities, flags)
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Parcelable.Creator<DisplayableApp> {
    override fun createFromParcel(parcel: Parcel): DisplayableApp {
      val packageInfo = PackageInfo.CREATOR.createFromParcel(parcel)
      val label = parcel.readString()!!
      val firstLetter = parcel.readString()!!
      val launchIntent = parcel.readNullableIntent()
      val displayActivities = parcel.readParcelableListCompat(DisplayableActivity.CREATOR)
      return DisplayableApp(
        packageInfo,
        label,
        firstLetter,
        launchIntent,
        displayActivities
      )
    }

    override fun newArray(size: Int): Array<DisplayableApp?> {
      return arrayOfNulls(size)
    }
  }
}

@WorkerThread internal fun PackageManager.loadDisplayableApps(): List<DisplayableApp> {
  val installedPackages = getInstalledPackages(PackageManager.GET_ACTIVITIES)
  val locale = Locale.getDefault()
  val displayableApps = ArrayList<DisplayableApp>()
  for (packageInfo in installedPackages) {
    val appLabel = packageInfo.applicationInfo!!.loadLabel(this).toString()
    val launchIntent = getLaunchIntentForPackage(packageInfo.packageName)
    var packageActivities = packageInfo.activities
    if (packageActivities == null) {
      if (launchIntent == null) {
        continue
      }
      // The launch Intent is not null, but Android gave use no Activities.
      // This seems to be able to happen on some Samsung devices.
      packageActivities = arrayOf(
        launchIntent.resolveActivityInfo(this, PackageManager.GET_META_DATA)
      )
    } else {
      check(packageActivities.isNotEmpty())
    }
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
      if (launchIntent == null) {
        continue
      }
      // The launch Intent is not null, but Android only gave us non-exported Activities.
      // This seems to be able to happen on some Sony devices.
      activities += DisplayableApp.DisplayableActivity(
        launchIntent.resolveActivityInfo(this, PackageManager.GET_META_DATA),
        firstLetter
      )
    }
    displayableApps += DisplayableApp(
      packageInfo,
      appLabel,
      firstLetter,
      launchIntent,
      activities
    )
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
