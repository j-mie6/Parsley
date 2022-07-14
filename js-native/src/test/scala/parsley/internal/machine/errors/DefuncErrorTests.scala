/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.errors

import parsley.ParsleyTest

import parsley.internal.errors.{TrivialError, FancyError, Raw, Desc, EndOfInput}
import scala.language.implicitConversions

import MockedBuilders.mockedErrorItemBuilder

class DefuncErrorTests extends ParsleyTest {
    "ClassicExpectedError" should "evaluate to TrivialError" in {
        val err = new ClassicExpectedError(0, 0, 0, None)
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }
    it should "only be empty when its label is" in {
        new ClassicExpectedError(0, 0, 0, None).isExpectedEmpty shouldBe true
        new ClassicExpectedError(0, 0, 0, Some(EndOfInput)).isExpectedEmpty shouldBe false
    }

    "ClassicExpectedErrorWithReason" should "evaluate to TrivialError" in {
        val err = new ClassicExpectedErrorWithReason(0, 0, 0, None, "")
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }
    it should "only be empty when its label is" in {
        new ClassicExpectedErrorWithReason(0, 0, 0, None, "").isExpectedEmpty shouldBe true
        new ClassicExpectedErrorWithReason(0, 0, 0, Some(EndOfInput), "").isExpectedEmpty shouldBe false
    }

    "ClassicUnexpectedError" should "evaluate to TrivialError" in {
        val err = new ClassicUnexpectedError(0, 0, 0, None, EndOfInput)
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }
    it should "only be empty when its label is" in {
        new ClassicUnexpectedError(0, 0, 0, None, EndOfInput).isExpectedEmpty shouldBe true
        new ClassicUnexpectedError(0, 0, 0, Some(EndOfInput), EndOfInput).isExpectedEmpty shouldBe false
    }

    "ClassicFancyError" should "evaluate to FancyError" in {
        val err = new ClassicFancyError(0, 0, 0, "")
        err.isTrivialError shouldBe false
        err.asParseError shouldBe a [FancyError]
    }
    it should "always be empty" in {
        new ClassicFancyError(0, 0, 0, "hi").isExpectedEmpty shouldBe true
    }

    "EmptyError" should "evaluate to TrivialError" in {
        val err = new EmptyError(0, 0, 0)
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }

    "TokenError" should "evaluate to TrivialError" in {
        val err = new TokenError(0, 0, 0, None, 1)
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }
    it should "only be empty when its label is" in {
        new TokenError(0, 0, 0, None, 1).isExpectedEmpty shouldBe true
        new TokenError(0, 0, 0, Some(EndOfInput), 1).isExpectedEmpty shouldBe false
    }

    "EmptyErrorWithReason" should "evaluate to TrivialError" in {
        val err = new EmptyErrorWithReason(0, 0, 0, "")
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }

    "MultiExpectedError" should "evaluate to TrivialError" in {
        val err = new MultiExpectedError(0, 0, 0, Set.empty, 1)
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }
    it should "only be empty when its label is" in {
        new MultiExpectedError(0, 0, 0, Set.empty, 1).isExpectedEmpty shouldBe true
        new MultiExpectedError(0, 0, 0, Set(EndOfInput), 1).isExpectedEmpty shouldBe false
    }

    "MergedErrors" should "be trivial if both children are" in {
        val err = MergedErrors(new EmptyError(0, 0, 0), new MultiExpectedError(0, 0, 0, Set.empty, 1))
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }
    they should "be a trivial error if one trivial child is further than the other fancy child" in {
        val err1 = MergedErrors(new EmptyError(1, 0, 0), new ClassicFancyError(0, 0, 0, ""))
        err1.isTrivialError shouldBe true
        err1.asParseError shouldBe a [TrivialError]

        val err2 = MergedErrors(new ClassicFancyError(0, 0, 0, ""), new EmptyError(1, 0, 0))
        err2.isTrivialError shouldBe true
        err2.asParseError shouldBe a [TrivialError]
    }
    they should "be a fancy error in any other case" in {
        val err1 = MergedErrors(new EmptyError(0, 0, 0), new ClassicFancyError(0, 0, 0, ""))
        err1.isTrivialError shouldBe false
        err1.asParseError shouldBe a [FancyError]

        val err2 = MergedErrors(new ClassicFancyError(0, 0, 0, ""), new EmptyError(0, 0, 0))
        err2.isTrivialError shouldBe false
        err2.asParseError shouldBe a [FancyError]

        val err3 = MergedErrors(new EmptyError(0, 0, 0), new ClassicFancyError(1, 0, 0, ""))
        err3.isTrivialError shouldBe false
        err3.asParseError shouldBe a [FancyError]

        val err4 = MergedErrors(new ClassicFancyError(1, 0, 0, ""), new EmptyError(0, 0, 0))
        err4.isTrivialError shouldBe false
        err4.asParseError shouldBe a [FancyError]

        val err5 = MergedErrors(new ClassicFancyError(0, 0, 0, ""), new ClassicFancyError(0, 0, 0, ""))
        err5.isTrivialError shouldBe false
        err5.asParseError shouldBe a [FancyError]

        val err6 = MergedErrors(new ClassicFancyError(1, 0, 0, ""), new ClassicFancyError(0, 0, 0, ""))
        err6.isTrivialError shouldBe false
        err6.asParseError shouldBe a [FancyError]
    }
    they should "be empty when trivial and same offset only when both children are empty" in {
        MergedErrors(new EmptyError(0, 0, 0), new EmptyError(0, 0, 0)).isExpectedEmpty shouldBe true
        MergedErrors(new EmptyError(0, 0, 0), new ClassicExpectedError(0, 0, 0, Some(EndOfInput))).isExpectedEmpty shouldBe false
        MergedErrors(new ClassicExpectedError(0, 0, 0, Some(EndOfInput)), new EmptyError(0, 0, 0)).isExpectedEmpty shouldBe false
        MergedErrors(new ClassicExpectedError(0, 0, 0, Some(EndOfInput)), new ClassicExpectedError(0, 0, 0, Some(EndOfInput))).isExpectedEmpty shouldBe false
    }
    they should "contain all the expecteds from both branches when appropriate" in {
        val err = MergedErrors(new MultiExpectedError(0, 0, 0, Set(Raw("a"), Raw("b")), 1),
                               new MultiExpectedError(0, 0, 0, Set(Raw("b"), Raw("c")), 1))
        err.asParseError.asInstanceOf[TrivialError].expecteds should contain only (Raw("a"), Raw("b"), Raw("c"))
    }

