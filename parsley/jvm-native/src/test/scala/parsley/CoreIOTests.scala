/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import parsley.combinator.manyTill
import parsley.character.item
import parsley.syntax.character.stringLift

import java.io.File

class CoreIOTests extends ParsleyTest {
    "parse" should "work" in {
        (manyTill(item, "Jamie Willis") *> item).parseFile(new File("LICENSE")).get shouldBe Success('\n')
    }
    it should "fail with an error when file does not exist" in {
        Parsley.empty.parseFile(new File("foo.diuh")) shouldBe a [scala.util.Failure[_]]
    }
}
