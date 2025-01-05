/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.errors

import parsley.Parsley

import parsley.internal.deepembedding.frontend.LazyParsley
import parsley.internal.deepembedding.singletons
import parsley.internal.errors.{RigidCaret, UnexpectDesc}
import parsley.internal.machine.errors.{DefuncError, EmptyError, ExpectedError, UnexpectedError}

import org.typelevel.scalaccompat.annotation.unused

/** This class can be used to generate hand-tuned error messages without using `flatMap`.
  *
  * This class, and its subclasses, describe special primitives that can use the results of
  * a previous parser to form an error message and then raise it. This is not something that
  * is normally possible with raw combinators, without using `flatMap`, which is expensive.
  *
  * Primarily, these are designed to be used with `filterWith`/`verifiedWith`/`preventWith`
  * but can be used in other parsers as well. See the methods of the class for information.
  *
  * @since 4.4.0
  */
sealed abstract class ErrorGen[-A] {
    /** This combinator takes a given parser and raises an error based on its returned results.
      *
      * The given parser produces a value and a width, which are used to synthesise and raise
      * an error message derived from the value with the given width. This is a safe way of using
      * `parser`, since it ensures that the result of the given parser `p` is not optimised out.
      * `errGen(p)` is similar to `withWidth(p).flatMap { case (x, w) => failCombinator(...) }`,
      * in that it generates errors in a context-sensitive way. However, this is much more efficient
      * than using the expensive `flatMap`, so it is provided as a primitive operation.
      *
      * @since 4.4.0
      */
    final def apply(p: Parsley[(A, Int)]): Parsley[Nothing] = (p <**> parser).impure

    /** This parser can be applied (postfix) to a parser returning a value and a width to generate an
      * error message tailored to them.
      *
      * This is '''not''' a generally safe operation to be performing, and should only be used
      * within a combinator that is guaranteed to use its results. The optimiser is not aware that
      * the results of the parser this will be applied to will actually be needed, and so may optimise
      * them out. Using this parser inside an arm of `select` or `branch`, say, would be safe, because
      * these combinators force the result of their condition to be generated, but `p <**> this.parser`
      * is not generally safe without a use of `impure` to guard it. This is what `apply` accomplishes
      * more safely.
      *
      * @since 4.4.0
      */
    final def parser: Parsley[((A, Int)) => Nothing] = new Parsley(internal)
    private [errors] def internal: LazyParsley[((A, Int)) => Nothing]

    /** This method can be overridden to control how wide an error is based on the value and width
      * that produces it.
      *
      * The width provides to this error generator likely comes directly from the span of the
      * parser used to produce the required result. However, this may not be entirely accurate
      * for how the user might want the error to be sized (perhaps there was whitespace, or the
      * parser consumed more input than was necessary to pin-point the problem). In these cases,
      * this method allows for custom logic to derive the actual width of the error message. By
      * default, just returns the given `width`.
      *
      * @since 4.4.0
      */
    def adjustWidth(@unused x: A, width: Int): Int = width
}

/** An error generator for ''Vanilla'' errors, which can tune the unexpected message and a
  * generated reason.
  *
  * @since 4.4.0
  */
class VanillaGen[-A] extends ErrorGen[A] {
    /** What should the unexpected component of the error message be based on the result the
      * offending parser produced?
      *
      * @since 4.4.0
      */
    def unexpected(@unused x: A): VanillaGen.UnexpectedItem = VanillaGen.EmptyItem

    /** What should the reason component of the error message be (if any) based on the result the
      * offending parser produced?
      *
      * @since 4.4.0
      */
    def reason(@unused x: A): Option[String] = None

    private [errors] override def internal: LazyParsley[((A, Int)) => Nothing] = new singletons.VanillaGen(this)
}

/** This object contain the `UnexpectedItem` classes, needed to define the `unexpected` component of `VanillaGen`.
  *
  * @since 4.4.0
  */
object VanillaGen {
    /** Base class for describing how to form the unexpected component of a vanilla error message from `VanillaGen`.
      *
      * @since 4.4.0
      */
    sealed abstract class UnexpectedItem {
        private [parsley] def makeError(offset: Int, line: Int, col: Int, caretWidth: Int): DefuncError
    }

    /** Signifies that the error generated should use whatever input was consumed by the offending parser
      * verbatim.
      *
      * @since 4.4.0
      */
    case object RawItem extends UnexpectedItem {
        private[parsley] def makeError(offset: Int, line: Int, col: Int, caretWidth: Int): DefuncError =
            new ExpectedError(offset, line, col, None, caretWidth)
    }

    /** Signifies that the error generated should not have an unexpected component at all (as in `filter`).
      *
      * @since 4.4.0
      */
    case object EmptyItem extends UnexpectedItem {
        private[parsley] def makeError(offset: Int, line: Int, col: Int, caretWidth: Int): DefuncError =
            new EmptyError(offset, line, col, caretWidth)
    }

    /** Signifies that the error generated should use the given name as the unexpected component.
      *
      * @since 4.4.0
      */
    final case class NamedItem(name: String) extends UnexpectedItem {
        private[parsley] def makeError(offset: Int, line: Int, col: Int, caretWidth: Int): DefuncError =
            new UnexpectedError(offset, line, col, None, new UnexpectDesc(name, new RigidCaret(caretWidth)))
    }
}

/** An error generate for ''Specialized'' errors, which can tune the freeform messages of the error.
  *
  * @since 4.4.0
  */
abstract class SpecializedGen[-A] extends ErrorGen[A] {
    /** What should the messages of the error message be based on the result the
      * offending parser produced?
      *
      * @since 4.4.0
      */
    def messages(x: A): Seq[String]

    private [errors] override def internal: LazyParsley[((A, Int)) => Nothing] = new singletons.SpecializedGen(this)
}
