package com.mkondratek.lensesonremote.autoedit

import com.intellij.codeInsight.documentation.actions.DocumentationDownloader
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.lang.documentation.ide.impl.DocumentationHistory
import com.intellij.lang.documentation.ide.impl.DocumentationLinkProtocol
import com.intellij.lang.documentation.ide.impl.DocumentationUsageCollector
import com.intellij.lang.documentation.ide.impl.DocumentationUsageCollector.logDownloadFinished
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.ide.documentation.DocumentationBrowserFacade
import com.intellij.util.lateinitVal
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias UISnapshot = () -> Unit


class AutoEditBrowser private constructor(
  private val project: Project,
  initialPage: AutoEditPage,
) : DocumentationBrowserFacade, Disposable {

  var ui: AutoEditUI by lateinitVal()
  var closeTrigger: (() -> Unit)? = null

  private sealed class BrowserRequest {

    class Load(val request: AutoEditRequest, val reset: Boolean) :
      BrowserRequest()

    object Reload : BrowserRequest()

    class Link(val url: String) : BrowserRequest()

    class Restore(val snapshot: HistorySnapshot) : BrowserRequest()
  }

  private val myRequestFlow: MutableSharedFlow<BrowserRequest> =
    MutableSharedFlow(
      replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  private val cs = CoroutineScope(EmptyCoroutineContext)

  init {
    cs.launch(CoroutineName("DocumentationBrowser requests")) {
      myRequestFlow.collectLatest {
        handleBrowserRequest(it)
      }
    }
  }

  override fun dispose() {
    cs.cancel("DocumentationBrowser disposal")
    myHistory.clear()
  }

  internal fun closeTrigger(close: () -> Unit) {
    closeTrigger = close
  }

  internal fun clearCloseTrigger() {
    closeTrigger = null
  }

  fun resetBrowser(request: AutoEditRequest) {
    load(request, reset = true)
  }

  private fun load(request: AutoEditRequest, reset: Boolean) {
    check(myRequestFlow.tryEmit(BrowserRequest.Load(request, reset)))
  }

  override fun reload() {
    check(myRequestFlow.tryEmit(BrowserRequest.Reload))
  }

  fun handleLink(url: String) {
    check(myRequestFlow.tryEmit(BrowserRequest.Link(url)))
  }

  private val myPageFlow = MutableStateFlow(initialPage)

  val pageFlow: SharedFlow<AutoEditPage> = myPageFlow.asSharedFlow()

  internal var page: AutoEditPage
    get() = myPageFlow.value
    set(value) {
      myPageFlow.value = value
    }

  override val targetPointer: Pointer<out DocumentationTarget> get() = page.request.targetPointer

  private suspend fun handleBrowserRequest(request: BrowserRequest): Unit =
    when (request) {
      is BrowserRequest.Load -> handleLoadRequest(
        request.request,
        request.reset
      )

      is BrowserRequest.Reload -> page.loadPage()
      is BrowserRequest.Link -> handleLinkRequest(request.url)
      is BrowserRequest.Restore -> handleRestoreRequest(request.snapshot)
    }

  private suspend fun handleLoadRequest(
    request: AutoEditRequest,
    reset: Boolean
  ) {
    val page = withContext(Dispatchers.EDT) {
      if (reset) {
        myHistory.clear()
      } else {
        myHistory.nextPage()
      }
      AutoEditPage(listOf(request), project).also {
        this@AutoEditBrowser.page = it
      }
    }
    page.loadPage()
  }

  private suspend fun handleLinkRequest(url: String) {
    throw NotImplementedError()
//    if (url.startsWith(DocumentationDownloader.HREF_PREFIX)) {
//      handleDownloadSourcesRequest(url)
//      return
//    }
//    val targetPointer = this.targetPointer
//    val internalResult = try {
//      com.intellij.lang.documentation.ide.impl.handleLink(
//        project,
//        targetPointer,
//        url,
//        page
//      )
//    }
//    catch (e: IndexNotReadyException) {
//      return // normal situation, nothing to do
//    }
//    when (internalResult) {
//      is OrderEntry -> withContext(Dispatchers.EDT) {
//        if (internalResult.isValid) {
//          ProjectSettingsService.getInstance(project).openLibraryOrSdkSettings(internalResult)
//        }
//      }
//      InternalLinkResult.InvalidTarget -> {
//        // TODO ? target was invalidated
//      }
//      InternalLinkResult.CannotResolve -> withContext(Dispatchers.EDT) {
//        logLinkClicked(DocumentationLinkProtocol.of(url))
//        @Suppress("ControlFlowWithEmptyBody")
//        if (!openUrl(project, targetPointer, url)) {
//          // TODO ? can't resolve link to target & nobody can open the link
//        }
//      }
//      is InternalLinkResult.Request -> {
//        logLinkClicked(DocumentationLinkProtocol.PSI_ELEMENT)
//        load(internalResult.request, reset = false)
//      }
//      is InternalLinkResult.Updater -> {
//        page.updatePage(internalResult.updater)
//      }
//    }
  }

  private suspend fun handleDownloadSourcesRequest(href: String) {
    val filePath = href.replaceFirst(DocumentationDownloader.HREF_PREFIX, "")
    val file = VirtualFileManager.getInstance().findFileByUrl(filePath)
    if (file != null) {
      val handler = DocumentationDownloader.EP.extensionList.find {
        it.canHandle(
          project,
          file
        )
      }
      if (handler != null) {
        blockingContext {
          val callback = handler.download(project, file)
          callback.doWhenProcessed {
            logDownloadFinished(project, handler::class.java, callback.isDone)
          }
        }
      }
      closeTrigger?.invoke()
    }
  }

  private suspend fun handleRestoreRequest(snapshot: HistorySnapshot) {
    val page = snapshot.page
    val restored = page.restorePage(snapshot.ui)
    this.page = page
    if (!restored) {
      page.loadPage()
    }
  }

  fun currentExternalUrl(): String? {
    return page.currentContent?.links?.externalUrl
  }

  val history: DocumentationHistory get() = myHistory

  private val myHistory = AutoEditBrowserHistory(::historySnapshot, ::restore)

  private class HistorySnapshot(
    val page: AutoEditPage,
    val ui: UISnapshot,
  )

  private fun historySnapshot(): HistorySnapshot {
    return HistorySnapshot(page, ui.uiSnapshot())
  }

  private fun restore(snapshot: HistorySnapshot) {
    check(myRequestFlow.tryEmit(BrowserRequest.Restore(snapshot)))
  }

  private fun logLinkClicked(protocol: DocumentationLinkProtocol) {
    DocumentationUsageCollector.DOCUMENTATION_LINK_CLICKED.log(
      protocol,
      LookupManager.getInstance(project).activeLookup != null
    )
  }

  companion object {

    fun createBrowser(
      project: Project,
      requests: List<AutoEditRequest>
    ): AutoEditBrowser {
      val browser = AutoEditBrowser(project, AutoEditPage(requests, project))
      browser.reload() // init loading
      return browser
    }

    /**
     * @return `true` if a loaded page has some content,
     * or `false` if a loaded page is empty
     */
    suspend fun AutoEditBrowser.waitForContent(): Boolean {
      return pageFlow.first().waitForContent()
    }
  }
}
