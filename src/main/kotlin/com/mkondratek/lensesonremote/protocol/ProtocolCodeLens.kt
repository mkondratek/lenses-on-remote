package com.mkondratek.lensesonremote.protocol

data class ProtocolCodeLens(
    val range: Range,
    val command: ProtocolCommand? = null,
)
