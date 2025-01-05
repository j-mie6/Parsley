/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.internal

import parsley.internal.deepembedding.frontend.LazyParsley
import parsley.internal.deepembedding.frontend.debug.TaggedWith

// An object with a single public apply method that allows the renaming of a
// debugged  parser's name in order to increase the clarity of the debugger's
// results. By default, this does nothing other than get the prettified names of
// a parser's class defined within.
// It also requires user interaction, as it needs to collect information from the
// classes or objects that define their parser.
private [parsley] object Renamer {
    // This will attempt to match parsers referentially.
    // This also assumes that all parsers are present (lazy or otherwise) before debugging.
    // Populating this map is only possible if the platform contains some form of implementation for
    // parsley.debugger.util.Collector.names, which attempts to collect the name-in-code for a given
    // reference to a parser.
    // Alternatively, users can use the `named` combinator from the debugger to weakly assign names
    // to their parsers, or directly invoke Collector.assignName to strongly assign names to parsers.
    // Just like parser reference collection for the debugger, we need a weak map so ephemeral renamed
    // parsers don't leak memory.
    lazy private val collected = new XWeakMap[LazyParsley[_], String]

    private def underlying(p: LazyParsley[_]): LazyParsley[_] = p match {
        case dbg: TaggedWith[_] => dbg.origin // this should never be the case?
        case _                => p
    }

    // This method attempts the renaming of a parser, even if at least partially.
    // This renames the parser if it is present, otherwise gives the default name found earlier.
    def nameOf(userAssignedName: Option[String], p: LazyParsley[_]): String = userAssignedName.getOrElse {
        val extracted = underlying(p)
        collected.getOrElse(extracted, extracted.debugName)
    }

    // Perform the first step of renaming, a partial rename where only the type name is exposed.
    @inline def internalName(p: LazyParsley[_]): String = underlying(p).debugName

    private [parsley] def addNames(names: Map[LazyParsley[_], String]): Unit = collected ++= names
    private [parsley] def addName(par: LazyParsley[_], name: String): Unit = collected.update(par, name)
}
