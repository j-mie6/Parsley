/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.errors

import parsley.XAssert._

import parsley.internal.errors.{CaretWidth, ExpectDesc, ExpectItem, FancyError, ParseError, TrivialError, UnexpectDesc}

// This file contains the defunctionalised forms of the error messages.
// Essentially, whenever an error is created in the machine, it should make use of one of
// these case classes. This means that every error message created will be done in a single
// O(1) allocation, avoiding anything to do with the underlying sets, options etc.

/** This structure represents a collection of operations that build up a
  * `ParseError`. This is done to allow for the operations to be performed
  * lazily, or potentially not at all if the error message is discarded
  * or a merge occurs with a different error kind. It also allows for the
  * conversion to `ParseError` to make use of mutable collections, which
  * would otherwise be unsound.
  */
private [parsley] sealed abstract class DefuncError {
    // Stores the bit-packed flags below, starting from bit 0 upwards
    private [errors] val flags: Int
    /** Is this error a trivial error, or is it fancy? */
    private [machine] final def isTrivialError: Boolean = (flags & DefuncError.TrivialErrorMask) != 0
    /** Does this error, if its trivial, have any expected items? */
    private [machine] final def isExpectedEmpty: Boolean = (flags & DefuncError.ExpectedEmptyMask) != 0
    /** Is this error protected from amendment via the `amend` combinator? */
    private [errors] final def entrenchedBy: Int = flags & DefuncError.EntrenchedMask
    private [machine] final def entrenched: Boolean = entrenchedBy > 0
    /** Is this error created while parsing a lexical token? */
    private [machine] final def lexicalError: Boolean = (flags & DefuncError.LexicalErrorMask) != 0
    /** Is the caret on this error flexible (only relevant for fancy errors) **/
    private [machine] final def flexibleCaret: Boolean = (flags & DefuncError.FlexibleCaretMask) != 0
    /** The offset at which this error appears to occur */
    private [machine] val presentationOffset: Int
    /** The offset at which this error supposedly originated */
    private [machine] def underlyingOffset: Int
    /** This function forces the lazy defunctionalised structure into a final `ParseError` value. */
    private [machine] def asParseError(implicit itemBuilder: ErrorItemBuilder): ParseError

    // Operations: these are the smart constructors for the hint operations, which will reduce the number of objects in the binary
    // they all perform some form of simplification step to avoid unnecesary allocations

    /** This operation merges two errors.
      *
      * The errors must have the same offset, with the deeper of the two
      * taking precedence if not. Otherwise, if they are equal offset, errors
      * can only be merged when they are both the same kind: if they are not,
      * fancy errors take precedence over trivial errors.
      *
      * @param err the error to merge with this one
      * @return either the merged error, or one of the two originals
      */
    private [machine] def merge(err: DefuncError): DefuncError
    /** This operation adds the currently available hints as expected
      * items into an error message. This is only applicable for trivial
      * errors.
      *
      * @param hints the hints to add to the message
      * @return the new error with hints
      * @note hints should only be incorporated when they are at the same offset
      */
    private [machine] def withHints(hints: DefuncHints): DefuncError
    /** This operation adds a reason to a trivial error.
      *
      * @param reason the reason to add to the message
      * @param offset the offset that the reason is applicable at
      * @return the error with the reason incorporated
      * @note reasons are kept in-order
      */
    private [parsley] def withReason(reason: String, offset: Int): DefuncError
    private [parsley] def withReason(reason: String): DefuncError = withReason(reason, presentationOffset)
    /** This operation replaces the expected labels in this error message
      * by the given label. This can only happen when the offset of
      * this error message matches the given offset: this should be the
      * offset on entry to the `label` ''combinator'''.
      *
      * If the label given is `""`, then this label is not emitted into
      * the error message, having the effect of removing the underlying
      * labels.
      *
      * @param label the name to replace the expected labels with
      * @param offset the offset that the label is applicable at
      * @return an error message that incorporates the relevant errors
      */
    private [machine] def label(labels: Iterable[String], offset: Int): DefuncError
    /** This operation changes the offset, line, and column number that
      * an error appears to occur at. The effect of this operation is
      * suppressed by `entrench`, however.
      *
      * @param partial whether the amend leaves the underlying offset as is
      * @param presentationOffset the new presentation offset of the error
      * @param line the new line of the error
      * @param col the new column of the error
      * @return a new error that has been amended
      */
    private [machine] def amend(partial: Boolean, presentationOffset: Int, line: Int, col: Int): DefuncError
    /** This operation resists the changes attempted by an `amend` operation
      * ''above'' it, without affecting the messages below.
      *
      * @return an entrenched error message
      */
    private [machine] def entrench: DefuncError
    /** This operation undoes the `amend` protection provided by an underlying entrenched error.
      *
      * @return a non-entrenched error message
      */
    private [machine] def dislodge(by: Int): DefuncError
    /** This operation sets this error message to be considered as a lexical
      * error message, which means that it will not perform lexical extraction
      * within the builder, instead opting to extract a token via raw input.
      * This only happens if the error occured at an offset ''greater'' than
      * the provided offset (which is the beginning of where the token started
      * parsing).
      *
      * @param offset the offset the error must have occured deeper than
      * @return a lexical error message
      */
    private [machine] def markAsLexical(offset: Int): DefuncError
}
// These are not covered by coverage because they are all inlined
private [errors] object DefuncError {
    // $COVERAGE-OFF$
    private [errors] final val TrivialErrorMask = 1 << (java.lang.Integer.SIZE - 1)
    private [errors] final val ExpectedEmptyMask = 1 << (java.lang.Integer.SIZE - 2)
    private [errors] final val LexicalErrorMask = 1 << (java.lang.Integer.SIZE - 3)
    private [errors] final val FlexibleCaretMask = 1 << (java.lang.Integer.SIZE - 4)
    // These are the remaining bits after discarding the above
    private [errors] final val EntrenchedMask = 0xffffffff & ~TrivialErrorMask & ~ExpectedEmptyMask & ~LexicalErrorMask & ~FlexibleCaretMask
    // $COVERAGE-ON$
}

