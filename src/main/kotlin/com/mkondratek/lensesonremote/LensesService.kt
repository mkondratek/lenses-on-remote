package com.mkondratek.lensesonremote

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionInitializer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.mkondratek.lensesonremote.protocol.ProtocolCodeLens
import com.mkondratek.lensesonremote.protocol.ProtocolCommand
import com.mkondratek.lensesonremote.protocol.Range
import com.mkondratek.lensesonremote.protocol.TitleParams
import com.mkondratek.lensesonremote.protocol_extensions.Position
import com.mkondratek.lensesonremote.providers.EditCodeVisionProvider

@Service(Service.Level.PROJECT)
class LensesService(val project: Project) {
  @Volatile private var lensGroups = mutableMapOf<VirtualFile, List<ProtocolCodeLens>>()

  fun updateLenses(uriString: String, codeLens: List<ProtocolCodeLens>) {
    val vf = MyEditorUtil.findFileOrScratch(project, uriString) ?: return
    synchronized(this) { lensGroups[vf] = codeLens }

    if (project.isDisposed) return
    MyEditorUtil.getSelectedEditors(project).forEach { editor ->
      CodeVisionInitializer.getInstance(project)
          .getCodeVisionHost()
          .invalidateProvider(
              CodeVisionHost.LensInvalidateSignal(
                  editor, EditCodeVisionProvider.allEditProviders().map { it.id }))
    }
  }

  fun getLenses(editor: Editor): List<ProtocolCodeLens> {
    val vf = editor.virtualFile

    synchronized(this) {
      return lensGroups[vf] ?: emptyList()
    }
  }

  companion object {
    fun getInstance(project: Project): LensesService {
      return project.service<LensesService>()
    }

    fun mock_codeLenses_display(project: Project, line: Int, url: String) {
      val codeLenses = aListOfLensesForLine(line)

      getInstance(project).updateLenses(url, codeLenses)
    }

    private fun aListOfLensesForLine(line: Int): List<ProtocolCodeLens> {
      return arrayListOf(
          ProtocolCodeLens(
              Range(Position(line, 0), Position(line, 0)),
              ProtocolCommand(
                  TitleParams(" Accept"),
                  "my_action.fixup.codelens.accept",
              )),
          ProtocolCodeLens(
              Range(Position(line, 0), Position(line, 0)),
              ProtocolCommand(
                  TitleParams("Edit & Retry"),
                  "my_action.fixup.codelens.retry",
              )),
          ProtocolCodeLens(
              Range(Position(line, 0), Position(line, 0)),
              ProtocolCommand(
                  TitleParams("Reject"),
                  "my_action.fixup.codelens.undo",
              )),
          ProtocolCodeLens(
              Range(Position(line, 0), Position(line, 0)),
              ProtocolCommand(
                  TitleParams("Show Diff"),
                  "my_action.fixup.codelens.diff",
              )))
    }
  }
}
