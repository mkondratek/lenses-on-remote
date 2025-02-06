package com.mkondratek.lensesonremote.autoedit

import com.intellij.codeInsight.documentation.actions.DocumentationDownloader
import com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_END
import com.intellij.lang.documentation.ide.impl.DocumentationUsageCollector
import com.intellij.lang.documentation.ide.ui.ExpandableDefinition
import com.intellij.model.Pointer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.AsyncDocumentation
import com.intellij.platform.backend.documentation.ContentUpdater
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AutoEditPage(val requests: List<AutoEditRequest>, val project: Project) {

  private val myContentFlow = MutableStateFlow<AutoEditPageContent?>(null)
  val request = requests.first()
  val contentFlow: SharedFlow<AutoEditPageContent?> = myContentFlow.asSharedFlow()
  val currentContent: AutoEditPageContent.Content?
    get() = myContentFlow.value as? AutoEditPageContent.Content

  var expandableDefinition: ExpandableDefinition? = null

  /** @return `true` if some content was loaded, `false` if content is empty */
  suspend fun waitForContent(): Boolean {
    return myContentFlow.filterNotNull().first() is AutoEditPageContent.Content
  }

  suspend fun loadPage() {
    val data =
        try {
          computeDocumentation(request.targetPointer)
        } catch (e: IndexNotReadyException) {
          null // normal situation, nothing to do
        }
    if (data == null) {
      myContentFlow.value = AutoEditPageContent.Empty
      return
    }
    val uiState = data.anchor?.let(UIState::ScrollToAnchor) ?: UIState.Reset
    expandableDefinition = createExpandableDefinition(data.content)
    myContentFlow.value =
        prepareContent(
            data.content.copy(html = expandableDefinition?.getDecorated() ?: data.content.html),
            data.links,
            uiState)
    update(data.updates, data.links)
  }

  suspend fun updatePage(contentUpdater: ContentUpdater) {
    val pageContent = checkNotNull(currentContent)
    val (html, imageResolver) = pageContent.content
    val updates =
        contentUpdater.prepareContentUpdates(html).map { AutoEditContentData(it, imageResolver) }
    update(updates, pageContent.links)
  }

  private suspend fun update(updates: Flow<AutoEditContentData>, links: LinkData) {
    updates.flowOn(Dispatchers.Default).collectLatest {
      myContentFlow.value = prepareContent(it, links, uiState = null)
    }
  }

  private suspend fun prepareContent(
      content: AutoEditContentData,
      links: LinkData,
      uiState: UIState?
  ): AutoEditPageContent.Content {
    var downloadSourcesLink: String? = null
    if (content.targetElement != null) {
      val targetFile = readAction { content.targetElement.getTargetFile() }
      if (targetFile != null) {
        if (DocumentationDownloader.EP.extensionList.find { it.canHandle(project, targetFile) } !=
            null) {
          downloadSourcesLink = DocumentationDownloader.formatLink(targetFile)
        }
      }
    }
    return AutoEditPageContent.Content(content, links, uiState, downloadSourcesLink)
  }

  /** @return whether the page was restored */
  fun restorePage(snapshot: UISnapshot): Boolean {
    when (val pageContent = myContentFlow.value) {
      null -> {
        // This can happen in the following scenario:
        // 1. Show doc.
        // 2. Click a link, a new page will open and start loading.
        // 3. Invoke the Back action during "Fetching..." message.
        //    At this point the request from link is cancelled, but stored in the forward stack of
        // the history.
        // 4. Invoke the Forward action.
        //    The page is taken from forward stack, but it was never loaded.
        //    Here we reload that cancelled request again
        return false
      }
      is AutoEditPageContent.Empty -> {
        // nothing to do here
        return true
      }
      is AutoEditPageContent.Content -> {
        // Consider the following steps:
        // 1. Open doc with anchor.
        // 2. Select some link with Tab.
        // 3. Activate the link, a new page will open.
        // 4. Go back, the previous page is restored, its last computed UI state is
        // UIState.ScrollToAnchor.
        // Expected: scroll state and selected link from 2 are restored.
        // Without the following line: the doc scrolls to the anchor, computed in 1.
        // Regardless of last computed UIState, we always want to restore the snapshot.
        myContentFlow.value = pageContent.copy(uiState = UIState.RestoreFromSnapshot(snapshot))
        return true
      }
    }
  }

  private fun PsiElement.getTargetFile(): VirtualFile? =
      containingFile?.run { virtualFile ?: originalFile.virtualFile }

  suspend fun computeDocumentation(targetPointer: Pointer<out DocumentationTarget>): AutoEditData? {
    return withContext(Dispatchers.Default) {
      val documentationResult: DocumentationResult? = readAction {
        targetPointer.dereference()?.computeDocumentation()
      }
      @Suppress("REDUNDANT_ELSE_IN_WHEN")
      when (documentationResult) {
        is AutoEditData -> documentationResult
        is AsyncDocumentation -> documentationResult.supplier.invoke() as AutoEditData?
        null -> null
        else -> error("Unexpected result: $documentationResult") // this fixes Kotlin incremental
      // compilation
      }
    }
  }

  fun createExpandableDefinition(contentData: AutoEditContentData): ExpandableDefinition? {
    if (contentData.definitionDetails == null) return null
    val doc = contentData.html
    val end = doc.indexOf(DEFINITION_END)
    if (end == -1) return null
    DocumentationUsageCollector.EXPANDABLE_DEFINITION_SHOWN.log()
    return ExpandableDefinition(
        doc.substring(0, end), doc.substring(end), contentData.definitionDetails)
  }
}

sealed class AutoEditPageContent {

  object Empty : AutoEditPageContent()

  data class Content(
      val content: AutoEditContentData,
      val links: LinkData,
      val uiState: UIState?,
      val downloadSourcesLink: String?
  ) : AutoEditPageContent()
}
