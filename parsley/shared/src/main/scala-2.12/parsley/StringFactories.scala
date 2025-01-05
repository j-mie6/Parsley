/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import scala.collection.{Factory, mutable}

private [parsley] object StringFactories {
    val charFactory = new Factory[Char, String] {
        def newBuilder = new StringBuilder
        def fromSpecific(it: Traversable[Char]): String = it.mkString
    }

    val intFactory = new Factory[Int, String] {
        def newBuilder = new mutable.Builder[Int, String] {
            private val sb = new StringBuilder
            def clear(): Unit = sb.clear()
            def result(): String = sb.result()
            def +=(codepoint: Int): this.type = {
                unicode.addCodepoint(sb, codepoint)
                this
            }
        }
        def fromSpecific(it: Traversable[Int]): String = {
            val b = newBuilder
            it.foreach(b += _)
            b.result()
        }
    }
}
