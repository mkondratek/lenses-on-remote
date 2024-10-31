package com.mkondratek.lensesonremote.protocol_extensions

// because line / column should probably be a long
import com.intellij.openapi.editor.Document
import com.mkondratek.lensesonremote.protocol.Position
import kotlin.math.max
import kotlin.math.min

fun Position(line: Int, character: Int): Position {
  return Position(line.toLong(), character.toLong())
}

fun Position.isOutsideOfDocument(document: Document): Boolean {
  return line < 0 || line > document.lineCount
}

fun Position.getRealLine(document: Document): Int {
  return max(0, min(max(0, document.lineCount - 1), line.toInt()))
}

fun Position.getRealColumn(document: Document): Int {
  val realLine = getRealLine(document)
  val lineLength = document.getLineEndOffset(realLine) - document.getLineStartOffset(realLine)
  return min(lineLength, character.toInt())
}

fun Position.toOffsetOrZero(document: Document): Int {
  val lineStartOffset = document.getLineStartOffset(getRealLine(document))
  return lineStartOffset + getRealColumn(document)
}
