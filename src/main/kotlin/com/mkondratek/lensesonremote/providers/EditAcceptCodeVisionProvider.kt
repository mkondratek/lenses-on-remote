package com.mkondratek.lensesonremote.providers

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.ui.JBColor

class EditAcceptCodeVisionProvider : EditCodeVisionProvider(EditAcceptCodeVisionProvider) {
  companion object : EditCodeVisionProviderMetadata() {
    override val ordering: CodeVisionRelativeOrdering =
        CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst
    override val command: String = "my_action.fixup.codelens.accept"
    override val textColor: JBColor = JBColor.GREEN
  }
}
