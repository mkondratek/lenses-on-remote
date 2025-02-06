package com.mkondratek.lensesonremote.autoedit

import com.intellij.codeInsight.documentation.DocumentationHtmlUtil
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.contentInnerPadding
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.contentOuterPadding
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.settingsButtonPadding
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.spaceBeforeParagraph
import com.intellij.codeInsight.hint.LineTooltipRenderer
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.lang.documentation.ide.ui.PopupUpdateEvent
import com.intellij.lang.documentation.ide.ui.scrollPaneWithCorner
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel
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
internal const val TOGGLE_SHOW_IN_POPUP_ACTION_ID: String = "AutoEdit.ToggleShowInPopup"
internal const val TOGGLE_AUTO_SHOW_ACTION_ID: String = "AutoEdit.ToggleAutoShow"
internal const val NAVIGATION_GROUP_ID: String = "AutoEdit.Navigation"
internal const val EDIT_SOURCE_ACTION_ID: String = "AutoEdit.EditSource"
val SELECTED_QUICK_DOC_TEXT: DataKey<String> = DataKey.create<String>("QUICK_DOC.SELECTED_TEXT")

internal fun primaryActions(): List<AnAction> = groupActions(PRIMARY_GROUP_ID)

private fun groupActions(groupId: String) =
    listOf(*requireNotNull(ActionUtil.getActionGroup(groupId)).getChildren(null))

const val NEW_JAVADOC_LOCATION_AND_SIZE = "javadoc.popup.new"

internal fun toolbarComponent(actions: ActionGroup, contextComponent: JComponent): JComponent {
  val toolbar =
      ActionManager.getInstance()
          .createActionToolbar(ActionPlaces.JAVADOC_TOOLBAR, actions, true)
          .also {
            it.setSecondaryActionsIcon(AllIcons.Actions.More, true)
            it.setTargetComponent(contextComponent)
            it.setReservePlaceAutoPopupIcon(false)
          }
  return toolbar.component
}

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
    val editorPane = ui.editorPane
    browser.closeTrigger { coroutineScope.launch(Dispatchers.EDT) { myPopup.cancel() } }
    //    val primaryActions = primaryActions().toMutableList()
    //    val secondaryActions = ArrayList<AnAction>()
    //
    // secondaryActions.add(ActionManager.getInstance().getAction(TOGGLE_SHOW_IN_POPUP_ACTION_ID))
    //    secondaryActions.add(ToggleShowDocsOnHoverAction())
    //    secondaryActions.add(ActionManager.getInstance().getAction(TOGGLE_AUTO_SHOW_ACTION_ID))
    //    secondaryActions.add(AdjustFontSizeAction())
    //    secondaryActions.add(RestoreDefaultSizeAction())

    //    val editSourceAction = ActionManager.getInstance().getAction(EDIT_SOURCE_ACTION_ID)
    //    val navigationGroup = ActionManager.getInstance().getAction(NAVIGATION_GROUP_ID)
    //    primaryActions[primaryActions.indexOf(navigationGroup)] =
    //        DefaultActionGroup().apply {
    //          copyFromGroup(navigationGroup as DefaultActionGroup)
    //          remove(editSourceAction)
    //        }

    val gearActions: DefaultActionGroup = MoreActionGroup()
    //    gearActions.addAll(secondaryActions)
    //    gearActions.addSeparator()
    //    gearActions.addAll(primaryActions)

    val corner =
        toolbarComponent(DefaultActionGroup(gearActions), editorPane).apply {
          border = JBUI.Borders.empty(0, 0, contentOuterPadding - 3, settingsButtonPadding - 5)
        }
    ui.trackDocumentationBackgroundChange(this) { corner.background = it }
    component =
        AutoEditPopupPane(ui.scrollPane).also { pane ->
          pane.add(scrollPaneWithCorner(this, ui.scrollPane, corner), BorderLayout.CENTER)
          pane.add(ui.switcherToolbarComponent, BorderLayout.NORTH)
          updatePaddings(corner)
          corner.addComponentListener(
              object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                  updatePaddings(corner)
                  popupUpdateFlow.tryEmit(PopupUpdateEvent.ToolbarSizeChanged)
                }
              })
        }

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

  fun jointHover() {
    // TODO ? separate DocumentationJointHoverUI class
    val bg = UIUtil.getToolTipActionBackground()
    Disposer.register(this, ui.setBackground(bg))
    component.background = bg
    component.border =
        IdeBorderFactory.createBorder(UIUtil.getTooltipSeparatorColor(), SideBorder.TOP)
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

    val editorPane = ui.editorPane
    editorPane.setHint(popup)
  }

  fun updatePopup(updater: suspend (PopupUpdateEvent) -> Unit) {
    coroutineScope.launch(Dispatchers.EDT) { popupUpdateFlow.collectLatest { updater(it) } }
  }

  private fun updatePaddings(toolbar: JComponent) {
    ui.locationLabel.border =
        JBUI.Borders.empty(
            2 + spaceBeforeParagraph,
            LineTooltipRenderer.CONTENT_PADDING,
            2 + contentOuterPadding,
            2 + (toolbar.width / JBUIScale.scale(1f)).toInt())
    val editorPreferredSize = ui.editorPane.preferredSize
    val viewPanel = ui.scrollPane.viewport.view as JPanel
    if (editorPreferredSize.height < toolbar.height * 2 &&
        !ui.locationLabel.isVisible &&
        editorPreferredSize.width + toolbar.width >
            JBUIScale.scale(DocumentationHtmlUtil.docPopupMinWidth)) {
      viewPanel.border =
          JBUI.Borders.emptyRight(
              (toolbar.width / JBUIScale.scale(1f)).toInt() -
                  contentOuterPadding -
                  contentInnerPadding -
                  10)
    } else {
      viewPanel.border = JBUI.Borders.empty()
    }
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

  private fun restoreSize() {
    manuallyResized = false
    DimensionService.getInstance().setSize(NEW_JAVADOC_LOCATION_AND_SIZE, null, project)
    popupUpdateFlow.tryEmit(PopupUpdateEvent.RestoreSize)
  }

  private inner class PopupResizeListener : Runnable {

    override fun run() {
      manuallyResized = true
      DimensionService.getInstance()
          .setSize(NEW_JAVADOC_LOCATION_AND_SIZE, myPopup.contentSize, project)
    }
  }
}
