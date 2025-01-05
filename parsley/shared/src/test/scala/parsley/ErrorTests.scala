/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import parsley.combinator.optional
import parsley.Parsley._
import parsley.syntax.character.{charLift, stringLift}
import parsley.character.{item, digit}
import parsley.errors.combinator.{fail => pfail, unexpected, amend, partialAmend, entrench, dislodge, amendThenDislodge, /*partialAmendThenDislodge,*/ ErrorMethods}
import parsley.errors.patterns._
import parsley.errors.SpecializedGen

class ErrorTests extends ParsleyTest {
    "mzero parsers" should "always fail" in {
        (Parsley.empty ~> 'a').parse("a") shouldBe a [Failure[_]]
        (pfail("") ~> 'a').parse("a") shouldBe a [Failure[_]]
        (unexpected("x") *> 'a').parse("a") shouldBe a [Failure[_]]
    }

    "filtering parsers" should "function correctly" in {
        val p = item.filterOut {
            case c if c.isLower => s"'$c' should have been uppercase"
        }
        inside(p.parse("a")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex shouldBe empty
                exs shouldBe empty
                rs should contain only ("'a' should have been uppercase")
        }
        p.parse("A") shouldBe Success('A')

        val q = item.guardAgainst {
            case c if c.isLower => Seq(s"'$c' is not uppercase")
        }
        inside(q.parse("a")) { case Failure(TestError((1, 1), SpecializedError(msgs, 1))) => msgs should contain only ("'a' is not uppercase") }
        q.parse("A") shouldBe Success('A')

        val r = item.unexpectedWithReasonWhen {
            case c if c.isLower => ("lowercase letter", s"'$c' should have been uppercase")
        }
        inside(r.parse("a")) { case Failure(TestError((1, 1), VanillaError(unex, exs, reasons, 1))) =>
            unex should contain (Named("lowercase letter"))
            exs shouldBe empty
            reasons should contain.only("'a' should have been uppercase")
        }

        val s = item.unexpectedWhen {
            case c if c.isLower => "lowercase letter"
        }
        inside(s.parse("a")) { case Failure(TestError((1, 1), VanillaError(unex, exs, reasons, 1))) =>
            unex should contain (Named("lowercase letter"))
            exs shouldBe empty
            reasons shouldBe empty
        }
    }

    "the collect/mapFilter combinators" should "act like a filter then a map" in {
        val p = item.collectMsg("oops") {
            case '+' => 0
            case c if c.isUpper => c - 'A' + 1
        }
        p.parse("+") shouldBe Success(0)
        p.parse("C") shouldBe Success(3)
        inside(p.parse("a"))  { case Failure(TestError((1, 1), SpecializedError(msgs, 1))) => msgs should contain only ("oops") }

        val q = item.collectMsg(c => Seq(s"$c is not appropriate")) {
            case '+' => 0
            case c if c.isUpper => c - 'A' + 1
        }
        q.parse("+") shouldBe Success(0)
        q.parse("C") shouldBe Success(3)
        inside(q.parse("a")) { case Failure(TestError((1, 1), SpecializedError(msgs, 1))) => msgs should contain only ("a is not appropriate") }

        val errGen = new SpecializedGen[Char] { def messages(c: Char): Seq[String] = Seq(s"$c is not appropriate") }
        val r = item.mapFilterWith(errGen) {
            case '+' => Some(0)
            case c if c.isUpper => Some(c - 'A' + 1)
            case _ => None
        }
        r.parse("+") shouldBe Success(0)
        r.parse("C") shouldBe Success(3)
        inside(r.parse("a")) { case Failure(TestError((1, 1), SpecializedError(msgs, 1))) => msgs should contain only ("a is not appropriate") }
    }

    // Issue #70
    "filterOut" should "not corrupt the stack under a handler" in {
        val p = atomic(item.filterOut {
            case c if c.isLower => "no lowercase!"
        })
        p.parse("a") shouldBe a [Failure[_]]
    }

