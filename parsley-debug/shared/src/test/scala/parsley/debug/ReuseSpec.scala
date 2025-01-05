/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug

import org.typelevel.scalaccompat.annotation.unused
import parsley.ParsleyTest

//noinspection ConvertExpressionToSAM
class ReuseSpec extends ParsleyTest {
    behavior of "the DebugView class and its re-usability / single-use enforcement"

    it should "throw when run multiple times, only if the view is marked as single-use" in {
        // Dummy values.
        val reusable: DebugView.Reusable = new DebugView.Reusable {
            override private [debug] def render(input: => String, tree: => DebugTree): Unit = ()
        }

        val singleUse: DebugView.SingleUse = new DebugView.SingleUse {
            override private [debug] def renderImpl(input: => String, tree: => DebugTree): Unit = ()
        }

        val tree: DebugTree = new DebugTree {
            override def parserName: String = "foo"

            override def internalName: String = parserName

            override def childNumber: Option[Long] = None

            override def parseResults: Option[ParseAttempt] = None

            override def nodeChildren: List[DebugTree] = Nil

            override def fullInput: String = "bar"
        }

        info("it should not throw when running a reusable frontend multiple times")
        reusable.render("bar", tree): @unused
        reusable.render("bar", tree): @unused

        // The first run should not throw.
        singleUse.render("bar", tree): @unused
        info("it should throw when running a single-use frontend multiple times")
        try {
            singleUse.render("bar", tree): @unused

            fail("single-use frontend did not throw an exception after running multiple times")
        } catch {
            case _: Throwable => info("single-use frontend has thrown after being run multiple times, as expected")
        }
    }
}
