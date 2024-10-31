package com.mkondratek.lensesonremote.providers

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.CodeVisionState.Companion.READY_EMPTY
import com.intellij.codeInsight.codeVision.ui.model.ClickableRichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.mkondratek.lensesonremote.LensesService
import com.mkondratek.lensesonremote.MyEditorUtil
import com.mkondratek.lensesonremote.protocol.ProtocolCommand
import java.awt.event.MouseEvent

abstract class EditCodeVisionProviderMetadata {
  abstract val ordering: CodeVisionRelativeOrdering
  abstract val command: String
  open val textColor: JBColor = JBColor.BLACK

  val id: String
    get() = "EditCodeVisionProvider-${command}"

  fun showAfter(providerCompanion: EditCodeVisionProviderMetadata): CodeVisionRelativeOrdering {
    return CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter(providerCompanion.id)
  }
}

abstract class EditCodeVisionProvider(private val metadata: EditCodeVisionProviderMetadata) :
    CodeVisionProvider<Unit>, DumbAware {
  override val id: String = metadata.id
  override val groupId: String = "EditCodeVisionProvider"
  override val name: String = "My Edit Lenses"
  override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top
  override val relativeOrderings: List<CodeVisionRelativeOrdering> = listOf(metadata.ordering)

  override fun precomputeOnUiThread(editor: Editor) {}

  private fun getActionRichText(cmd: ProtocolCommand): RichText {
    return RichText().also {
      it.append(
          cmd.title.text, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, metadata.textColor))
      it.append("   |", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY))
    }
  }

  override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
    return runReadAction {
      val project = editor.project ?: return@runReadAction READY_EMPTY

      val codeVisionEntries =
          LensesService.getInstance(project).getLenses(editor).mapNotNull { codeLens ->
            val cmd = codeLens.command
            if (cmd == null || cmd.command != metadata.command) null
            else {
              val richText = getActionRichText(cmd)
              val textRange = MyEditorUtil.getTextRange(editor.document, codeLens.range)
              val onClick = { _: MouseEvent?, _: Editor -> println("Trigger action: $cmd") }
              val entry =
                  ClickableRichTextCodeVisionEntry(
                      id, richText, onClick, null, "", richText.text, emptyList())
              textRange to entry
            }
          }

      return@runReadAction CodeVisionState.Ready(codeVisionEntries)
    }
  }

  companion object {
    fun allEditProviders(): List<EditCodeVisionProviderMetadata> {
      return listOf(
          EditAcceptCodeVisionProvider,
          EditDiffCodeVisionProvider,
          EditRetryCodeVisionProvider,
          EditUndoCodeVisionProvider)
    }
  }
}