/** Represents partially evaluated trivial errors */
private [errors] sealed abstract class TrivialDefuncError extends DefuncError {
    private [machine] final override def asParseError(implicit itemBuilder: ErrorItemBuilder): TrivialError = {
        val errorBuilder = new TrivialErrorBuilder(presentationOffset, !itemBuilder.inRange(presentationOffset), lexicalError)
        makeTrivial(errorBuilder)
        errorBuilder.mkError
    }
    private [errors] def makeTrivial(builder: TrivialErrorBuilder): Unit

    /** This method collects up the error labels of this error message into the given `HintState`.
      *
      * This is important for `DefuncHints`, which generates a hints set for previously recovered
      * errors. This is a more efficient way of computing this information as it ignores the other
      * data processed during the formation of a regular error message.
      *
      * @param state the hint state that is collecting up the expected items
      * @note this function should be tail-recursive!
      */
    private [errors] final def collectHints(collector: HintCollector): Unit = this match {
        case self: BaseError           =>
            collector ++= self.expected
            collector.updateWidth(self.unexpectedWidth)
        // FIXME: Why doesn't this traverse deeper to collect the width?
        case self: WithLabel           => collector ++= self.labels.map(new ExpectDesc(_))
        case self: WithHints           =>
            self.hints.collect(collector)
            self.err.collectHints(collector)
        case self: TrivialTransitive   => self.err.collectHints(collector) // the WithLabel and WithHint cases must be above
        case self: TrivialMergedErrors =>
            self.err1.collectHints(collector)
            self.err2.collectHints(collector)
    }

    private [errors] final def adjustCaret(builder: FancyErrorBuilder): Unit = this match {
        case self: BaseError           => builder.updateCaretWidth(self.unexpectedWidth)
        case self: TrivialTransitive   => self.err.adjustCaret(builder)
        case self: TrivialMergedErrors =>
            self.err1.adjustCaret(builder)
            self.err2.adjustCaret(builder)
    }

    private [machine] final override def merge(err: DefuncError): DefuncError = {
        val cmp = Integer.compareUnsigned(this.underlyingOffset, err.underlyingOffset)
        if (cmp > 0) this
        else if (cmp < 0) err
        else {
            val cmp = Integer.compareUnsigned(this.presentationOffset, err.presentationOffset)
            if (cmp > 0) this
            else if (cmp < 0) err
            else err match {
                case err: TrivialDefuncError                    => new TrivialMergedErrors(this, err)
                case err: FancyDefuncError if err.flexibleCaret => new FancyAdjustCaret(err, this)
                case err                                        => err
            }
        }
    }

    private [machine] final override def withHints(hints: DefuncHints): TrivialDefuncError = {
        if (hints.nonEmpty) new WithHints(this, hints)
        else this
    }
    private [parsley] final override def withReason(reason: String, offset: Int): TrivialDefuncError = {
        if (this.presentationOffset == offset) new WithReason(this, reason)
        else this
    }
    private [machine] final override def label(labels: Iterable[String], offset: Int): TrivialDefuncError = {
        if (this.presentationOffset == offset) new WithLabel(this, labels)
        else this
    }
    private [machine] final override def amend(partial: Boolean, presentationOffset: Int, line: Int, col: Int): TrivialDefuncError = {
        if (!this.entrenched) new TrivialAmended(presentationOffset, if (partial) this.underlyingOffset else presentationOffset, line, col, this)
        else this
    }
    private [machine] final override def entrench: TrivialDefuncError = this match {
        case self: TrivialEntrenched => new TrivialEntrenched(self.by + 1, self.err)
        case self => new TrivialEntrenched(1, self)
    }
    private [machine] final override def dislodge(by: Int): TrivialDefuncError = this match {
        case self: TrivialEntrenched if by == self.by => self.err
        case self if !self.entrenched => self
        case self: TrivialDislodged => new TrivialDislodged(self.by + by, self.err)
        case self => new TrivialDislodged(by, self)
    }
    private [machine] final override def markAsLexical(offset: Int): TrivialDefuncError = {
        if (Integer.compareUnsigned(this.presentationOffset, offset) > 0) new TrivialLexical(this)
        else this
    }
}

