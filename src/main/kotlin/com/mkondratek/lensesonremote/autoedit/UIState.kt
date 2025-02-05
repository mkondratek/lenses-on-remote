package com.mkondratek.lensesonremote.autoedit

sealed class UIState {

  object Reset : UIState()

  class ScrollToAnchor(val anchor: String) : UIState()

  class RestoreFromSnapshot(val snapshot: UISnapshot) : UIState()
}
