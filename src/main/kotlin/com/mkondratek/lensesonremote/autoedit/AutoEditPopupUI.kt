package com.mkondratek.lensesonremote.autoedit

import com.intellij.ide.DataManager
import com.intellij.lang.documentation.ide.ui.PopupUpdateEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import java.util.function.Supplier
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch

internal val DOCUMENTATION_POPUP: DataKey<JBPopup> = DataKey.create("cody.autoedit.popup")

internal const val PRIMARY_GROUP_ID: String = "AutoEdit.PrimaryGroup"
val SELECTED_QUICK_DOC_TEXT: DataKey<String> = DataKey.create<String>("QUICK_DOC.SELECTED_TEXT")

const val NEW_JAVADOC_LOCATION_AND_SIZE = "javadoc.popup.new"

class AutoEditPopupUI(
    private val project: Project,
    ui: AutoEditUI,
) : Disposable {

  private var _ui: AutoEditUI? = ui
  val ui: AutoEditUI
    get() = requireNotNull(_ui) { "already detached" }

  val browser: AutoEditBrowser
    get() = ui.browser

  val component: JComponent
  val preferableFocusComponent: JComponent
    get() = ui.editorPane

  val coroutineScope: CoroutineScope = CoroutineScope(Job())
  private val popupUpdateFlow =
      MutableSharedFlow<PopupUpdateEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private lateinit var myPopup: AbstractPopup

  init {
    browser.closeTrigger { coroutineScope.launch(Dispatchers.EDT) { myPopup.cancel() } }

    val configuration =
        JBHtmlPaneConfiguration.builder()
            .customStyleSheet("h1 { margin: 0px; padding: 0px; }")
            .build()
    component = ui.scrollPane
//                JBHtmlPane(JBHtmlPaneStyleConfiguration(), configuration).also { pane ->
//        JPanel(BorderLayout()).also { pane ->
//          pane.add(ui.scrollPane)
//          }

    coroutineScope.launch { popupUpdateFlow.emitAll(ui.contentSizeUpdates) }
  }

  override fun dispose() {
    coroutineScope.cancel()
    val ui = _ui
    if (ui != null) {
      Disposer.dispose(ui)
      _ui = null
    }
  }

  fun setPopup(popup: AbstractPopup) {
    Disposer.register(popup, this)
    myPopup = popup

    DataManager.registerDataProvider(component) { dataId ->
      if (DOCUMENTATION_POPUP.`is`(dataId)) {
        popup
      } else {
        null
      }
    }
  }

  fun updatePopup(updater: suspend (PopupUpdateEvent) -> Unit) {
    coroutineScope.launch(Dispatchers.EDT) { popupUpdateFlow.collectLatest { updater(it) } }
  }

  private var manuallyResized = false

  fun useStoredSize(): Supplier<Boolean> {
    EDT.assertIsEdt()
    myPopup.addResizeListener(PopupResizeListener(), this)
    val storedSize = DimensionService.getInstance().getSize(NEW_JAVADOC_LOCATION_AND_SIZE, project)
    if (storedSize != null && storedSize.width > 50 && storedSize.height > 50) {
      manuallyResized = true
      myPopup.size = storedSize
    }
    // this makes impossible to use manuallyResized without installing listener
    return Supplier {
      EDT.assertIsEdt()
      manuallyResized
    }
  }

  private inner class PopupResizeListener : Runnable {

    override fun run() {
      manuallyResized = true
      DimensionService.getInstance()
          .setSize(NEW_JAVADOC_LOCATION_AND_SIZE, myPopup.contentSize, project)
    }
  }
}
