/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.instructions.token

import scala.annotation.tailrec

import parsley.character.{isHexDigit, isOctDigit}
import parsley.token.errors.SpecializedFilterConfig

import parsley.internal.collection.immutable.Trie
import parsley.internal.errors.{ExpectDesc, ExpectItem, ExpectRaw}
import parsley.internal.machine.Context
import parsley.internal.machine.XAssert._
import parsley.internal.machine.errors.{EmptyError, ExpectedError}
import parsley.internal.machine.instructions.Instr

private [internal] final class EscapeMapped(escTrie: Trie[Int], caretWidth: Int, expecteds: Set[ExpectItem]) extends Instr {
    def this(escTrie: Trie[Int], escs: Set[String]) = this(escTrie, escs.view.map(_.length).max, escs.map(new ExpectRaw(_)))
    // Do not consume input on failure, it's possible another escape sequence might share a lead
    override def apply(ctx: Context): Unit = {
        ensureRegularInstruction(ctx)
        findFirst(ctx, 0, escTrie)
    }

    @tailrec private def findLongest(ctx: Context, off: Int, escs: Trie[Int], longestChar: Int, longestSz: Int): Unit = {
        val (nextLongestChar, nextLongestSz) = escs.get("") match {
            case Some(x) => (x, off)
            case None => (longestChar, longestSz)
        }
        lazy val escsNew = escs.suffixes(ctx.peekChar(off))
        if (ctx.moreInput(off + 1) && escsNew.nonEmpty) findLongest(ctx, off + 1, escsNew, nextLongestChar, nextLongestSz)
        else {
            ctx.fastUncheckedConsumeChars(nextLongestSz)
            ctx.pushAndContinue(nextLongestChar)
        }
    }

    @tailrec private def findFirst(ctx: Context, off: Int, escs: Trie[Int]): Unit = {
        lazy val escsNew = escs.suffixes(ctx.peekChar(off))
        val couldTryMore = ctx.moreInput(off + 1) && escsNew.nonEmpty
        escs.get("") match {
            case Some(x) if couldTryMore => findLongest(ctx, off + 1, escsNew, x, off)
            case Some(x) =>
                ctx.fastUncheckedConsumeChars(off)
                ctx.pushAndContinue(x)
            case None if couldTryMore => findFirst(ctx, off + 1, escsNew)
            case None => ctx.fail(new ExpectedError(ctx.offset, ctx.line, ctx.col, expecteds, caretWidth))
        }
    }

    // $COVERAGE-OFF$
    override def toString: String = "EscapeMapped"
    // $COVERAGE-ON$
}

private [machine] abstract class EscapeSomeNumber(radix: Int) extends Instr {
    final def someNumber(ctx: Context, n: Int): EscapeSomeNumber.Result = {
        assume(n > 0, "n cannot be zero for EscapeAtMost or EscapeExactly")
        if (ctx.moreInput && pred(ctx.peekChar)) go(ctx, n - 1, ctx.consumeChar().asDigit) else EscapeSomeNumber.NoDigits
    }

    private def go(ctx: Context, n: Int, num: BigInt): EscapeSomeNumber.Result = {
        if (n > 0 && ctx.moreInput && pred(ctx.peekChar)) go(ctx, n - 1, num * radix + ctx.consumeChar().asDigit)
        else if (n > 0) EscapeSomeNumber.NoMoreDigits(n, num)
        else EscapeSomeNumber.Good(num)
    }

    protected val expected: Some[ExpectDesc] = radix match {
        case 10 => Some(new ExpectDesc("digit"))
        case 16 => Some(new ExpectDesc("hexadecimal digit"))
        case 8 => Some(new ExpectDesc("octal digit"))
        case 2 => Some(new ExpectDesc("bit"))
    }

    protected val pred: Char => Boolean = radix match {
        case 10 => _.isDigit
        case 16 => isHexDigit(_)
        case 8 => isOctDigit(_)
        case 2 => c => c == '0' || c == '1'
    }
}
private [token] object EscapeSomeNumber {
    sealed abstract class Result
    case class Good(num: BigInt) extends Result
    case class NoMoreDigits(remaining: Int, num: BigInt) extends Result
    case object NoDigits extends Result
}

