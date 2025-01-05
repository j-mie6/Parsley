/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.util

import parsley.token.Lexer

import parsley.internal.deepembedding.frontend.LazyParsley

// Empty collector for platforms that don't support reflection.
// $COVERAGE-OFF$
private [util] class XDummyCollector extends CollectorImpl {
    override def collectNames(obj: Any): Map[LazyParsley[_], String] = Map.empty
    override def collectLexer(lexer: Lexer): Map[LazyParsley[_], String] = Map.empty
    override val supported: Boolean = false
}
// $COVERAGE-ON$
