package com.nightlynexus.transparentwidget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.Volatile
import me.zhanghai.android.fastscroll.DefaultAnimationHelper
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupTextProvider

internal class AllAppsListView(context: Context, attrs: AttributeSet) :
  RecyclerView(context, attrs) {
  private val packageManager = context.packageManager
  private val adapter = Adapter()
  private lateinit var handler: Handler
  private lateinit var onComponentNameClickedListener: OnComponentNameClickedListener
  @Volatile private var attached = false

  interface OnComponentNameClickedListener {
    fun onComponentNameClicked(componentName: ComponentName?)
  }

  init {
    setAdapter(adapter)
    layoutManager = LinearLayoutManager(context)
    itemAnimator = DefaultItemAnimator().apply {
      addDuration = 50L
      removeDuration = 50L
      moveDuration = 150L
    }
  }

  fun setOnComponentNameClickedListener(listener: OnComponentNameClickedListener) {
    onComponentNameClickedListener = listener
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    attached = true
    handler = getHandler()
    executor.execute {
      val displayableApps = packageManager.loadDisplayableApps()
      handler.post {
        adapter.setApps(displayableApps)
        FastScrollerBuilder(this)
          .useMd2Style()
          .apply {
            setAnimationHelper(object : DefaultAnimationHelper(this@AllAppsListView) {
              override fun getScrollbarAutoHideDelayMillis(): Int {
                return 250
              }
            })
          }
          .build()
      }
      displayableApps.preloadApps()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    attached = false
  }

  private fun List<DisplayableApp>.preloadApps() {
    executor.execute {
      for (i in indices) {
        if (!attached) {
          return@execute
        }
        val displayableApp = this[i]
        if (displayableApp.icon == null) {
          displayableApp.loadIcon(packageManager)
        }
        if (displayableApp.launchIntent == null) {
          for (j in displayableApp.displayActivities.indices) {
            val displayActivity = displayableApp.displayActivities[j]
            displayActivity.loadIcon(packageManager)
            displayActivity.loadLabel(packageManager)
          }
        }
      }
    }
  }

  private inner class Adapter : RecyclerView.Adapter<ViewHolder>(), PopupTextProvider {
    private inner class NoComponentViewHolder(itemView: View) : ViewHolder(itemView)

    private inner class LoadingViewHolder(itemView: View) : ViewHolder(itemView)

    private inner class AppViewHolder(itemView: View) : ViewHolder(itemView) {
      val iconView = itemView.findViewById<ImageView>(R.id.app_icon)!!
      val labelView = itemView.findViewById<TextView>(R.id.app_label)!!
      val expandCollapseView = itemView.findViewById<ImageView>(R.id.app_expand_collapse)!!
      var setImageRunnable: SetImageRunnable? = null
    }

    private inner class ActivityViewHolder(itemView: View) : ViewHolder(itemView) {
      val iconView = itemView.findViewById<ImageView>(R.id.activity_icon)!!
      val labelView = itemView.findViewById<TextView>(R.id.activity_label)!!
      val nameView = itemView.findViewById<TextView>(R.id.activity_name)!!
      var setImageAndLabelRunnable: SetImageAndLabelRunnable? = null
    }

    private val appsAndActivities = mutableListOf<Any>()

    fun setApps(apps: List<DisplayableApp>) {
      var oldSize = appsAndActivities.size
      if (oldSize == 0) {
        oldSize = 1 // The loading view.
      }
      appsAndActivities.clear()
      adapter.notifyItemRangeRemoved(1, oldSize)
      for (i in apps.indices) {
        val app = apps[i]
        appsAndActivities += app
        if (app.launchIntent == null) {
          appsAndActivities.addAll(app.displayActivities)
        }
      }
      adapter.notifyItemRangeInserted(1, appsAndActivities.size)
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
      return when (val item = appsAndActivities[position]) {
        is DisplayableApp -> {
          item.firstLetter
        }

        is DisplayableApp.DisplayableActivity -> {
          item.firstLetter
        }

        else -> {
          throw IllegalStateException(item::class.java.toString())
        }
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val inflater = LayoutInflater.from(parent.context)
      val root = inflater.inflate(viewType, parent, false)
      return when (viewType) {
        R.layout.no_component_list_item -> {
          root.setOnClickListener {
            onComponentNameClickedListener.onComponentNameClicked(null)
          }
          NoComponentViewHolder(root)
        }

        R.layout.loading_list_item -> LoadingViewHolder(root)
        R.layout.app_list_item -> AppViewHolder(root)
        R.layout.activity_list_item -> ActivityViewHolder(root)
        else -> throw IllegalStateException(viewType.toString())
      }
    }

    override fun getItemCount(): Int {
      val size = appsAndActivities.size
      return if (size == 0) {
        2
      } else {
        size + 1
      }
    }

    override fun getItemViewType(position: Int): Int {
      if (position == 0) {
        return R.layout.no_component_list_item
      }
      if (position == 1 && appsAndActivities.isEmpty()) {
        return R.layout.loading_list_item
      }
      // Decrease the index by 1 for the no component view at the top.
      return when (val item = appsAndActivities[position - 1]) {
        is DisplayableApp -> R.layout.app_list_item
        is DisplayableApp.DisplayableActivity -> R.layout.activity_list_item
        else -> throw IllegalStateException(item::class.toString())
      }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      when (holder) {
        is NoComponentViewHolder -> {
          // No-op.
        }

        is LoadingViewHolder -> {
          // No-op.
        }

        is AppViewHolder -> onBindViewHolder(holder, position)
        is ActivityViewHolder -> onBindViewHolder(holder, position)
        else -> throw IllegalStateException(holder::class.toString())
      }
    }

    private fun onBindViewHolder(holder: AppViewHolder, position: Int) {
      // Decrease the index by 1 for the no component view at the top.
      val displayableApp = appsAndActivities[position - 1] as DisplayableApp
      holder.labelView.text = displayableApp.label
      val previousSetImageRunnable = holder.setImageRunnable
      if (previousSetImageRunnable != null) {
        previousSetImageRunnable.resetDisplayableApp = displayableApp
      }
      if (displayableApp.icon == null) {
        holder.iconView.setImageDrawable(null)
        holder.iconView.contentDescription = null
        if (previousSetImageRunnable == null
          || previousSetImageRunnable.displayableApp != displayableApp) {
          val setImageRunnable = SetImageRunnable(
            displayableApp,
            packageManager,
            handler,
            holder.iconView
          )
          holder.setImageRunnable = setImageRunnable
          executor.execute(setImageRunnable)
        }
      } else {
        holder.iconView.setImageDrawable(displayableApp.icon)
        holder.iconView.contentDescription = context.getString(
          R.string.app_icon_content_description,
          displayableApp.label
        )
      }
      if (displayableApp.expanded) {
        holder.expandCollapseView.setImageResource(R.drawable.ic_collapse)
        holder.expandCollapseView.contentDescription =
          context.getText(R.string.collapse_content_description)
      } else {
        holder.expandCollapseView.setImageResource(R.drawable.ic_expand)
        holder.expandCollapseView.contentDescription =
          context.getText(R.string.expand_content_description)
      }
      holder.expandCollapseView.setOnClickListener {
        val adapterPosition = holder.bindingAdapterPosition
        // Decrease the index by 1 for the no component view at the top.
        val index = adapterPosition - 1
        if (displayableApp.expanded) {
          displayableApp.expanded = false
          holder.expandCollapseView.setImageResource(R.drawable.ic_expand)
          holder.expandCollapseView.contentDescription =
            context.getText(R.string.expand_content_description)
          val removeCount = displayableApp.displayActivities.size
          var i = removeCount
          while (i != 0) {
            appsAndActivities.removeAt(index + i)
            i--
          }
          notifyItemRangeRemoved(adapterPosition + 1, removeCount)
        } else {
          displayableApp.expanded = true
          holder.expandCollapseView.setImageResource(R.drawable.ic_collapse)
          holder.expandCollapseView.contentDescription =
            context.getText(R.string.collapse_content_description)
          appsAndActivities.addAll(index + 1, displayableApp.displayActivities)
          notifyItemRangeInserted(adapterPosition + 1, displayableApp.displayActivities.size)
          if (!canScrollVertically(1)) {
            // Increase the position by 1 for the no component view at the top.
            scrollToPosition(appsAndActivities.size)
            // Scroll through the padding. I tried scrolling 16 dips, but it wasn't enough.
            // There was still unscrolled padding at the bottom. I don't know why.
            scrollBy(0, 999)
          }
        }
      }
      if (displayableApp.launchIntent == null) {
        holder.expandCollapseView.visibility = GONE
        holder.itemView.isClickable = false
      } else {
        holder.expandCollapseView.visibility = VISIBLE
        holder.itemView.setOnClickListener {
          val componentName = displayableApp.launchIntent.component!!
          onComponentNameClickedListener.onComponentNameClicked(componentName)
        }
      }
      displayableApp.preloadActivities()
    }

    private fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
      // Decrease the index by 1 for the no component view at the top.
      val displayableActivity =
        appsAndActivities[position - 1] as DisplayableApp.DisplayableActivity
      holder.nameView.text = displayableActivity.activityInfo.name
      val previousSetImageAndLabelRunnable = holder.setImageAndLabelRunnable
      if (previousSetImageAndLabelRunnable != null) {
        previousSetImageAndLabelRunnable.resetDisplayableActivity = displayableActivity
      }
      if (displayableActivity.icon == null) {
        holder.iconView.setImageDrawable(null)
        holder.iconView.contentDescription = null
        holder.labelView.text = null
        if (previousSetImageAndLabelRunnable == null
          || previousSetImageAndLabelRunnable.displayableActivity != displayableActivity) {
          val newSetLabelAndImageRunnable = SetImageAndLabelRunnable(
            displayableActivity,
            packageManager,
            handler,
            holder.iconView,
            holder.labelView
          )
          holder.setImageAndLabelRunnable = newSetLabelAndImageRunnable
          executor.execute(newSetLabelAndImageRunnable)
        }
      } else {
        holder.iconView.setImageDrawable(displayableActivity.icon)
        val label = displayableActivity.label
        holder.iconView.contentDescription = context.getString(
          R.string.activity_icon_content_description,
          label
        )
        holder.labelView.text = label
      }
      holder.itemView.setOnClickListener {
        onComponentNameClickedListener.onComponentNameClicked(
          ComponentName(
            displayableActivity.activityInfo.packageName,
            displayableActivity.activityInfo.name
          )
        )
      }
    }

    private fun DisplayableApp.preloadActivities() {
      executor.execute {
        for (i in displayActivities.indices) {
          val displayableActivity = displayActivities[i]
          if (displayableActivity.icon == null) {
            displayableActivity.loadIcon(packageManager)
            displayableActivity.loadLabel(packageManager)
          }
        }
      }
    }

    private inner class SetImageRunnable(
      val displayableApp: DisplayableApp,
      private val packageManager: PackageManager,
      private val handler: Handler,
      private val iconView: ImageView
    ) : Runnable {
      var resetDisplayableApp: DisplayableApp = displayableApp

      override fun run() {
        val icon = displayableApp.loadIcon(packageManager)
        handler.post {
          if (resetDisplayableApp != displayableApp) {
            return@post
          }
          iconView.setImageDrawable(icon)
          iconView.contentDescription = context.getString(
            R.string.app_icon_content_description,
            displayableApp.label
          )
        }
      }
    }

    private inner class SetImageAndLabelRunnable(
      val displayableActivity: DisplayableApp.DisplayableActivity,
      private val packageManager: PackageManager,
      private val handler: Handler,
      private val iconView: ImageView,
      private val labelView: TextView
    ) : Runnable {
      var resetDisplayableActivity: DisplayableApp.DisplayableActivity = displayableActivity

      override fun run() {
        val icon = displayableActivity.loadIcon(packageManager)
        val label = displayableActivity.loadLabel(packageManager)
        handler.post {
          if (resetDisplayableActivity != displayableActivity) {
            return@post
          }
          iconView.setImageDrawable(icon)
          iconView.contentDescription = context.getString(
            R.string.activity_icon_content_description,
            label
          )
          labelView.text = label
        }
      }
    }
  }
}
