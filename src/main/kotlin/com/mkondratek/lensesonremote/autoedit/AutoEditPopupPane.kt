package com.mkondratek.lensesonremote.autoedit

import com.intellij.openapi.util.Key
import com.intellij.ui.WidthBasedLayout
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal val FORCED_WIDTH: Key<Int> = Key.create("WidthBasedLayout.width")

class AutoEditPopupPane(
  private val variableHeightComponent: JComponent,
) : JPanel(BorderLayout()), WidthBasedLayout {

  override fun getPreferredWidth(): Int = preferredSize.width

  override fun getPreferredHeight(width: Int): Int {
    UIUtil.putClientProperty(variableHeightComponent, FORCED_WIDTH, width)
    try {
      return preferredSize.height
    }
    finally {
      UIUtil.putClientProperty(variableHeightComponent, FORCED_WIDTH, null)
    }
  }
}
