/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.errors

import parsley.Parsley
import parsley.errors.combinator, combinator.ErrorMethods

import parsley.internal.errors.{RigidCaret, UnexpectDesc}
import parsley.internal.machine.errors.{ClassicFancyError, DefuncError, EmptyError, EmptyErrorWithReason, UnexpectedError}

import org.typelevel.scalaccompat.annotation.unused

/** This trait, and its subclasses, can be used to configure how filters should be used within the `Lexer`.
  * @since 4.1.0
  * @group filters
  */
sealed trait FilterConfig[A] {
    private [parsley] def filter(p: Parsley[A])(f: A => Boolean): Parsley[A]
    private [parsley] def mkError(offset: Int, line: Int, col: Int, caretWidth: Int, x: A): DefuncError
    // $COVERAGE-OFF$
    private [parsley] def collect[B](p: Parsley[A])(f: PartialFunction[A, B]): Parsley[B] = this.filter(p)(f.isDefinedAt).map(f)
    private [parsley] def injectLeft[B]: FilterConfig[Either[A, B]]
    private [parsley] def injectRight[B]: FilterConfig[Either[B, A]]
    private [parsley] def injectSnd[B]: FilterConfig[(B, A)]
    // $COVERAGE-ON$
}

/** This subtrait of `FilterConfig` specifies that only filters generating ''specialised'' errors may be used.
  * @since 5.0.0
  * @group filters
  */
sealed trait SpecializedFilterConfig[A] extends FilterConfig[A]
/** This subtrait of `FilterConfig` specifies that only filters generating ''vanilla'' errors may be used.
  * @since 4.1.0
  * @group filters
  */
sealed trait VanillaFilterConfig[A] extends FilterConfig[A]

/** This class ensures that the filter will generate ''specialised'' messages for the given failing parse.
  * @since 5.0.0
  * @group filters
  */
abstract class SpecializedMessage[A] extends SpecializedFilterConfig[A] { self =>
    private def this(@unused fullAmend: Boolean) = this()
    /** This method produces the messages for the given value.
      * @since 4.1.0
      * @group badchar
      */
    def message(x: A): Seq[String]

    private [parsley] final override def filter(p: Parsley[A])(f: A => Boolean) = p.guardAgainst {
        case x if !f(x) => message(x)
    }
    private [parsley] final override def collect[B](p: Parsley[A])(f: PartialFunction[A, B]) = p.collectMsg(message(_))(f)
    private [parsley] final override def mkError(offset: Int, line: Int, col: Int, caretWidth: Int, x: A): DefuncError = {
        new ClassicFancyError(offset, line, col, new RigidCaret(caretWidth), message(x): _*)
    }

    // $COVERAGE-OFF$
    private [parsley] final override def injectLeft[B] = new SpecializedMessage[Either[A, B]] {
        def message(xy: Either[A, B]) = {
            val Left(x) = xy: @unchecked
            self.message(x)
        }
    }
    private [parsley] final override def injectRight[B] = new SpecializedMessage[Either[B, A]] {
        def message(xy: Either[B, A]) = {
            val Right(y) = xy: @unchecked
            self.message(y)
        }
    }
    private [parsley] final override def injectSnd[B] = new SpecializedMessage[(B, A)] {
        def message(xy: (B, A)) = self.message(xy._2)
    }
    // $COVERAGE-ON$
}

/** This class ensures that the filter will generate a ''vanilla'' unexpected item for the given failing parse.
  * @since 4.1.0
  * @group filters
  */
abstract class Unexpected[A] extends VanillaFilterConfig[A] { self =>
    private def this(@unused fullAmend: Boolean) = this()
    /** This method produces the unexpected label for the given value.
      * @since 4.1.0
      * @group badchar
      */
    def unexpected(x: A): String

    private [parsley] final override def filter(p: Parsley[A])(f: A => Boolean) = p.unexpectedWhen {
        case x if !f(x) => unexpected(x)
    }
    private [parsley] final override def mkError(offset: Int, line: Int, col: Int, caretWidth: Int, x: A): DefuncError = {
        new UnexpectedError(offset, line, col, Set.empty, new UnexpectDesc(unexpected(x), new RigidCaret(caretWidth)))
    }

    // $COVERAGE-OFF$
    private [parsley] final override def injectLeft[B] = new Unexpected[Either[A, B]] {
        def unexpected(xy: Either[A, B]) = {
            val Left(x) = xy: @unchecked
            self.unexpected(x)
        }
    }
    private [parsley] final override def injectRight[B] = new Unexpected[Either[B, A]] {
        def unexpected(xy: Either[B, A]) = {
            val Right(y) = xy: @unchecked
            self.unexpected(y)
        }
    }
    private [parsley] final override def injectSnd[B] = new Unexpected[(B, A)] {
        def unexpected(xy: (B, A)) = self.unexpected(xy._2)
    }
    // $COVERAGE-ON$
}