    "WithHints" should "be trivial if its child is" in {
        val err = WithHints(new ClassicExpectedError(0, 0, 0, None), EmptyHints)
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }
    it should "support fancy errors as not trivial" in {
        val err = WithHints(new ClassicFancyError(0, 0, 0, ""), EmptyHints)
        err.isTrivialError shouldBe false
        err.asParseError shouldBe a [FancyError]
    }
    it should "only be empty when its label is" in {
        WithHints(new EmptyError(0, 0, 0), EmptyHints).isExpectedEmpty shouldBe true
        WithHints(new ClassicExpectedError(0, 0, 0, Some(EndOfInput)), EmptyHints).isExpectedEmpty shouldBe false
    }

    "WithReason" should "be trivial if its child is" in {
        val err = WithReason(new ClassicExpectedError(0, 0, 0, None), "")
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }
    it should "support fancy errors as not trivial" in {
        val err = WithReason(new ClassicFancyError(0, 0, 0, ""), "")
        err.isTrivialError shouldBe false
        err.asParseError shouldBe a [FancyError]
    }
    it should "only be empty when its label is" in {
        WithReason(new EmptyError(0, 0, 0), "").isExpectedEmpty shouldBe true
        WithReason(new ClassicExpectedError(0, 0, 0, Some(EndOfInput)), "").isExpectedEmpty shouldBe false
    }

    "WithLabel" should "be trivial if its child is" in {
        val err = WithLabel(new ClassicExpectedError(0, 0, 0, None), "")
        err.isTrivialError shouldBe true
        err.asParseError shouldBe a [TrivialError]
    }
    it should "support fancy errors as not trivial" in {
        val err = WithLabel(new ClassicFancyError(0, 0, 0, ""), "")
        err.isTrivialError shouldBe false
        err.asParseError shouldBe a [FancyError]
    }
    it should "be empty if the label is empty and not otherwise" in {
        WithLabel(new EmptyError(0, 0, 0), "").isExpectedEmpty shouldBe true
        WithLabel(new EmptyError(0, 0, 0), "a").isExpectedEmpty shouldBe false
        WithLabel(new ClassicExpectedError(0, 0, 0, Some(Desc("x"))), "").isExpectedEmpty shouldBe true
        WithLabel(new ClassicExpectedError(0, 0, 0, Some(Desc("x"))), "a").isExpectedEmpty shouldBe false
    }
    it should "replace all expected" in {
        val errShow = WithLabel(new MultiExpectedError(0, 0, 0, Set(Raw("a"), Raw("b")), 1), "x")
        val errHide = WithLabel(new MultiExpectedError(0, 0, 0, Set(Raw("a"), Raw("b")), 1), "")
        errShow.asParseError.asInstanceOf[TrivialError].expecteds should contain only (Desc("x"))
        errHide.asParseError.asInstanceOf[TrivialError].expecteds shouldBe empty
    }

    "Amended" should "Change the error position information" in {
        val err = Amended(10, 10, 10, new EmptyError(0, 0, 0))
        val errOut = err.asParseError
        errOut.col shouldBe 10
        errOut.line shouldBe 10
        errOut.offset shouldBe 10
    }
    it should "work for fancy errors too" in {
        val err = Amended(10, 10, 10, new ClassicFancyError(0, 0, 0, ""))
        val errOut = err.asParseError
        errOut.col shouldBe 10
        errOut.line shouldBe 10
        errOut.offset shouldBe 10
    }

    "Entrenched" should "guard against amendment" in {
        val err = Amended(10, 10, 10, Entrenched(new EmptyError(0, 0, 0)))
        val errOut = err.asParseError
        errOut.col shouldBe 0
        errOut.line shouldBe 0
        errOut.offset shouldBe 0
    }
    it should "work for fancy errors too" in {
        val err = Amended(10, 10, 10, Entrenched(new ClassicFancyError(0, 0, 0, "")))
        val errOut = err.asParseError
        errOut.col shouldBe 0
        errOut.line shouldBe 0
        errOut.offset shouldBe 0
    }
}