    lazy val r: Parsley[List[String]] = "correct error message" <::> r
    "label" should "affect base error messages" in {
        inside(('a' ? "ay!").parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("b"))
                exs should contain only (Named("ay!"))
                rs shouldBe empty
        }
        inside(('a'.label("ay!", "see!")).parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("b"))
                exs should contain.only(Named("ay!"), Named("see!"))
                rs shouldBe empty
        }
    }
    it should "work across a recursion boundary" in {
        def p = r.label("nothing but this :)")
        inside(p.parse("")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (EndOfInput)
                exs should contain only (Named("nothing but this :)"))
                rs shouldBe empty
        }
        inside(p.parse("correct error message")) {
            case Failure(TestError((1, 22), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (EndOfInput)
                exs should contain only (Raw("correct error message"))
                rs shouldBe empty
        }
    }

    it should "replace everything under the label" in {
        val p = (optional('a') *> 'b').label("hi")
        inside(p.parse("e")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain.only(Named("hi"))
                rs shouldBe empty
        }
        val s = (optional('a') *> optional('b')).label("hi") *> 'c'
        inside(s.parse("e")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain.only(Named("hi"), Raw("c"))
                rs shouldBe empty
        }
        val t = (optional('a') *> optional('b').label("bee")).label("hi") *> 'c'
        inside(t.parse("e")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain.only(Named("hi"), Raw("c"))
                rs shouldBe empty
        }
        inside(t.parse("ae")) {
            case Failure(TestError((1, 2), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain.only(Named("bee"), Raw("c"))
                rs shouldBe empty
        }
        val u = (optional('a').hide *> optional('b')).label("hi") *> 'c'
        inside(u.parse("e")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain.only(Named("hi"), Raw("c"))
                rs shouldBe empty
        }
        inside(u.parse("ae")) {
            case Failure(TestError((1, 2), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain.only(Raw("b"), Raw("c"))
                rs shouldBe empty
        }
        val v = (optional('a').hide *> optional('b').label("bee")).label("hi") *> 'c'
        inside(v.parse("e")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain.only(Named("hi"), Raw("c"))
                rs shouldBe empty
        }
        inside(v.parse("ae")) {
            case Failure(TestError((1, 2), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain.only(Named("bee"), Raw("c"))
                rs shouldBe empty
        }
    }

    it should "not replace hints if input is consumed" in {
        inside((many(digit).label("number") <* eof).parse("1e")) {
            case Failure(TestError((1, 2), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain.only(Named("digit"), EndOfInput)
                rs shouldBe empty
        }
    }

    "hide" should "not produce any visible output" in {
        inside('a'.hide.parse("")) {
            case Failure(TestError((1, 1), VanillaError(_, exs, _, 0))) =>
                exs shouldBe empty
        }
        inside("a".hide.parse("")) {
            case Failure(TestError((1, 1), VanillaError(_, exs, _, 0))) =>
                exs shouldBe empty
        }
        inside(digit.hide.parse("")) {
            case Failure(TestError((1, 1), VanillaError(_, exs, _, 0))) =>
                exs shouldBe empty
        }
    }

    it should "suppress hints even if input is consumed" in {
        inside((many(digit).hide <* eof).parse("1e")) {
            case Failure(TestError((1, 2), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain only EndOfInput
                rs shouldBe empty
        }
    }

    it should "not allow hints to be unsuppressed by another label" in {
        inside((many(digit).hide.label("hey") <* eof).parse("1e")) {
            case Failure(TestError((1, 2), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("e"))
                exs should contain only EndOfInput
                rs shouldBe empty
        }
    }

    "explain" should "provide a message, but only on failure" in {
        inside(Parsley.empty.explain("oops!").parse("")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 0))) =>
                unex shouldBe empty
                exs shouldBe empty
                rs should contain only ("oops!")
        }
        inside('a'.explain("requires an a").parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("b"))
                exs should contain only (Raw("a"))
                rs should contain only ("requires an a")
        }
        inside(('a'.explain("an a") <|> 'b'.explain("a b")).parse("c")){
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("c"))
                exs should contain.only(Raw("a"), Raw("b"))
                rs should contain.only("an a", "a b")
        }
        inside(('a'.explain("should be absent") *> 'b').parse("a")) {
            case Failure(TestError((1, 2), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (EndOfInput)
                exs should contain only (Raw("b"))
                rs shouldBe empty
        }
    }
    it should "not have any effect when more input has been consumed since it was added" in {
        inside(('a'.explain("should be absent") <|> ('b' *> digit)).parse("b")) {
            case Failure(TestError((1, 2), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (EndOfInput)
                exs should contain only (Named("digit"))
                rs shouldBe empty
        }
    }

    "fail" should "yield a raw message" in {
        inside(pfail("hi").parse("b")) { case Failure(TestError((1, 1), SpecializedError(msgs, 1))) => msgs should contain only ("hi") }
    }
    it should "be flexible when the width is unspecified" in {
        inside(("abc" | pfail("hi")).parse("xyz")) {
            case Failure(TestError((1, 1), SpecializedError(msgs, 3))) => msgs should contain only ("hi")
        }
    }
    it should "dominate otherwise" in {
        inside(("abc" | pfail(2, "hi")).parse("xyz")) {
            case Failure(TestError((1, 1), SpecializedError(msgs, 2))) => msgs should contain only ("hi")
        }
    }

    "unexpected" should "yield changes to unexpected messages" in {
        inside(unexpected("bee").parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Named("bee"))
                exs shouldBe empty
                rs shouldBe empty
        }
    }
    it should "produce expected message under influence of ?, along with original message" in {
        inside(('a' <|> unexpected("bee") ? "something less cute").parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Named("bee"))
                exs should contain.only(Raw("a"), Named("something less cute"))
                rs shouldBe empty
        }
    }
    it should "be flexible when the width is unspecified" in {
        inside(("abc" | unexpected("bee")).parse("xyz")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 3))) =>
                unex should contain (Named("bee"))
                exs should contain.only(Raw("abc"))
                rs shouldBe empty
        }
    }
    it should "dominate otherwise" in {
        inside(("abc" | unexpected(2, "bee")).parse("xyz")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 2))) =>
                unex should contain (Named("bee"))
                exs should contain.only(Raw("abc"))
                rs shouldBe empty
        }
    }

    "lookAhead" should "produce no hints following it" in {
        val p = 'a' <|> lookAhead(optional(digit) *> 'c') <|> 'b'
        inside(p.parse("d")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("d"))
                exs should contain.only(Raw("a"), Raw("c"), Raw("b"), Named("digit"))
                rs shouldBe empty
        }
        val q = 'a' <|> lookAhead(optional(digit)) *> 'c' <|> 'b'
        inside(q.parse("d")){
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("d"))
                exs should contain.only(Raw("a"), Raw("b"), Raw("c"))
                rs shouldBe empty
        }
        val r = 'a' <|> lookAhead(digit) *> 'c' <|> 'b'
        inside(r.parse("d")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("d"))
                exs should contain.only(Raw("a"), Raw("b"), Named("digit"))
                rs shouldBe empty
        }
    }

    "notFollowedBy" should "produce no hints" in {
        val p = 'a' <|> notFollowedBy(optional(digit)) *> 'c' <|> 'b'
        inside(p.parse("d")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("d"))
                exs should contain.only(Raw("a"), Raw("b"))
                rs shouldBe empty
        }
        val q = 'a' <|> notFollowedBy(digit) *> 'c' <|> 'b'
        inside(q.parse("d")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("d"))
                exs should contain.only(Raw("a"), Raw("c"), Raw("b"))
                rs shouldBe empty
        }
    }

    "empty" should "produce unknown error messages" in {
        inside(Parsley.empty.parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 0))) =>
                unex shouldBe empty
                exs shouldBe empty
                rs shouldBe empty
        }
    }
    it should "produce no unknown message under influence of ?" in {
        inside((Parsley.empty ? "something, at least").parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 0))) =>
                unex shouldBe empty
                exs should contain only (Named("something, at least"))
                rs shouldBe empty
        }
    }
    it should "not produce an error message at the end of <|> chain" in {
        inside(('a' <|> Parsley.empty).parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("b"))
                exs should contain only (Raw("a"))
                rs shouldBe empty
        }
    }
    it should "produce an expected error under influence of ? in <|> chain" in {
        inside(('a' <|> Parsley.empty ? "something, at least").parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("b"))
                exs should contain.only(Named("something, at least"), Raw("a"))
                rs shouldBe empty
        }
    }
    it should "have an effect if it's caret is wider" in {
        inside(('a' <|> Parsley.empty(3)).parse("bcd")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 3))) =>
                unex should contain (Raw("bcd"))
                exs should contain.only(Raw("a"))
                rs shouldBe empty
        }
    }

    "eof" should "produce expected end of input" in {
        inside(eof.parse("a")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("a"))
                exs should contain only (EndOfInput)
                rs shouldBe empty
        }
    }
    it should "change message under influence of ?" in {
        inside((eof ? "something more").parse("a")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("a"))
                exs should contain only (Named("something more"))
                rs shouldBe empty
        }
    }

    "amend" should "change error messages under it" in {
        val p = 'a' *> amend('b' *> 'c' *> 'd')
        inside(p.parse("ab")) { case Failure(TestError((1, 2), _)) => }
        inside(p.parse("abc")) { case Failure(TestError((1, 2), _)) => }
    }
    it should "not affect input consumption" in {
        (amend('a' *> 'b') <|> 'a').parse("a") shouldBe a [Failure[_]]
    }

    "entrench" should "prevent the change of error messages under it" in {
        val p = 'a' *> amend('b' *> entrench('c') *> 'd')
        inside(p.parse("ab")) { case Failure(TestError((1, 3), _)) => }
        inside(p.parse("abc")) { case Failure(TestError((1, 2), _)) => }
        val q = 'a' *> amend('b' *> 'c' *> entrench('d'))
        inside(q.parse("ab")) { case Failure(TestError((1, 2), _)) => }
        inside(q.parse("abc")) { case Failure(TestError((1, 4), _)) => }
    }
    it should "not prevent the action of amend inside it" in {
        val p = 'a' *> amend('b' *> entrench(amend('c' *> 'd' *> entrench('e'))) *> 'f')
        inside(p.parse("ab")) { case Failure(TestError((1, 3), _)) => }
        inside(p.parse("abc")) { case Failure(TestError((1, 3), _)) => }
        inside(p.parse("abcd")) { case Failure(TestError((1, 5), _)) => }
        inside(p.parse("abcde")) { case Failure(TestError((1, 2), _)) => }
    }

    "dislodge" should "undo an entrench so that amend works again" in {
        val p = 'a' *> amend('b' *> dislodge(entrench(entrench('c'))) *> 'd')
        inside(p.parse("ab")) { case Failure(TestError((1, 2), _)) => }
        inside(p.parse("abc")) { case Failure(TestError((1, 2), _)) => }
    }
    it should "not prevent another entrench from occurring" in {
        val p = 'a' *> amend('b' *> entrench(dislodge(entrench('c'))))
        inside(p.parse("ab")) { case Failure(TestError((1, 3), _)) => }
    }
    it should "only unwind as many as instructed if applicable" in {
        val p = 'a' *> amend('b' *> dislodge(1)(entrench(entrench('c'))) *> 'd')
        val q = 'a' *> amend('b' *> dislodge(2)(entrench(entrench('c'))) *> 'd')
        inside(p.parse("abc")) { case Failure(TestError((1, 2), _)) => }
        inside(p.parse("ab")) { case Failure(TestError((1, 3), _)) => }
        inside(q.parse("abc")) { case Failure(TestError((1, 2), _)) => }
        inside(q.parse("ab")) { case Failure(TestError((1, 2), _)) => }
    }

    "amendThenDislodge" should "amend only non-entrenched messages and dislodge those that are" in {
        val p = 'a' *> amendThenDislodge('b' *> entrench(entrench('c')) *> 'd')
        val q = amend(p)
        inside(p.parse("ab")) { case Failure(TestError((1, 3), _)) => }
        inside(p.parse("abc")) { case Failure(TestError((1, 2), _)) => }
        inside(q.parse("ab")) { case Failure(TestError((1, 1), _)) => }
        inside(q.parse("abc")) { case Failure(TestError((1, 1), _)) => }
    }
    it should "only unwind as many as instructed if applicable" in {
        val p = 'a' *> amendThenDislodge(1)('b' *> entrench(entrench('c')) *> 'd')
        val q = amend(p)
        val r = 'a' *> amendThenDislodge(2)('b' *> entrench(entrench('c')) *> 'd')
        val s = amend(r)
        inside(p.parse("ab")) { case Failure(TestError((1, 3), _)) => }
        inside(p.parse("abc")) { case Failure(TestError((1, 2), _)) => }
        inside(q.parse("ab")) { case Failure(TestError((1, 3), _)) => }
        inside(q.parse("abc")) { case Failure(TestError((1, 1), _)) => }
        inside(r.parse("ab")) { case Failure(TestError((1, 3), _)) => }
        inside(r.parse("abc")) { case Failure(TestError((1, 2), _)) => }
        inside(s.parse("ab")) { case Failure(TestError((1, 1), _)) => }
        inside(s.parse("abc")) { case Failure(TestError((1, 1), _)) => }
    }

    "partialAmend" should "perform visual amendment but allow for domination" in {
        def errorMaker(n: Int, msg: String) = atomic(combinator.exactly(n, 'a') *> ('b' <|> pfail(msg)))
        val p = errorMaker(2, "small") <|> amend(errorMaker(3, "big"))
        val q = errorMaker(2, "small") <|> partialAmend(errorMaker(3, "big"))
        val r = errorMaker(3, "first") <|> partialAmend(errorMaker(3, "second"))
        info("a regular amend should lose against even a shallower error")
        inside(p.parse("a" * 4)) {
            case Failure(TestError((1, 3), SpecializedError(msgs, 1))) =>
                msgs should contain only "small"
        }
        info("a partial amend can win against an error at a lesser offset but greater presentation")
        inside(q.parse("a" * 4)) {
            case Failure(TestError((1, 1), SpecializedError(msgs, 1))) =>
                msgs should contain only "big"
        }
        info("however, they do not win at equal underlying offset")
        inside(r.parse("a" * 4)) {
            case Failure(TestError((1, 4), SpecializedError(msgs, 1))) =>
                msgs should contain only "first"
        }
    }

    "oneOf" should "incorporate range notation into the error" in {
        inside(character.oneOf('0' to '9').parse("a")) {
            case Failure(TestError(_, VanillaError(_, expecteds, _, 1))) =>
                expecteds should contain only Named("one of \"0\" to \"9\"")
        }
    }

    it should "incorporate sets of characters into the error" in {
        inside(character.oneOf(('0' to '9').toSet).parse("a")) {
            case Failure(TestError(_, VanillaError(_, expecteds, _, 1))) =>
                expecteds should contain only Named("one of \"0\", \"1\", \"2\", \"3\", \"4\", \"5\", \"6\", \"7\", \"8\", or \"9\"")
        }
    }

    "noneOf" should "incorporate range notation into the error" in {
        inside(character.noneOf('0' to '9').parse("8")) {
            case Failure(TestError(_, VanillaError(_, expecteds, _, 1))) =>
                expecteds should contain only Named("anything outside of \"0\" to \"9\"")
        }
    }

    it should "incorporate sets of characters into the error" in {
        inside(character.noneOf(('0' to '9').toSet).parse("8")) {
            case Failure(TestError(_, VanillaError(_, expecteds, _, _))) =>
                expecteds should contain only Named("anything except \"0\", \"1\", \"2\", \"3\", \"4\", \"5\", \"6\", \"7\", \"8\", or \"9\"")
        }
    }

    // Verified Errors
    "verifiedFail" should "fail having consumed input on the parser success" in {
        inside(optional("abc".verifiedFail(x => Seq("no, no", s"absolutely not $x"))).parse("abc")) {
            case Failure(TestError((1, 1), SpecializedError(msgs, 3))) =>
                msgs should contain.only("no, no", "absolutely not abc")
        }
    }
    it should "not consume input if the parser did not succeed" in {
        optional("abc".verifiedFail("no, no", "absolutely not")).parse("ab") shouldBe Success(())
    }
    it should "not produce any labels" in {
        inside("abc".verifiedFail("hi").parse("ab")) {
            case Failure(TestError((1, 1), VanillaError(None, expecteds, _, 0))) =>
                expecteds shouldBe empty
        }
    }

    "verifiedExplain/Unexpected" should "fail having consumed input on the parser success" in {
        inside(optional("abc".verifiedExplain(x => s"$x is not allowed")).parse("abc")) {
            case Failure(TestError((1, 1), VanillaError(unex, expecteds, reasons, 3))) =>
                expecteds shouldBe empty
                unex should contain (Raw("abc"))
                reasons should contain only ("abc is not allowed")
        }
        inside(optional("abc".verifiedExplain(s"abc is not allowed")).parse("abc")) {
            case Failure(TestError((1, 1), VanillaError(unex, expecteds, reasons, 3))) =>
                expecteds shouldBe empty
                unex should contain (Raw("abc"))
                reasons should contain only ("abc is not allowed")
        }
        inside(optional("abc".verifiedUnexpected).parse("abc")) {
            case Failure(TestError((1, 1), VanillaError(unex, expecteds, reasons, 3))) =>
                expecteds shouldBe empty
                unex should contain (Raw("abc"))
                reasons shouldBe empty
        }
    }
    it should "not consume input if the parser did not succeed" in {
        optional("abc".verifiedExplain(x => s"$x is not allowed")).parse("ab") shouldBe Success(())
        optional("abc".verifiedExplain(s"abc is not allowed")).parse("ab") shouldBe Success(())
        optional("abc".verifiedUnexpected).parse("ab") shouldBe Success(())
    }
    it should "not produce any labels" in {
        inside("abc".verifiedUnexpected.parse("ab")) {
            case Failure(TestError((1, 1), VanillaError(None, expecteds, _, 0))) =>
                expecteds shouldBe empty
        }
    }

    // Preventative Errors
    "preventativeFail" should "fail having consumed input on the parser success" in {
        inside(optional("abc".preventativeFail(x => Seq("no, no", s"absolutely not $x"))).parse("abc")) {
            case Failure(TestError((1, 1), SpecializedError(msgs, 3))) =>
                msgs should contain.only("no, no", "absolutely not abc")
        }
    }
    it should "not consume input if the parser did not succeed and not fail" in {
        "abc".preventativeFail("no, no", "absolutely not").parse("ab") shouldBe Success(())
    }

    "preventativeExplain" should "fail having consumed input on the parser success" in {
        inside(optional("abc".preventativeExplain(x => s"$x is not allowed")).parse("abc")) {
            case Failure(TestError((1, 1), VanillaError(unex, expecteds, reasons, 3))) =>
                expecteds shouldBe empty
                unex should contain (Raw("abc"))
                reasons should contain only ("abc is not allowed")
        }
        inside(optional("abc".preventativeExplain(s"abc is not allowed")).parse("abc")) {
            case Failure(TestError((1, 1), VanillaError(unex, expecteds, reasons, 3))) =>
                expecteds shouldBe empty
                unex should contain (Raw("abc"))
                reasons should contain only ("abc is not allowed")
        }
    }
    it should "not consume input if the parser did not succeed and not fail" in {
        "abc".preventativeExplain(x => s"$x is not allowed").parse("ab") shouldBe Success(())
        "abc".preventativeExplain(s"abc is not allowed").parse("ab") shouldBe Success(())
    }
    it should "produce labels when specified" in {
        inside("abc".preventativeExplain(x => s"$x is not allowed", "something else").parse("abc")) {
            case Failure(TestError((1, 1), VanillaError(Some(Raw("abc")), expecteds, reasons, 3))) =>
                expecteds should contain only (Named("something else"))
                reasons should contain only ("abc is not allowed")
        }
    }

    // Issue 107
    "hints" should "incorporate only with errors at the same offset depth" in {
        val p = atomic('a' ~> digit)
        val parser = optional('b'.label("b")) ~> p.label("foo")
        inside(parser.parse("aa")) {
            case Failure(TestError(_, VanillaError(_, expected, _, 1))) =>
                expected should contain only (Named("digit"))
        }

        val q = amend('a' ~> digit)
        val qarser = optional('b'.label("b")) ~> q.label("foo")
        inside(qarser.parse("aa")) {
            case Failure(TestError(_, VanillaError(_, expected, _, 1))) =>
                expected should contain.allOf(Named("foo"), Named("b"))
        }
    }
}
