package com.mkondratek.lensesonremote.autoedit

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation

class AutoEditRequest (
  val targetPointer: Pointer<out DocumentationTarget>,
  val presentation: TargetPresentation,
)
