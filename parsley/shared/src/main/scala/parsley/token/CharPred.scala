/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token

import parsley.Parsley, Parsley.empty
import parsley.character.satisfy
import parsley.exceptions.ParsleyException
import parsley.unicode.{satisfy => satisfyUtf16}

import scala.collection.immutable.NumericRange

/** Base class for character predicates.
  * @since 4.0.0
  */
sealed abstract class CharPred {
    private [token] def toBmp: Parsley[Char]
    private [token] def toUnicode: Parsley[Int]
    private [token] def toNative: Parsley[Unit]
    private [token] def startsWith(s: String): Boolean
    private [token] def endsWith(s: String): Boolean
    private [parsley] def asInternalPredicate: parsley.internal.machine.instructions.token.CharPredicate
}

/** More generic character predicate, which reads any unicode codepoint.
  *
  * Full unicode characters can be up to 24-bits, which is handled by a
  * 32-bit number on the JVM. This predicate can be used, therefore, to
  * handle any single unicode codepoint: this excludes multi-codepoint
  * characters like flags, or modified emojis.
  *
  * In Scala, characters can be upcast to integers, so still can be used
  * in the description of this predicate.
  *
  * @since 4.0.0
  */
final case class Unicode(predicate: Int => Boolean) extends CharPred {
    def this(c: Int) = this(_ == c)
    def this(cs: NumericRange[Int]) = this(cs.contains(_))
    def this(cs: Range) = this(cs.contains(_))
    private [token] override def toBmp = satisfy(c => predicate(c.toInt))
    private [token] override def toUnicode = satisfyUtf16(predicate)
    private [token] override def toNative = toUnicode.void
    private [token] def startsWith(s: String) = s.nonEmpty && predicate(s.codePointAt(0))
    private [token] def endsWith(s: String) = s.nonEmpty && predicate(s.codePointBefore(s.length))
    private [parsley] def asInternalPredicate = new parsley.internal.machine.instructions.token.Unicode(predicate)
}
object Unicode {
    /** Lifts a regular full-width character predicate.
      * @since 5.0.0
      */
    def apply(c: Int): Unicode = new Unicode(c)
    /** Constructs a predicate for anything in a range of specific unicode codepoints.
      * @since 5.0.0
      */
    def apply(cs: NumericRange[Int]): Unicode = new Unicode(cs)
    /** Constructs a predicate for anything in a range of specific unicode codepoints.
      * @since 5.0.0
      */
    def apply(cs: Range): Unicode = new Unicode(cs)

    /** Lifts a regular character predicate.
      * @since 5.0.0
      */
    def char(pred: Char => Boolean): Unicode = new Unicode(c => c.isValidChar && pred(c.toChar))
    /** Constructs a predicate for the specific given character.
      * @since 5.0.0
      */
    def char(c: Char): Unicode = new Unicode(c.toInt)
    /** Constructs a predicate for anything in a range of specific characters.
      * @since 5.0.0
      */
    def char(cs: NumericRange[Char]): Unicode = char(cs.contains(_))
}

/** Basic character predicate, which reads regular Scala 16-bit characters.
  *
  * This predicate is only capable of recognising characters within the
  * Basic Multilingual Plane.
  *
  * @since 4.0.0
  */
final case class Basic(predicate: Char => Boolean) extends CharPred {
    def this(c: Char) = this(_ == c)
    def this(cs: NumericRange[Char]) = this(cs.contains(_))
    private [token] override def toBmp = satisfy(predicate)
    // $COVERAGE-OFF$
    private [token] override def toUnicode =
        throw new ParsleyException("Cannot parse unicode with a `Basic` `Char => Boolean` predicate") // scalastyle:ignore throw
    // $COVERAGE-ON$
    private [token] override def toNative = toBmp.void
    private [token] def startsWith(s: String) = s.headOption.exists(predicate)
    private [token] def endsWith(s: String) = s.lastOption.exists(predicate)
    private [parsley] def asInternalPredicate = new parsley.internal.machine.instructions.token.Basic(predicate)
}
object Basic {
    /** Constructs a predicate for the specific given character.
      * @since 5.0.0
      */
    def apply(c: Char): Basic = new Basic(c)
    /** Constructs a predicate for anything in a range of specific characters.
      * @since 5.0.0
      */
    def apply(cs: NumericRange[Char]): Basic = new Basic(cs)
}

/** Character predicate that never succeeds.
  *
  * @since 4.0.0
  */
case object NotRequired extends CharPred {
    private [token] override def toBmp = empty
    private [token] override def toUnicode = empty
    private [token] override def toNative = empty
    private [token] def startsWith(s: String) = true
    private [token] def endsWith(s: String) = true
    private [parsley] def asInternalPredicate = parsley.internal.machine.instructions.token.NotRequired
}
