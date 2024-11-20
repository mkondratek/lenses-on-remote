package com.mkondratek.lensesonremote.providers

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.ui.JBColor

class EditAcceptCodeVisionProvider : EditCodeVisionProvider(Metadata) {
  override val relativeOrderings =
      listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst)
  override val textColor: JBColor = JBColor.GREEN

  object Metadata : EditCodeVisionProviderMetadata() {
    override val myActionId = "my_action.fixup.codelens.accept"
    override val id: String = deriveId(myActionId)
  }
}
