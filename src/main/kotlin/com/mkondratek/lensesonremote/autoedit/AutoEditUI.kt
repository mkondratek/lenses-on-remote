package com.mkondratek.lensesonremote.autoedit

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.DocumentationHintEditorPane
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil
import com.intellij.codeInsight.documentation.DocumentationManager.decorate
import com.intellij.codeInsight.documentation.DocumentationScrollPane
import com.intellij.codeInsight.documentation.getDocumentationFontSize
import com.intellij.codeInsight.documentation.setDocumentationFontSize
import com.intellij.codeInsight.hint.DefinitionSwitcher
import com.intellij.codeInsight.hint.LineTooltipRenderer
import com.intellij.ide.DataManager
import com.intellij.lang.documentation.DocumentationImageResolver
import com.intellij.lang.documentation.DocumentationMarkup.CLASS_CONTENT
import com.intellij.lang.documentation.ide.ui.PopupUpdateEvent
import com.intellij.lang.documentation.ide.ui.PopupUpdateEvent.ContentUpdateKind
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.options.FontSize
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.ide.documentation.DOCUMENTATION_BROWSER
import com.intellij.platform.util.coroutines.flow.collectLatestUndispatched
import com.intellij.ui.FontSizeModel
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingTextTrimmer
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.Color
import java.awt.Rectangle
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane
import kotlin.math.abs
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.Nls

