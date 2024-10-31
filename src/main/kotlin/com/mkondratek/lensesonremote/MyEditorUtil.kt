package com.mkondratek.lensesonremote

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.withScheme
import com.mkondratek.lensesonremote.protocol.Range
import com.mkondratek.lensesonremote.protocol_extensions.toOffsetRange
import java.net.URI
import java.net.URISyntaxException
import kotlin.io.path.toPath

object MyEditorUtil {
  @JvmStatic
  fun getTextRange(document: Document, range: Range): TextRange {
    val (start, end) = range.toOffsetRange(document)
    return TextRange.create(start, end)
  }

  @JvmStatic
  fun getSelectedEditors(project: Project): Array<out Editor> {
    return FileEditorManager.getInstance(project).selectedTextEditorWithRemotes
  }

  fun findFileOrScratch(project: Project, uriString: String): VirtualFile? {
    try {
      val uri = URI.create(uriString)
      val fixedUri = if (uriString.startsWith("untitled")) uri.withScheme("file") else uri
      return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fixedUri.toPath())
    } catch (e: URISyntaxException) {
      // Let's try scratch files
      val fileName = uriString.substringAfterLast(':').trimStart('/', '\\')
      return ScratchRootType.getInstance()
          .findFile(project, fileName, ScratchFileService.Option.existing_only)
    }
  }
}
