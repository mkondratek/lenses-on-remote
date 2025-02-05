package com.mkondratek.lensesonremote.autoedit

import com.intellij.lang.documentation.ide.impl.DocumentationHistory
import com.intellij.util.containers.Stack
import com.intellij.util.ui.EDT

class AutoEditBrowserHistory<T>(
  private val snapshot: () -> T,
  private val restore: (T) -> Unit,
) : DocumentationHistory {

  private val backStack = Stack<T>()
  private val forwardStack = Stack<T>()

  override fun canBackward(): Boolean {
    EDT.assertIsEdt()
    return !backStack.isEmpty()
  }

  override fun backward() {
    EDT.assertIsEdt()
    forwardStack.push(snapshot())
    restore(backStack.pop())
  }

  override fun canForward(): Boolean {
    EDT.assertIsEdt()
    return !forwardStack.isEmpty()
  }

  override fun forward() {
    EDT.assertIsEdt()
    backStack.push(snapshot())
    restore(forwardStack.pop())
  }

  fun clear() {
    EDT.assertIsEdt()
    backStack.clear()
    forwardStack.clear()
  }

  fun nextPage() {
    EDT.assertIsEdt()
    backStack.push(snapshot())
    forwardStack.clear()
  }
}