/** This class ensures that the filter will generate a ''vanilla'' reason for the given failing parse.
  * @since 4.1.0
  * @group filters
  */
abstract class Because[A] extends VanillaFilterConfig[A] { self =>
    private def this(@unused fullAmend: Boolean) = this()
    /** This method produces the reason for the given value.
      * @since 4.1.0
      * @group badchar
      */
    def reason(x: A): String

    private [parsley] final override def filter(p: Parsley[A])(f: A => Boolean) = p.filterOut {
        case x if !f(x) => reason(x)
    }
    private [parsley] final override def mkError(offset: Int, line: Int, col: Int, caretWidth: Int, x: A): DefuncError = {
        new EmptyErrorWithReason(offset, line, col, reason(x), caretWidth)
    }

    // $COVERAGE-OFF$
    private [parsley] final override def injectLeft[B] = new Because[Either[A, B]] {
        def reason(xy: Either[A, B]) = {
            val Left(x) = xy: @unchecked
            self.reason(x)
        }
    }
    private [parsley] final override def injectRight[B] = new Because[Either[B, A]] {
        def reason(xy: Either[B, A]) = {
            val Right(y) = xy: @unchecked
            self.reason(y)
        }
    }
    private [parsley] final override def injectSnd[B] = new Because[(B, A)] {
        def reason(xy: (B, A)) = self.reason(xy._2)
    }
    // $COVERAGE-ON$
}

/** This class ensures that the filter will generate a ''vanilla'' unexpected item and a reason for the given failing parse.
  * @since 4.1.0
  * @group filters
  */
abstract class UnexpectedBecause[A] extends VanillaFilterConfig[A] { self =>
    private def this(@unused fullAmend: Boolean) = this()
    /** This method produces the unexpected label for the given value.
      * @since 4.1.0
      * @group badchar
      */
    def unexpected(x: A): String
    /** This method produces the reason for the given value.
      * @since 4.1.0
      * @group badchar
      */
    def reason(x: A): String

    private [parsley] final override def filter(p: Parsley[A])(f: A => Boolean) = p.unexpectedWithReasonWhen {
        case x if !f(x) => (unexpected(x), reason(x))
    }
    private [parsley] final override def mkError(offset: Int, line: Int, col: Int, caretWidth: Int, x: A): DefuncError = {
        new UnexpectedError(offset, line, col, Set.empty, new UnexpectDesc(unexpected(x), new RigidCaret(caretWidth))).withReason(reason(x))
    }

    // $COVERAGE-OFF$
    private [parsley] final override def injectLeft[B] = new UnexpectedBecause[Either[A, B]] {
        def unexpected(xy: Either[A, B]) = {
            val Left(x) = xy: @unchecked
            self.unexpected(x)
        }
        def reason(xy: Either[A, B]) = {
            val Left(x) = xy: @unchecked
            self.reason(x)
        }
    }
    private [parsley] final override def injectRight[B] = new UnexpectedBecause[Either[B, A]] {
        def unexpected(xy: Either[B, A]) = {
            val Right(y) = xy: @unchecked
            self.unexpected(y)
        }
        def reason(xy: Either[B, A]) = {
            val Right(y) = xy: @unchecked
            self.reason(y)
        }
    }
    private [parsley] final override def injectSnd[B] = new UnexpectedBecause[(B, A)] {
        def unexpected(xy: (B, A)) = self.unexpected(xy._2)
        def reason(xy: (B, A)) = self.reason(xy._2)
    }
    // $COVERAGE-ON$
}

/** This class can be used to not specify an error configuration for the filter, a regular `filter` is used instead.
  * @since 4.1.0
  * @group filters
  */
final class BasicFilter[A] extends SpecializedFilterConfig[A] with VanillaFilterConfig[A] {
    private [parsley] final override def filter(p: Parsley[A])(f: A => Boolean) = p.filter(f)
    private [parsley] final override def collect[B](p: Parsley[A])(f: PartialFunction[A, B]) = p.collect(f)
    private [parsley] final override def mkError(offset: Int, line: Int, col: Int, caretWidth: Int, @unused x: A): DefuncError = {
        new EmptyError(offset, line, col, caretWidth)
    }

    // $COVERAGE-OFF$
    private [parsley] final override def injectLeft[B] = new BasicFilter[Either[A, B]]
    private [parsley] final override def injectRight[B] = new BasicFilter[Either[B, A]]
    private [parsley] final override def injectSnd[B] = new BasicFilter[(B, A)]
    // $COVERAGE-ON$
}
