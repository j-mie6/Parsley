/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.descriptions

import parsley.token.{CharPred, Unicode}

/** This class describes how whitespace should be handled lexically.
  *
  * @param lineCommentStart how do single-line comments start? (empty for no single-line comments)
  * @param lineCommentAllowsEOF can a single-line comment be terminated by the end-of-file, or '''must''' it ends with a newline
  * @param multiLineCommentStart how do multi-line comments start? (empty for no multi-line comments)
  * @param multiLineCommentEnd how do multi-line comments end? (empty for no multi-line comments)
  * @param multiLineNestedComments can multi-line comments be nested within each other?
  * @param space what characters serve as whitespace within the language?
  * @param whitespaceIsContextDependent can the definition of whitespace change depending on context? (in Python, say, newlines are valid whitespace
  *                                     within parentheses, but are significant outside of them)
  * @since 4.0.0
  */
final case class SpaceDesc (lineCommentStart: String,
                            lineCommentAllowsEOF: Boolean,
                            multiLineCommentStart: String,
                            multiLineCommentEnd: String,
                            multiLineNestedComments: Boolean,
                            space: CharPred,
                            whitespaceIsContextDependent: Boolean) {
    require(multiLineCommentStart.nonEmpty == multiLineCommentEnd.nonEmpty, "multi-line comments must describe both start and end")
    require(multiLineCommentStart.isEmpty || lineCommentStart.isEmpty || !lineCommentStart.startsWith(multiLineCommentStart),
            "multi-line comments which are a valid prefix of a single-line comment are not allowed as this causes ambiguity in the parser")
    require(!multiLineCommentStart.contains('\n') && !multiLineCommentEnd.contains('\n') && !lineCommentStart.contains('\n'),
            "comment descriptions cannot include newlines")
    private [token] lazy val supportsComments = multiLineCommentStart.nonEmpty || lineCommentStart.nonEmpty
}

/** This object contains any default configurations describing whitespace.
  * @since 4.0.0
  */
object SpaceDesc {
    /** The plain definition of space, with no comments, no nested comments, and any unicode space character.
      *
      * {{{
      * multiLineCommentStart = ""
      * multiLineCommendEnd = ""
      * lineCommentStart = ""
      * lineCommentAllowsEOF = true
      * multiLineNestedComments = false
      * space = Unicode(Character.isWhitespace)
      * whitespaceIsContextDependent = false
      * }}}
      *
      * @since 4.0.0
      */
    val plain = SpaceDesc(
        multiLineCommentStart = "",
        multiLineCommentEnd = "",
        lineCommentStart = "",
        lineCommentAllowsEOF = true,
        multiLineNestedComments = false,
        space = Unicode(Character.isWhitespace(_)),
        whitespaceIsContextDependent = false
    )
}
