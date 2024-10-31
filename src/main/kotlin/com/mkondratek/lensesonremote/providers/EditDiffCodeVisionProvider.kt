package com.mkondratek.lensesonremote.providers

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering

class EditDiffCodeVisionProvider : EditCodeVisionProvider(EditDiffCodeVisionProvider) {
  companion object : EditCodeVisionProviderMetadata() {
    override val ordering: CodeVisionRelativeOrdering = showAfter(EditUndoCodeVisionProvider)
    override val command: String = "my_action.fixup.codelens.diff"
  }
}
