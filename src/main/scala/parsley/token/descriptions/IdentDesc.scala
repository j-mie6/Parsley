/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.descriptions

import parsley.token.{Impl, NotRequired}

private [parsley] // TODO: remove
case class IdentDesc (identStart: Impl,
                      identLetter: Impl,
                      keywords: Set[String],
                      caseSensitive: Boolean) {
    private [parsley] def isReservedName(name: String): Boolean =
        theReservedNames.contains(if (caseSensitive) name else name.toLowerCase)
    private lazy val theReservedNames =  if (caseSensitive) keywords else keywords.map(_.toLowerCase)
}

private [parsley] // TODO: remove
object IdentDesc {
    val plain = IdentDesc(NotRequired, NotRequired, Set.empty, true)
}
