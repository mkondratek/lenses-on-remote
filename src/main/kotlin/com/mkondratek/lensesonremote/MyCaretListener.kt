package com.mkondratek.lensesonremote

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.mkondratek.lensesonremote.autoedit.AutoEditManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyCaretListener : CaretListener {

  override fun caretPositionChanged(e: CaretEvent) {
    val offset = e.caret!!.offset
    val lineNumber = e.editor.document.getLineNumber(offset)
    val project = e.editor.project ?: return

    (project as ComponentManagerEx).getCoroutineScope().launch {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        AutoEditManager.getInstance(project).actionPerformed(DataContext.EMPTY_CONTEXT)
      }
    }
  }
}
