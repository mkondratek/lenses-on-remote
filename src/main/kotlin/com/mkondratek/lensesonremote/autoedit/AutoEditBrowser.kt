package com.mkondratek.lensesonremote.autoedit

import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
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

class AutoEditBrowser
private constructor(
    private val project: Project,
    initialPage: AutoEditPage,
) : DocumentationBrowserFacade, Disposable {

  var ui: AutoEditUI by lateinitVal()
  var closeTrigger: (() -> Unit)? = null

  private sealed class BrowserRequest {

    class Load(val request: AutoEditRequest) : BrowserRequest()

    object Reload : BrowserRequest()
  }

  private val myRequestFlow: MutableSharedFlow<BrowserRequest> =
      MutableSharedFlow(
          replay = 1,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

  private val cs = CoroutineScope(EmptyCoroutineContext)

  init {
    cs.launch(CoroutineName("DocumentationBrowser requests")) {
      myRequestFlow.collectLatest { handleBrowserRequest(it) }
    }
  }

  override fun dispose() {
    cs.cancel("DocumentationBrowser disposal")
  }

  internal fun closeTrigger(close: () -> Unit) {
    closeTrigger = close
  }

  fun resetBrowser(request: AutoEditRequest) {
    load(request)
  }

  private fun load(request: AutoEditRequest) {
    check(myRequestFlow.tryEmit(BrowserRequest.Load(request)))
  }

  override fun reload() {
    check(myRequestFlow.tryEmit(BrowserRequest.Reload))
  }

  private val myPageFlow = MutableStateFlow(initialPage)

  val pageFlow: SharedFlow<AutoEditPage> = myPageFlow.asSharedFlow()

  internal var page: AutoEditPage
    get() = myPageFlow.value
    set(value) {
      myPageFlow.value = value
    }

  override val targetPointer: Pointer<out DocumentationTarget>
    get() = throw NotImplementedError()

  private suspend fun handleBrowserRequest(request: BrowserRequest): Unit =
      when (request) {
        is BrowserRequest.Load -> handleLoadRequest(request.request)
        is BrowserRequest.Reload -> page.loadPage()
      }

  private suspend fun handleLoadRequest(request: AutoEditRequest) {
    val page =
        withContext(Dispatchers.EDT) {
          AutoEditPage(listOf(request), project).also { this@AutoEditBrowser.page = it }
        }
    page.loadPage()
  }

  companion object {

    fun createBrowser(project: Project, requests: List<AutoEditRequest>): AutoEditBrowser {
      val browser = AutoEditBrowser(project, AutoEditPage(requests, project))
      browser.reload() // init loading
      return browser
    }

    /** @return `true` if a loaded page has some content, or `false` if a loaded page is empty */
    suspend fun AutoEditBrowser.waitForContent(): Boolean {
      return pageFlow.first().waitForContent()
    }
  }
}