/** Represents partially evaluated fancy errors */
private [errors] sealed abstract class FancyDefuncError extends DefuncError {
    private [machine] final override def asParseError(implicit itemBuilder: ErrorItemBuilder): FancyError = {
        val builder = new FancyErrorBuilder(presentationOffset)
        makeFancy(builder)
        builder.mkError
    }
    private [errors] def makeFancy(builder: FancyErrorBuilder): Unit

    private [machine] final override def merge(err: DefuncError): DefuncError = {
        val cmp = Integer.compareUnsigned(this.underlyingOffset, err.underlyingOffset)
        if (cmp > 0) this
        else if (cmp < 0) err
        else {
            val cmp = Integer.compareUnsigned(this.presentationOffset, err.presentationOffset)
            if (cmp > 0) this
            else if (cmp < 0) err
            else err match {
                case err: FancyDefuncError                    => new FancyMergedErrors(this, err)
                case err: TrivialDefuncError if flexibleCaret => new FancyAdjustCaret(this, err)
                case _                                        => this
            }
        }
    }

    private [machine] final override def withHints(hints: DefuncHints): FancyDefuncError = this
    private [parsley] final override def withReason(reason: String, offset: Int): FancyDefuncError = this
    private [machine] final override def label(labels: Iterable[String], offset: Int): FancyDefuncError = this
    private [machine] final override def amend(partial: Boolean, presentationOffset: Int, line: Int, col: Int): FancyDefuncError = {
        if (!this.entrenched) new FancyAmended(presentationOffset, if (partial) this.underlyingOffset else presentationOffset, line, col, this)
        else this
    }
    private [machine] final override def entrench: FancyDefuncError = this match {
        case self: FancyEntrenched => new FancyEntrenched(self.by + 1, self.err)
        case self => new FancyEntrenched(1, self)
    }
    private [machine] final override def dislodge(by: Int): FancyDefuncError = this match {
        case self: FancyEntrenched if by == self.by => self.err
        case self if !self.entrenched => self
        case self: FancyDislodged => new FancyDislodged(self.by + by, self.err)
        case self => new FancyDislodged(by, self)
    }
    private [machine] final override def markAsLexical(offset: Int): FancyDefuncError = this
}

