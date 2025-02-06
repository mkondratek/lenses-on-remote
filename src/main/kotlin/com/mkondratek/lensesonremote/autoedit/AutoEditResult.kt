package com.mkondratek.lensesonremote.autoedit

import com.intellij.platform.backend.documentation.DocumentationContent
import java.awt.Image
import org.jetbrains.annotations.Nls

/** This is a union type: `Documentation | suspend () -> Documentation?`. */
sealed interface AutoEditResult {

  sealed interface AutoEdit : AutoEditResult {

    fun html(html: @Nls String): AutoEdit

    /** @param images map from `url` of `<img src="url">` tag to an image */
    fun images(images: Map<String, Image>): AutoEdit

    /** @param content [html] with [images] */
    fun content(content: DocumentationContent): AutoEdit

    /**
     * The scrolling is only executed on the initial showing, the anchor does not have an effect on
     * [updates].
     *
     * @param anchor element `id` or link `name` in the [html] to scroll to, or `null` to scroll to
     *   the top
     */
    fun anchor(anchor: String?): AutoEdit

    /** Expandable section under element definition */
    fun definitionDetails(details: String?): AutoEdit
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
