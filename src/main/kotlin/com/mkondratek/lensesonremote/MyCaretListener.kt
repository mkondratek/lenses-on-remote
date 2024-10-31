package com.mkondratek.lensesonremote

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

class MyCaretListener(val project: Project) : CaretListener {
  override fun caretPositionChanged(e: CaretEvent) {
    val offset = e.caret!!.offset
    val lineNumber = e.editor.document.getLineNumber(offset)
    val url = FileDocumentManager.getInstance().getFile(e.editor.document)!!.url
    LensesService.mock_codeLenses_display(project, lineNumber, url)
  }
}
