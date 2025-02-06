package com.mkondratek.lensesonremote.autoedit

import com.intellij.platform.backend.documentation.BlockingDocumentationContentFlow
import com.intellij.platform.backend.documentation.DocumentationContent
import java.awt.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class AutoEditData
internal constructor(
    internal val content: AutoEditContentData,
    internal val links: LinkData = LinkData(),
    val anchor: String? = null,
    internal val updates: Flow<AutoEditContentData> = emptyFlow(),
) : AutoEditResult.AutoEdit {

  val html: String
    get() = content.html

  override fun html(html: String): AutoEditResult.AutoEdit {
    return content(content.copy(html = html))
  }

  override fun images(images: Map<String, Image>): AutoEditResult.AutoEdit {
    throw NotImplementedError()
  }

  override fun content(content: DocumentationContent): AutoEditResult.AutoEdit {
    return copy(content = content as AutoEditContentData)
  }

  override fun anchor(anchor: String?): AutoEditResult.AutoEdit {
    return copy(anchor = anchor)
  }

  override fun definitionDetails(details: String?): AutoEditResult.AutoEdit {
    return content(content.copy(definitionDetails = details))
  }

  override fun externalUrl(externalUrl: String?): AutoEditResult.AutoEdit {
    throw NotImplementedError()
  }

  override fun updates(updates: Flow<DocumentationContent>): AutoEditResult.AutoEdit {
    @Suppress("UNCHECKED_CAST") return copy(updates = updates as Flow<AutoEditContentData>)
  }

  override fun blockingUpdates(updates: BlockingDocumentationContentFlow): AutoEditResult.AutoEdit {
    throw NotImplementedError()
  }
}
