package com.mkondratek.lensesonremote.providers

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering

class EditRetryCodeVisionProvider : EditCodeVisionProvider(Metadata) {
  override val relativeOrderings = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingLast)

  object Metadata : EditCodeVisionProviderMetadata() {
    override val myActionId = "my_action.fixup.codelens.retry"
    override val id: String = deriveId(myActionId)
  }
}
