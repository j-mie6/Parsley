/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.util

import scala.annotation.nowarn
import scala.collection.mutable
import scala.reflect.runtime.{universe => ru}

import parsley.Parsley
import parsley.token.Lexer

import parsley.internal.deepembedding.frontend.LazyParsley

private [parsley] object XCollector extends CollectorImpl {
    // True, it works, but it is on the whims of reflection functioning as expected.
    override val supported: Boolean = true

    // There should not be too many differences in the public API between 2.12 and 2.13's reflection
    // packages. However, results may vary. Scala 3 however, is a wild-west of compatibility.
    // XXX: @nowarn is required as the Runtime Universe's MethodSymbol type is erased at runtime,
    //      though it should be a safe type comparison if the version of scala-reflect remains
    //      constant between compile-time and runtime.
    // XXX: This works for Scala 2, but it is not guaranteed to work at all for Scala 3.
    def collectNames(obj: Any): Map[LazyParsley[_], String] = {
        val accumulator: mutable.HashMap[LazyParsley[_], String] = new mutable.HashMap()

        val mirror = scala.reflect.runtime.currentMirror
        val objRefl = mirror.reflect(obj)
        val objClass = mirror.classSymbol(obj.getClass)

        // XXX: Does not work with def-defined custom parsers due to new references of
        //      parsers being created with each invocation.
        val getters = objClass.toType.members.collect {
            case mem: ru.MethodSymbol @nowarn if mem.isGetter => mem
        }

        for (getter <- getters) {
            val getterRefl = objRefl.reflectMethod(getter)
            val parser = getterRefl()

            parser match {
                case _: LazyParsley[_] | _: Parsley[_] =>
                    val name = getter.name.toString
                    accumulator.put(tryExtract(parser), name)
                case _ => // Don't actually do anything.
            }
        }

        accumulator.toMap
    }

    // XXX: See collectNames' hack (XXX) message for more information.
    // Collect all names from the exposed objects inside of a lexer, in case a user wants to know when a lexer is
    // automatically interacting with their parser.
    def collectLexer(lexer: Lexer): Map[LazyParsley[_], String] = lexerObjects(lexer).flatMap(collectNames(_)).toMap
}
