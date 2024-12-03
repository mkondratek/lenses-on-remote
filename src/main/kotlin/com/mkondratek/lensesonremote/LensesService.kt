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
import com.mkondratek.lensesonremote.providers.AcceptEditCodeVisionProvider

@Service(Service.Level.PROJECT)
class LensesService(val project: Project) {
  private var lensGroups = mutableMapOf<VirtualFile, ProtocolCodeLens>()

  fun updateLenses(uriString: String, line: Int) {
    val vf = MyEditorUtil.findFileOrScratch(project, uriString) ?: return
    lensGroups[vf] =
        ProtocolCodeLens(
            Range(Position(line, 0), Position(line, 0)),
            ProtocolCommand(
                TitleParams(" Accept"),
                "my_action.fixup.codelens.accept",
            ))

    if (project.isDisposed) return
    MyEditorUtil.getSelectedEditors(project).forEach { editor ->
      CodeVisionInitializer.getInstance(project)
          .getCodeVisionHost()
          .invalidateProvider(
              CodeVisionHost.LensInvalidateSignal(editor, listOf(AcceptEditCodeVisionProvider.ID)))
    }
  }

  fun getLenses(editor: Editor): ProtocolCodeLens? {
    val vf = editor.virtualFile
    return lensGroups[vf]
  }

  companion object {
    fun getInstance(project: Project): LensesService {
      return project.service<LensesService>()
    }
  }
}
