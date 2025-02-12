package com.mkondratek.lensesonremote.autoedit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

class AutoEditBrowser
private constructor(
    initialPage: AutoEditPage,
) : Disposable {

  var closeTrigger: (() -> Unit)? = null

  private val cs = CoroutineScope(EmptyCoroutineContext)

  private val myPageFlow = MutableStateFlow(initialPage)

  init {
    page.loadPage()
  }

  override fun dispose() {
    cs.cancel("AutoEditBrowser disposal")
  }

  internal fun closeTrigger(close: () -> Unit) {
    closeTrigger = close
  }

  val pageFlow: SharedFlow<AutoEditPage> = myPageFlow.asSharedFlow()

  internal var page: AutoEditPage
    get() = myPageFlow.value
    set(value) {
      myPageFlow.value = value
    }

  companion object {

    fun createBrowser(project: Project): AutoEditBrowser {
      val browser = AutoEditBrowser(AutoEditPage(project))
      browser.page.loadPage()
      return browser
    }

    /** @return `true` if a loaded page has some content, or `false` if a loaded page is empty */
    suspend fun AutoEditBrowser.waitForContent(): Boolean {
      return pageFlow.first().waitForContent()
    }
  }
}