private [errors] sealed abstract class TrivialTransitive extends TrivialDefuncError {
    val err: TrivialDefuncError
}
/** This is the common supertype of all "regular" trivial errors: those that result from failures as opposed to operations on errors. */
private [errors] sealed abstract class BaseError extends TrivialDefuncError {
    /** The line number the error occurred at */
    private [errors] val line: Int
    /** The column number the error occurred at */
    private [errors] val col: Int
    def underlyingOffset: Int = presentationOffset
    /** The size of the unexpected token demanded by this error */
    private [errors] def unexpectedWidth: Int
    /** The error items produced by this error */
    private [errors] def expected: Iterable[ExpectItem]
    // private [errors] def expected: IterableOnce[ErrorItem] // TODO: when 2.12 is dropped this will work better

    /** Adds the reasons and errors (or any other work) after the position and unexpected updates.
      * By default, this will just add the expected messages found in the `expectedIterable`.
      *
      * @note the default can, and should, be overriden for efficiency or to add more information
      */
    private [errors] def addLabelsAndReasons(builder: TrivialErrorBuilder): Unit = builder ++= expected

    /** Default implementation of `makeTrivial`, which updates the position of the error, and the
      * size of the unexpected token. This calls `addLabelsAndReasons` to complete any work.
      *
      * @note the default implementation should be overriden for EmptyErrors, which must not update the unexpected!
      */
    private [errors] override def makeTrivial(builder: TrivialErrorBuilder): Unit = {
        builder.pos_=(line, col)
        builder.updateUnexpected(unexpectedWidth)
        addLabelsAndReasons(builder)
    }
}

private [parsley] final class ExpectedError(val presentationOffset: Int, val line: Int, val col: Int,
                                            val expected: Iterable[ExpectItem], val unexpectedWidth: Int) extends BaseError {
    override final val flags = if (expected.isEmpty) (DefuncError.ExpectedEmptyMask | DefuncError.TrivialErrorMask) else DefuncError.TrivialErrorMask
}
private [machine] final class ExpectedErrorWithReason(val presentationOffset: Int, val line: Int, val col: Int,
                                                      val expected: Iterable[ExpectItem], val reason: String, val unexpectedWidth: Int) extends BaseError {
    override final val flags = if (expected.isEmpty) (DefuncError.ExpectedEmptyMask | DefuncError.TrivialErrorMask) else DefuncError.TrivialErrorMask
    override def addLabelsAndReasons(builder: TrivialErrorBuilder): Unit = {
        builder ++= expected
        builder += reason
    }
}
private [parsley] final class UnexpectedError(val presentationOffset: Int, val line: Int, val col: Int, val expected: Iterable[ExpectItem],
                                              val unexpected: UnexpectDesc) extends BaseError {
    override final val flags = if (expected.isEmpty) (DefuncError.ExpectedEmptyMask | DefuncError.TrivialErrorMask) else DefuncError.TrivialErrorMask
    override private [errors] def unexpectedWidth: Int = unexpected.width.width
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = {
        builder.pos_=(line, col)
        builder ++= expected
        builder.updateUnexpected(unexpected)
    }
}

private [parsley] final class ClassicFancyError(val presentationOffset: Int, val line: Int, val col: Int, caretWidth: CaretWidth, val msgs: String*)
    extends FancyDefuncError {
    def underlyingOffset: Int = presentationOffset
    override final val flags =
        if (caretWidth.isFlexible) DefuncError.ExpectedEmptyMask | DefuncError.FlexibleCaretMask else DefuncError.ExpectedEmptyMask
    override def makeFancy(builder: FancyErrorBuilder): Unit = {
        builder.pos_=(line, col)
        builder ++= msgs
        builder.updateCaretWidth(caretWidth)
    }
}
private [parsley] final class EmptyError(val presentationOffset: Int, val line: Int, val col: Int, val unexpectedWidth: Int) extends BaseError {
    override final val flags = DefuncError.ExpectedEmptyMask | DefuncError.TrivialErrorMask
    override def expected: Iterable[ExpectItem] = None
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = {
        builder.pos_=(line, col)
        builder.updateEmptyUnexpected(unexpectedWidth)
    }
}
// TODO: remove
private [parsley] final class EmptyErrorWithReason(val presentationOffset: Int, val line: Int, val col: Int, val reason: String, val unexpectedWidth: Int)
    extends BaseError {
    override final val flags = DefuncError.ExpectedEmptyMask | DefuncError.TrivialErrorMask
    override def expected: Iterable[ExpectItem] = None
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = {
        builder.pos_=(line, col)
        builder.updateEmptyUnexpected(unexpectedWidth)
        builder += reason
    }
}

