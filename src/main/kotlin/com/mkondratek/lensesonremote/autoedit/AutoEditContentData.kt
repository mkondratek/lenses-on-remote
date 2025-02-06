package com.mkondratek.lensesonremote.autoedit

import com.intellij.lang.documentation.DocumentationImageResolver
import com.intellij.platform.backend.documentation.DocumentationContent

data class AutoEditContentData(
    val html: String,
    val imageResolver: DocumentationImageResolver? = null
) : DocumentationContent
