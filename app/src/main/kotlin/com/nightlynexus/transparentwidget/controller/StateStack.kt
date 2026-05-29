package com.nightlynexus.transparentwidget.controller

import android.os.Parcelable
import android.util.SparseArray
import android.view.ViewGroup

internal class StateStack(
  private val dependencies: Map<Any, Any>,
  private val parentView: ViewGroup,
  private val index: Int,
  stack: List<ControllerState<out Controller<out Parcelable>, out Parcelable>>,
  controllerState: ControllerState<Controller<Parcelable>, Parcelable>?
) {
  private val stack = ArrayList(stack)
  private var currentControllerFactory:
    Controller.Factory<out Controller<out Parcelable>, out Parcelable>? = null
  private var currentController: Controller<out Parcelable>? = null

  init {
    if (controllerState != null) {
      val controllerFactory = controllerState.factory
      val viewState = controllerState.viewState
      val savedState = controllerState.savedState
      val controller = controllerFactory.create(dependencies, parentView, savedState)
      val controllerView = controller.getView()
      controllerView.restoreHierarchyState(viewState)
      controllerView.isSaveFromParentEnabled = false
      currentControllerFactory = controllerFactory
      currentController = controller
      parentView.addView(controller.getView(), index)
    }
  }

  fun getStack(): ArrayList<ControllerState<out Controller<out Parcelable>, out Parcelable>> {
    return ArrayList(stack)
  }

  fun getCurrentController(): Controller<out Parcelable>? {
    return currentController
  }

  fun saveCurrent(): ControllerState<out Controller<out Parcelable>, out Parcelable> {
    val currentController = currentController
    checkNotNull(currentController) {
      "Nothing to save."
    }
    val viewState = currentController.saveViewState()
    val savedState = currentController.saveState()
    return ControllerState(
      currentControllerFactory
        as Controller.Factory<out Controller<Parcelable>, Parcelable>,
      viewState,
      savedState
    )
  }

  fun <S : Parcelable> push(controllerFactory: Controller.Factory<out Controller<S>, S>) {
    val currentController = currentController
    if (currentController != null) {
      val viewState = currentController.saveViewState()
      val savedState = currentController.saveState()
      val controllerState = ControllerState(
        currentControllerFactory
          as Controller.Factory<out Controller<Parcelable>, Parcelable>,
        viewState,
        savedState
      )
      stack += controllerState
      currentController.onDestroy()
      parentView.removeViewAt(index)
    }

    currentControllerFactory = controllerFactory
    val controller = controllerFactory.create(dependencies, parentView, savedState = null)
    controller.getView().isSaveFromParentEnabled = false
    this.currentController = controller
    parentView.addView(controller.getView(), index)
  }

  fun pop() {
    val currentController = currentController
    checkNotNull(currentController) {
      "Nothing to pop."
    }
    currentController.onDestroy()
    parentView.removeViewAt(index)

    val controllerState = stack.removeAt(stack.lastIndex)
    val controllerFactory = controllerState.factory
      as Controller.Factory<out Controller<Parcelable>, Parcelable>
    val viewState = controllerState.viewState
    val savedState = controllerState.savedState
    val controller = controllerFactory.create(dependencies, parentView, savedState)
    val controllerView = controller.getView()
    controllerView.restoreHierarchyState(viewState)
    controllerView.isSaveFromParentEnabled = false
    currentControllerFactory = controllerFactory
    this.currentController = controller
    parentView.addView(controller.getView(), index)
  }

  fun isEmpty(): Boolean {
    return stack.isEmpty()
  }

  private fun Controller<*>.saveViewState(): SparseArray<Parcelable> {
    val viewState = SparseArray<Parcelable>()
    getView().saveHierarchyState(viewState)
    return viewState
  }
}
