package com.mkondratek.lensesonremote.autoedit

import com.intellij.platform.backend.documentation.DocumentationContent
import java.awt.Image

data class AutoEditData
internal constructor(
    internal val content: AutoEditContentData,
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
}
