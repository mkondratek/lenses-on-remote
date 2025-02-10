package com.mkondratek.lensesonremote.autoedit

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class AutoEditPage(val requests: List<AutoEditRequest>, val project: Project) {

  private val myContentFlow = MutableStateFlow<AutoEditPageContent?>(null)
  val request = requests.first()
  val contentFlow: SharedFlow<AutoEditPageContent?> = myContentFlow.asSharedFlow()

  /** @return `true` if some content was loaded, `false` if content is empty */
  suspend fun waitForContent(): Boolean {
    return myContentFlow.filterNotNull().first() is AutoEditPageContent.Content
  }

  fun loadPage() {
    val data =
        try {
          computeAutoEdit()
        } catch (e: IndexNotReadyException) {
          null // normal situation, nothing to do
        }
    if (data == null) {
      myContentFlow.value = AutoEditPageContent.Empty
      return
    }
    myContentFlow.value = prepareContent(data.content.copy(html = data.content.html))
  }

  private fun prepareContent(
      content: AutoEditContentData,
  ): AutoEditPageContent.Content {
    return AutoEditPageContent.Content(content)
  }

  fun computeAutoEdit(): AutoEditData? {
    //    return AutoEditData(
    //      content = AutoEditContentData("<html><body>Hello world from Auto Edit!<body></html>"),
    //    )
    return AutoEditData(
        content =
            AutoEditContentData(
                html =
                    "<!DOCTYPE html>\n" +
                        "<html lang=\"en\">\n" +
                        "<head>\n" +
                        "  <meta charset=\"UTF-8\">\n" +
                        "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                        "</head>\n" +
                        "<body>\n" +
                        "<p>Hello from Auto Edit! Hello from Auto Edit! Hello from Auto Edit! Hello from Auto Edit! Hello from Auto Edit! Hello from Auto Edit!</p>\n" +
                        "<img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg==\" alt=\"Red dot\" />\n" +
                        "</body>\n" +
                        "</html>"))
  }
}

sealed class AutoEditPageContent {

  object Empty : AutoEditPageContent()

  data class Content(val content: AutoEditContentData) : AutoEditPageContent()
}
