/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import scala.Predef.{String => ScalaString, _}

import parsley.Parsley
import parsley.errors.combinator.{amend, entrench, ErrorMethods}

abstract class String private[token] {
    def unicode: Parsley[ScalaString]
    def extendedAscii: Parsley[ScalaString]
    def ascii: Parsley[ScalaString]
}

private [text] object String {
    private def allCharsWithin(str: ScalaString, bound: Int) = str.codePoints().allMatch(_ <= bound)
    def isAscii(str: ScalaString): Boolean = allCharsWithin(str, Character.MaxAscii)
    def isExtendedAscii(str: ScalaString): Boolean = allCharsWithin(str, Character.MaxExtendedAscii)

    def ensureAscii(p: Parsley[ScalaString]): Parsley[ScalaString] = amend {
        entrench(p).guardAgainst {
            case str if !isAscii(str) => Seq("non-ascii characters in string literal, this is not allowed")
       }
    }

    def ensureExtendedAscii(p: Parsley[ScalaString]): Parsley[ScalaString] = amend {
        entrench(p).guardAgainst {
            case str if !isExtendedAscii(str) => Seq("non-extended-ascii characters in string literal, this is not allowed")
       }
    }
}
