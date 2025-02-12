package com.mkondratek.lensesonremote.autoedit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AutoEditManager(private val project: Project, private val cs: CoroutineScope) : Disposable {
  companion object {
    fun getInstance(project: Project): AutoEditManager = project.service()
  }

  // separate scope is needed for the ability to cancel its children
  private val popupScope: CoroutineScope = cs.childScope()

  override fun dispose() {
    cs.cancel()
  }

  fun actionPerformed(dataContext: DataContext, popupDependencies: Disposable? = null) {
    EDT.assertIsEdt()

    val editor = dataContext.getData(CommonDataKeys.EDITOR)
    val popupContext = DefaultPopupContext(project, editor)
    showAutoEdit(popupContext, popupDependencies)
  }

  private var popup: WeakReference<AbstractPopup>? = null

  private fun getPopup(): AbstractPopup? {
    EDT.assertIsEdt()
    val popup: AbstractPopup? = popup?.get()
    if (popup == null) {
      return null
    }
    if (!popup.isVisible) {
      // hint's window might've been hidden by AWT without notifying us
      // dispose to remove the popup from IDE hierarchy and avoid leaking components
      popup.cancel()
      check(this.popup == null)
      return null
    }
    return popup
  }

  private fun setPopup(popup: AbstractPopup, popupDependencies: Disposable?) {
    EDT.assertIsEdt()
    this.popup = WeakReference(popup)
    Disposer.register(popup) {
      EDT.assertIsEdt()
      this.popup = null
    }
    popupDependencies?.let { Disposer.register(popup, it) }
  }

  private fun showAutoEdit(popupContext: PopupContext, popupDependencies: Disposable? = null) {
    if (getPopup() != null) {
      return
    }
    popupScope.coroutineContext.job.cancelChildren()
    popupScope.launch(
        context = Dispatchers.EDT + ModalityState.current().asContextElement(),
        start = CoroutineStart.UNDISPATCHED) {
          val popup = showAutoEditPopup(project, popupContext)
          setPopup(popup, popupDependencies)
        }
  }
}
