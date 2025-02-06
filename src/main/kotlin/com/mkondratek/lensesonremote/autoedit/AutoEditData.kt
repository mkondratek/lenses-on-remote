package com.mkondratek.lensesonremote.autoedit

import com.intellij.platform.backend.documentation.BlockingDocumentationContentFlow
import com.intellij.platform.backend.documentation.DocumentationContent
import com.intellij.platform.backend.documentation.DocumentationResult.Documentation
import java.awt.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class AutoEditData
internal constructor(
    internal val content: AutoEditContentData,
    internal val links: LinkData = LinkData(),
    val anchor: String? = null,
    internal val updates: Flow<AutoEditContentData> = emptyFlow(),
) : Documentation {

  val html: String
    get() = content.html

  override fun html(html: String): Documentation {
    return content(content.copy(html = html))
  }

  override fun images(images: Map<String, Image>): Documentation {
    throw NotImplementedError()
  }

  override fun content(content: DocumentationContent): Documentation {
    return copy(content = content as AutoEditContentData)
  }

  override fun anchor(anchor: String?): Documentation {
    return copy(anchor = anchor)
  }

  override fun definitionDetails(details: String?): Documentation {
    return content(content.copy(definitionDetails = details))
  }

  override fun externalUrl(externalUrl: String?): Documentation {
    throw NotImplementedError()
  }

  override fun updates(updates: Flow<DocumentationContent>): Documentation {
    @Suppress("UNCHECKED_CAST") return copy(updates = updates as Flow<AutoEditContentData>)
  }

  override fun blockingUpdates(updates: BlockingDocumentationContentFlow): Documentation {
    throw NotImplementedError()
  }
}