private [errors] final class TrivialMergedErrors private [errors] (val err1: TrivialDefuncError, val err2: TrivialDefuncError) extends TrivialDefuncError {
    // FIXME: this is horrid, split out the flags at this point, we'll do 16-bit and 8-bit
    override final val flags = scala.math.max(err1.entrenchedBy, err2.entrenchedBy) |
                               (err1.flags & err2.flags & ~DefuncError.EntrenchedMask)
    assume(err1.underlyingOffset == err2.underlyingOffset, "two errors only merge when they have matching offsets")
    override val underlyingOffset = err1.underlyingOffset
    assume(err1.presentationOffset == err2.presentationOffset, "two errors only merge when they have matching offsets")
    override val presentationOffset = err1.presentationOffset //Math.max(err1.offset, err2.offset)
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = {
        err1.makeTrivial(builder)
        err2.makeTrivial(builder)
    }
}

private [errors] final class FancyMergedErrors private [errors] (val err1: FancyDefuncError, val err2: FancyDefuncError) extends FancyDefuncError {
    // FIXME: this is horrid, split out the flags at this point, we'll do 16-bit and 8-bit
    override final val flags = scala.math.max(err1.entrenchedBy, err2.entrenchedBy) |
                               (err1.flags & err2.flags & ~DefuncError.EntrenchedMask)
    assume(err1.underlyingOffset == err2.underlyingOffset, "two errors only merge when they have matching offsets")
    override val underlyingOffset = err1.underlyingOffset
    assume(err1.presentationOffset == err2.presentationOffset, "two errors only merge when they have matching offsets")
    override val presentationOffset = err1.presentationOffset //Math.max(err1.offset, err2.offset)
    override def makeFancy(builder: FancyErrorBuilder): Unit = {
        err1.makeFancy(builder)
        err2.makeFancy(builder)
    }
}

private [errors] final class FancyAdjustCaret private [errors] (val err: FancyDefuncError, val caretAdjuster: TrivialDefuncError) extends FancyDefuncError {
    override final val flags = err.flags
    assume(err.presentationOffset == caretAdjuster.presentationOffset, "two errors only merge when they have matching offsets")
    override val presentationOffset = err.presentationOffset
    override val underlyingOffset = err.underlyingOffset
    override def makeFancy(builder: FancyErrorBuilder): Unit = {
        err.makeFancy(builder)
        caretAdjuster.adjustCaret(builder)
    }
}

private [errors] final class WithHints private [errors] (val err: TrivialDefuncError, val hints: DefuncHints) extends TrivialTransitive {
    assume(!hints.isEmpty, "WithHints will always have non-empty hints")
    override final val flags = err.flags & ~DefuncError.ExpectedEmptyMask //err.isExpectedEmpty && hints.isEmpty
    override val presentationOffset = err.presentationOffset
    override val underlyingOffset = err.underlyingOffset
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = {
        err.makeTrivial(builder)
        builder.whenAcceptingExpected {
            for (size <- hints.updateExpectedsAndGetSize(builder)) builder.updateUnexpected(size)
        }
    }
}

private [errors] final class WithReason private [errors] (val err: TrivialDefuncError, val reason: String) extends TrivialTransitive {
    override final val flags = err.flags
    override val presentationOffset = err.presentationOffset
    override val underlyingOffset = err.underlyingOffset
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = {
        err.makeTrivial(builder)
        builder += reason
    }
}

private [errors] final class WithLabel private [errors] (val err: TrivialDefuncError, val labels: Iterable[String]) extends TrivialTransitive {
    override final val flags = {
        if (labels.isEmpty) err.flags |  DefuncError.ExpectedEmptyMask
        else                err.flags & ~DefuncError.ExpectedEmptyMask
    }
    override val presentationOffset = err.presentationOffset
    override val underlyingOffset = err.underlyingOffset
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = {
        builder.ignoreExpected {
            err.makeTrivial(builder)
        }
        builder ++= labels.map(new ExpectDesc(_))
    }
}