class AutoEditUI(
    val project: Project,
    val browser: AutoEditBrowser,
) : DataProvider, Disposable {

  val scrollPane: JScrollPane
  val editorPane: DocumentationHintEditorPane
  val locationLabel: JLabel
  val fontSize: DocumentationFontSizeModel = DocumentationFontSizeModel()
  val switcherToolbarComponent: JComponent

  private var imageResolver: DocumentationImageResolver? = null
  private val cs = CoroutineScope(Dispatchers.EDT)
  private val myContentUpdates =
      MutableSharedFlow<ContentUpdateKind>(
          replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val myContentSizeUpdates =
      MutableSharedFlow<PopupUpdateEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val contentSizeUpdates: SharedFlow<PopupUpdateEvent> = myContentSizeUpdates.asSharedFlow()
  private val switcher: DefinitionSwitcher<AutoEditRequest>
  private var contentKind: ContentKind? = null

  init {
    scrollPane = DocumentationScrollPane()
    editorPane =
        DocumentationHintEditorPane(project, DocumentationScrollPane.keyboardActions(scrollPane)) {
          imageResolver?.resolveImage(it)
        }
    Disposer.register(this, editorPane)
    scrollPane.addMouseWheelListener(FontSizeMouseWheelListener(fontSize))
    switcher = createSwitcher()
    switcherToolbarComponent =
        switcher.createToolbar().component.apply {
          border =
              JBUI.Borders.empty(
                  DocumentationHtmlUtil.spaceBeforeParagraph,
                  DocumentationHtmlUtil.contentOuterPadding - 4,
                  0,
                  DocumentationHtmlUtil.contentOuterPadding - 4)
        }
    val textTrimmer = SwingTextTrimmer.createCenterTrimmer(0.8f)
    locationLabel =
        object : JLabel() {
              override fun getToolTipText(): String? = if (textTrimmer.isTrimmed) text else null
            }
            .apply {
              iconTextGap = 6
              border =
                  JBUI.Borders.empty(
                      DocumentationHtmlUtil.spaceBeforeParagraph,
                      LineTooltipRenderer.CONTENT_PADDING,
                      2 + DocumentationHtmlUtil.contentOuterPadding,
                      DocumentationHtmlUtil.contentOuterPadding)
              putClientProperty(SwingTextTrimmer.KEY, textTrimmer)
            }
    scrollPane.setViewportView(editorPane, locationLabel)
    trackDocumentationBackgroundChange(this) {
      // Force update of the background color for scroll pane
      @Suppress("UseJBColor") val color = Color(it.rgb)
      editorPane.parent.background = color
      scrollPane.viewport.background = color
      locationLabel.background = color
      switcherToolbarComponent.background = color
    }

    browser.ui = this
    Disposer.register(this, browser)

    val contextMenu =
        PopupHandler.installPopupMenu(
            editorPane, PRIMARY_GROUP_ID, "documentation.pane.content.menu")
    Disposer.register(this) { editorPane.removeMouseListener(contextMenu) }

    DataManager.registerDataProvider(editorPane, this)

    fetchingMessage()
    cs.launch(
        CoroutineName("DocumentationUI content update"), start = CoroutineStart.UNDISPATCHED) {
          browser.pageFlow.collectLatestUndispatched { page -> handlePage(page) }
        }
    cs.launch(
        CoroutineName("DocumentationUI font size update"), start = CoroutineStart.UNDISPATCHED) {
          fontSize.updates.collect {
            editorPane.applyFontProps(it)
            myContentSizeUpdates.emit(PopupUpdateEvent.FontChanged)
          }
        }
    cs.launch(
        CoroutineName("DocumentationUI content size update emission"),
        start = CoroutineStart.UNDISPATCHED) {
          myContentUpdates.collect {
            myContentSizeUpdates.emit(PopupUpdateEvent.ContentChanged(it))
          }
        }
  }

  override fun dispose() {
    cs.cancel("DocumentationUI disposal")
    clearImages()
  }

  override fun getData(dataId: String): Any? {
    return when {
      DOCUMENTATION_BROWSER.`is`(dataId) -> browser
      SELECTED_QUICK_DOC_TEXT.`is`(dataId) ->
          editorPane.selectedText?.replace(160.toChar(), ' ') // IDEA-86633
      else -> null
    }
  }

  fun trackDocumentationBackgroundChange(disposable: Disposable, onChange: (Color) -> Unit) {
    val job =
        cs.launch {
          editorPane.backgroundFlow.collectLatest { withContext(Dispatchers.EDT) { onChange(it) } }
        }
    Disposer.register(disposable) { job.cancel() }
  }

  private fun clearImages() {
    imageResolver = null
  }

  private suspend fun handlePage(page: AutoEditPage) {
    val presentation = page.request.presentation
    val i = switcher.elements.indexOf(page.request)
    if (i < 0) {
      switcher.elements = page.requests.toTypedArray()
      switcher.index = 0
    } else {
      switcher.index = i
    }
    updateSwitcherVisibility()
    page.contentFlow.collectLatest { handleContent(presentation, it) }
  }

  fun updateSwitcherVisibility() {
    val visible = switcher.elements.count() > 1
    switcherToolbarComponent.isVisible = visible
    editorPane.border =
        JBUI.Borders.emptyTop(
            if (visible) 0
            else
                DocumentationHtmlUtil.contentOuterPadding -
                    DocumentationHtmlUtil.spaceBeforeParagraph)
  }

  private suspend fun handleContent(
      presentation: TargetPresentation,
      pageContent: AutoEditPageContent?
  ) {
    when (pageContent) {
      null -> {
        // to avoid flickering: don't show ""Fetching..." message right away, give a chance for
        // documentation to load
        delay(DEFAULT_UI_RESPONSE_TIMEOUT) // this call will be immediately cancelled once a new
        // emission happens
        clearImages()
        fetchingMessage()
        applyUIState()
      }
      AutoEditPageContent.Empty -> {
        clearImages()
        noDocumentationMessage()
        applyUIState()
      }
      is AutoEditPageContent.Content -> {
        clearImages()
        handleContent(presentation, pageContent)
      }
    }
  }

  private suspend fun handleContent(
      presentation: TargetPresentation,
      pageContent: AutoEditPageContent.Content
  ) {
    val content = pageContent.content
    imageResolver = content.imageResolver
    //    val linkChunk = linkChunk(presentation.presentableText, pageContent.links)
    val decorated = decorate(content.html, null, null, pageContent.downloadSourcesLink)
    if (!updateContent(decorated, presentation, ContentKind.DocumentationPage)) {
      return
    }
    val uiState = pageContent.uiState
    if (uiState != null) {
      yield()
      applyUIState()
    }
  }

  private fun fetchingMessage() {
    updateContent(
        message(CodeInsightBundle.message("javadoc.fetching.progress")),
        null,
        ContentKind.InfoMessage)
  }

  private fun noDocumentationMessage() {
    updateContent(
        message(CodeInsightBundle.message("no.documentation.found")), null, ContentKind.InfoMessage)
  }

  private fun message(message: @Nls String): @Nls String {
    return HtmlChunk.div()
        .setClass(CLASS_CONTENT)
        .children(HtmlChunk.p().addText(message))
        .wrapWith("body")
        .wrapWith("html")
        .toString()
  }

  private fun updateContent(
      text: @Nls String,
      presentation: TargetPresentation?,
      newContentKind: ContentKind
  ): Boolean {
    EDT.assertIsEdt()
    if (editorPane.text == text &&
        locationLabel.text == presentation?.locationText &&
        locationLabel.icon == presentation?.icon &&
        contentKind == newContentKind) {
      return false
    }
    val oldContentKind = contentKind
    contentKind = newContentKind
    editorPane.text = text
    if (presentation?.locationText != null) {
      locationLabel.text = presentation.locationText
      locationLabel.toolTipText = presentation.locationText
      locationLabel.icon = presentation.locationIcon
      locationLabel.isVisible = true
    } else {
      locationLabel.text = ""
      locationLabel.isVisible = false
    }
    check(
        myContentUpdates.tryEmit(
            when (newContentKind) {
              ContentKind.InfoMessage -> ContentUpdateKind.InfoMessage
              ContentKind.DocumentationPage ->
                  if (oldContentKind != newContentKind) ContentUpdateKind.DocumentationPageOpened
                  else ContentUpdateKind.DocumentationPageNavigated
            }))
    return true
  }

  private fun applyUIState() {
    editorPane.scrollRectToVisible(Rectangle(0, 0))
    if (ScreenReader.isActive()) {
      editorPane.caretPosition = 0
    }
  }

  private fun createSwitcher(): DefinitionSwitcher<AutoEditRequest> {
    val requests = browser.page.requests
    return DefinitionSwitcher(requests.toTypedArray(), scrollPane) { browser.resetBrowser(it) }
  }

  private enum class ContentKind {
    InfoMessage,
    DocumentationPage,
  }
}

class DocumentationFontSizeModel : FontSizeModel<FontSize> {

  private val myFontSize: MutableStateFlow<FontSize> =
      MutableStateFlow(getDocumentationFontSize()) // initialize with current state

  override var value: FontSize
    get() {
      return myFontSize.value
    }
    set(value) {
      setDocumentationFontSize(value) // persist modifications
      myFontSize.value = value
    }

  override val values: List<FontSize>
    get() = FontSize.entries.toList()

  override val updates: Flow<FontSize> = myFontSize.asSharedFlow()
}

class FontSizeMouseWheelListener(
    private val model: FontSizeModel<FontSize>,
) : MouseWheelListener {

  override fun mouseWheelMoved(e: MouseWheelEvent) {
    if (!EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled ||
        !EditorUtil.isChangeFontSize(e)) {
      // TODO this check must be done outside this listener: listener should not even be registered
      // if this case
      return
    }
    val rotation = e.wheelRotation
    if (rotation == 0) {
      return
    }
    var newFontSize = model.value
    val increase = rotation <= 0
    repeat(abs(rotation)) {
      newFontSize =
          if (increase) {
            newFontSize.larger()
          } else {
            newFontSize.smaller()
          }
    }
    model.value = newFontSize
  }
}
