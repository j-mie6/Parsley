/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import scala.annotation.experimental
import parsley.debug.internal.Renamer

@experimental
class AnnotationTest extends ParsleyTest {
    "parsley.debuggable" should "fill in the names for objects" in {
        Renamer.nameOf(None, annotation.otherParsers.a.internal) shouldBe "a"
    }

    it should "fill in the names for classes" in {
        val parsers = new annotation.parsers(6)
        Renamer.nameOf(None, parsers.p.internal) shouldBe "p"
        Renamer.nameOf(None, parsers.q.internal) shouldBe "q"
        Renamer.nameOf(None, parsers.r.internal) shouldBe "r"
        Renamer.nameOf(None, parsers.s.internal) should not be ("char") // see the objects lol
    }
}
