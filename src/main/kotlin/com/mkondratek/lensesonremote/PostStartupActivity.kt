package com.mkondratek.lensesonremote

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer

class PostStartupActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    val multicaster = EditorFactory.getInstance().eventMulticaster as EditorEventMulticasterEx
    val disposable = Disposer.newDisposable()
    multicaster.addCaretListener(MyCaretListener(), disposable)
  }
}
