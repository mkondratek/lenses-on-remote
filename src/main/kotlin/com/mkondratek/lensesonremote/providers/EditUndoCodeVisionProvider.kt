package com.mkondratek.lensesonremote.providers

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.ui.JBColor

class EditUndoCodeVisionProvider : EditCodeVisionProvider(Metadata) {
  override val relativeOrderings = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingLast)
  override val textColor: JBColor = JBColor.RED

  object Metadata : EditCodeVisionProviderMetadata() {
    override val myActionId = "my_action.fixup.codelens.undo"
    override val id: String = deriveId(myActionId)
  }
}
