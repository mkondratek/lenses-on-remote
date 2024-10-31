package com.mkondratek.lensesonremote.providers

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.ui.JBColor

class EditUndoCodeVisionProvider : EditCodeVisionProvider(EditUndoCodeVisionProvider) {
  companion object : EditCodeVisionProviderMetadata() {
    override val ordering: CodeVisionRelativeOrdering = showAfter(EditAcceptCodeVisionProvider)
    override val command: String = "my_action.fixup.codelens.undo"
    override val textColor: JBColor = JBColor.RED
  }
}