private [errors] final class TrivialAmended private [errors](val presentationOffset: Int, val underlyingOffset: Int, val line: Int, val col: Int,
                                                             val err: TrivialDefuncError) extends TrivialTransitive {
    assume(!err.entrenched, "an amendment will only occur on unentrenched errors")
    override final val flags = err.flags
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = {
        err.makeTrivial(builder)
        // this should happen after the sub-error has been proceeded
        builder.pos_=(line, col)
    }
}

private [errors] final class FancyAmended private [errors](val presentationOffset: Int, val underlyingOffset: Int, val line: Int, val col: Int,
                                                           val err: FancyDefuncError) extends FancyDefuncError {
    assume(!err.entrenched, "an amendment will only occur on unentrenched errors")
    override final val flags = err.flags
    override def makeFancy(builder: FancyErrorBuilder): Unit = {
        err.makeFancy(builder)
        builder.pos_=(line, col)
    }
}

private [errors] final class TrivialEntrenched private [errors] (val by: Int, val err: TrivialDefuncError) extends TrivialTransitive {
    assume((DefuncError.EntrenchedMask & 1) == 1, "the entrenchment is the least significant bits of the flag")
    override final val flags = err.flags + by//| DefuncError.EntrenchedMask
    assert((flags & ~DefuncError.EntrenchedMask) == (err.flags & ~DefuncError.EntrenchedMask), "entrench should not affect any other flags")
    override val presentationOffset = err.presentationOffset
    override val underlyingOffset = err.underlyingOffset
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = err.makeTrivial(builder)
}

private [errors] final class TrivialDislodged private [errors] (val by: Int, val err: TrivialDefuncError) extends TrivialTransitive {
    assume(err.entrenched, "an dislodge will only occur on unentrenched errors")
    assume((DefuncError.EntrenchedMask & 1) == 1, "the entrenchment is the least significant bits of the flag")
    override final val flags = if (err.entrenchedBy > by) err.flags - by else err.flags & ~DefuncError.EntrenchedMask
    assert((flags & ~DefuncError.EntrenchedMask) == (err.flags & ~DefuncError.EntrenchedMask), "dislodge should not affect any other flags")
    override val presentationOffset = err.presentationOffset
    override val underlyingOffset = err.underlyingOffset
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = err.makeTrivial(builder)
}

private [errors] final class FancyEntrenched private [errors] (val by: Int, val err: FancyDefuncError) extends FancyDefuncError {
    assume((DefuncError.EntrenchedMask & 1) == 1, "the entrenchment is the least significant bits of the flag")
    override final val flags = err.flags + by//| DefuncError.EntrenchedMask
    assert((flags & ~DefuncError.EntrenchedMask) == (err.flags & ~DefuncError.EntrenchedMask), "entrench should not affect any other flags")
    override val presentationOffset = err.presentationOffset
    override val underlyingOffset = err.underlyingOffset
    override def makeFancy(builder: FancyErrorBuilder): Unit = err.makeFancy(builder)
}

private [errors] final class FancyDislodged private [errors] (val by: Int, val err: FancyDefuncError) extends FancyDefuncError {
    assume(err.entrenched, "an dislodge will only occur on unentrenched errors")
    assume((DefuncError.EntrenchedMask & 1) == 1, "the entrenchment is the least significant bits of the flag")
    override final val flags = if (err.entrenchedBy > by) err.flags - by else err.flags & ~DefuncError.EntrenchedMask
    assert((flags & ~DefuncError.EntrenchedMask) == (err.flags & ~DefuncError.EntrenchedMask), "dislodge should not affect any other flags")
    override val presentationOffset = err.presentationOffset
    override val underlyingOffset = err.underlyingOffset
    override def makeFancy(builder: FancyErrorBuilder): Unit = err.makeFancy(builder)
}

private [errors] final class TrivialLexical private [errors] (val err: TrivialDefuncError) extends TrivialTransitive {
    override final val flags = err.flags | DefuncError.LexicalErrorMask
    override val presentationOffset = err.presentationOffset
    override val underlyingOffset = err.underlyingOffset
    override def makeTrivial(builder: TrivialErrorBuilder): Unit = err.makeTrivial(builder)
}
