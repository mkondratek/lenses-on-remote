package com.mkondratek.lensesonremote.providers

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering

class EditRetryCodeVisionProvider : EditCodeVisionProvider(EditRetryCodeVisionProvider) {
  companion object : EditCodeVisionProviderMetadata() {
    override val ordering: CodeVisionRelativeOrdering =
        CodeVisionRelativeOrdering.CodeVisionRelativeOrderingLast
    override val command: String = "my_action.fixup.codelens.retry"
  }
}
