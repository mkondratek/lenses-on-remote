package com.mkondratek.lensesonremote.autoedit

import com.intellij.lang.documentation.DocumentationImageResolver
import com.intellij.platform.backend.documentation.DocumentationContent
import com.intellij.psi.PsiElement

data class LinkData(
  val externalUrl: String? = null,
  val linkUrls: List<String> = emptyList(),
)

 data class AutoEditContentData(
  val html: String,
  val imageResolver: DocumentationImageResolver?,
  val definitionDetails: String? = null,
  val targetElement: PsiElement? = null
) : DocumentationContent
