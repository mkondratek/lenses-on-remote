package com.mkondratek.lensesonremote.protocol_extensions

import com.intellij.openapi.editor.Document
import com.mkondratek.lensesonremote.protocol.Range

typealias RangeOffset = Pair<Int, Int>

/**
 * Converts the range represented by this [Range] object to a pair of offsets within the given
 * [Document].
 *
 * If the start or end position of the range is outside the document, the corresponding offset will
 * be set to 0 or the document's text length, respectively.
 *
 * @param document The [Document] to use for converting the range to offsets.
 * @return A [RangeOffset] pair containing the start and end offsets of the range within the
 *   document.
 */
fun Range.toOffsetRange(document: Document): RangeOffset {
  val startOffset = if (start.isOutsideOfDocument(document)) 0 else start.toOffsetOrZero(document)
  val endOffset =
      if (end.isOutsideOfDocument(document)) document.textLength else end.toOffsetOrZero(document)

  return RangeOffset(startOffset, endOffset)
}
