package com.mkondratek.lensesonremote.autoedit

import com.intellij.platform.backend.documentation.DocumentationContent
import java.awt.Image
import org.jetbrains.annotations.Nls

/**
 * Here is an image: <img height="16"
 * src="file:/Users/mkondratek/runIdeProjects/runIde-demo-zoo-private/src/main/resources/menu-cut.svg"
 * width="16"/><p>16×16, 32bpp</p><div class="content"><p><span class="grayed">VCS
 * Status:</span>&nbsp;<span color="73bd79">Added</span></p><p><span
 * class="grayed">Size:</span>&nbsp;2.08 kB</p><p><span class="grayed">Type:</span>&nbsp;Scalable
 * Vector Graphics (SVG)</p><p><span class="grayed">Modified:</span>&nbsp;05/10/2023,
 * 13:52</p><p><span class="grayed">Created:</span>&nbsp;05/10/2023, 13:52</p></div>
 *
 * end of a doc
 */
sealed interface AutoEditResult {

  sealed interface AutoEdit : AutoEditResult {

    fun html(html: @Nls String): AutoEdit

    /** @param images map from `url` of `<img src="url">` tag to an image */
    fun images(images: Map<String, Image>): AutoEdit

    /** @param content [html] with [images] */
    fun content(content: DocumentationContent): AutoEdit
  }

  companion object {

    /** @param html see [Documentation.html] */
    @JvmStatic
    fun documentation(html: @Nls String): AutoEdit {
      return documentation(DocumentationContent.content(html))
    }

    /** @param content see [Documentation.content] */
    @JvmStatic
    fun documentation(content: DocumentationContent): AutoEdit {
      return AutoEditData(content as AutoEditContentData)
    }
  }
}