private [internal] final class EscapeAtMost(n: Int, radix: Int) extends EscapeSomeNumber(radix) {
    override def apply(ctx: Context): Unit = someNumber(ctx, n) match {
        case EscapeSomeNumber.Good(num) =>
            assume(new EmptyError(ctx.offset, ctx.line, ctx.col, 0).isExpectedEmpty, "empty errors don't have expecteds, so don't effect hints")
            ctx.pushAndContinue(num)
        case EscapeSomeNumber.NoDigits => ctx.expectedFail(expected, unexpectedWidth = 1)
        case EscapeSomeNumber.NoMoreDigits(_, num) =>
            ctx.addHints(expected.toSet, unexpectedWidth = 1)
            ctx.pushAndContinue(num)
    }

    // $COVERAGE-OFF$
    override def toString: String = s"EscapeAtMost(n = $n, radix = $radix)"
    // $COVERAGE-ON$
}

private [internal] final class EscapeOneOfExactly(radix: Int, ns: List[Int], inexactErr: SpecializedFilterConfig[Int]) extends EscapeSomeNumber(radix) {
    private val (m :: ms) = ns: @unchecked
    def apply(ctx: Context): Unit = {
        val origOff = ctx.offset
        val origLine = ctx.line
        val origCol = ctx.col
        someNumber(ctx, m) match {
            case EscapeSomeNumber.Good(num) =>
                assume(new EmptyError(ctx.offset, ctx.line, ctx.col, 0).isExpectedEmpty, "empty errors don't have expecteds, so don't effect hints")
                ctx.pushAndContinue(go(ctx, m, ms, num))
            case EscapeSomeNumber.NoDigits => ctx.expectedFail(expected, unexpectedWidth = 1)
            case EscapeSomeNumber.NoMoreDigits(remaining, _) =>
                assume(remaining != 0, "cannot be left with 0 remaining digits and failed")
                ctx.fail(inexactErr.mkError(origOff, origLine, origCol, ctx.offset - origOff, m - remaining))
        }
    }

    private def rollback(ctx: Context, origOff: Int, origLine: Int, origCol: Int) = {
        // To Cosmin: this can use the save point mechanism you have
        ctx.offset = origOff
        ctx.line = origLine
        ctx.col = origCol
    }

    @tailrec def go(ctx: Context, m: Int, ns: List[Int], acc: BigInt): BigInt = ns match {
        case Nil => acc
        case n :: ns =>
            val origOff = ctx.offset
            val origLine = ctx.line
            val origCol = ctx.col
            someNumber(ctx, n-m) match { // this is the only place where the failure can actually happen: go never fails
                case EscapeSomeNumber.Good(num) =>
                    assume(new EmptyError(ctx.offset, ctx.line, ctx.col, 0).isExpectedEmpty, "empty errors don't have expecteds, so don't effect hints")
                    go(ctx, n, ns, acc * BigInt(radix).pow(n-m) + num)
                case EscapeSomeNumber.NoDigits =>
                    ctx.addHints(expected.toSet, unexpectedWidth = 1)
                    rollback(ctx, origOff, origLine, origCol)
                    acc
                case EscapeSomeNumber.NoMoreDigits(remaining, _) =>
                    assume(remaining != 0, "cannot be left with 0 remaining digits and failed")
                    assume(inexactErr.mkError(origOff, origLine, origCol, ctx.offset - origOff, n - remaining).isExpectedEmpty,
                           "filter errors don't have expecteds, so don't effect hints")
                    rollback(ctx, origOff, origLine, origCol)
                    acc
            }
    }

    // $COVERAGE-OFF$
    override def toString: String = s"EscapeOneOfExactly(ns = $ns, radix = $radix)"
    // $COVERAGE-ON$
}
