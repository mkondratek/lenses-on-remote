package com.mkondratek.lensesonremote

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager

class MyCaretListener : CaretListener {
  override fun caretPositionChanged(e: CaretEvent) {
    val offset = e.caret!!.offset
    val lineNumber = e.editor.document.getLineNumber(offset)
    val url = FileDocumentManager.getInstance().getFile(e.editor.document)?.url ?: return
    val project = e.editor.project ?: return
    LensesService.mock_codeLenses_display(project, lineNumber, url)
  }
}
