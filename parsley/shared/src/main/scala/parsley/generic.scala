/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import lift._
import parsley.errors.combinator._

/** This module contains the definition of 23 basic ''generic parser bridge traits'', which
  * are used to implement the ''Parser Bridge'' pattern for types that do not require metadata.
  *
  * The traits within are designed to be extended by the companion object of some case class that
  * is produced as the result of a parser: by using these traits, it enables a new `apply` method
  * that makes it appear like the constructor is applied to the parsers themselves. This can be
  * very useful for performing extra verification on the produced results, or to incorporate metadata
  * into the result. Specifically, these traits are designed to be the bare-minimum functionaity,
  * and do not interact with any metadata.
  *
  * @since 4.5.0
  *
  * @define bridgefor
  *     Generic bridge trait for types that have constructors of arity
  */
object generic {
    // $COVERAGE-OFF$
    // scalastyle:off parameter.number ensure.single.space.after.token
    /** This generically allows for the tagging of parsers via its `error` combinator:
      * as it happens, both labels and reasons are very often attached to bridges.
      *
      * @since 5.0.0
      */
    trait ErrorBridge {
        /** The labels that should be associated with a failure to parse this bridge.
          *
          * The default, `Nil`, will not affect the labelling of the original error.
          */
        def labels: List[String] = Nil
        /** The reason that should be associated with a failure to parse this bridge.
          *
          * The default, `None`, will not add any reasons.
          */
        def reason: Option[String] = None

        /** Applies the error components described by `labels` and `reason` to the given
          * parser.
          *
          * @note this should be used within a bridge's apply and the `from` combinator.
          */
        protected final def error[T](p: Parsley[T]): Parsley[T] = applyReason(applyLabels(p))
        private def applyLabels[T](p: Parsley[T]): Parsley[T] = labels match {
            case Nil => p
            case l0 :: ls => p.label(l0, ls: _*).ut()
        }
        private def applyReason[T](p: Parsley[T]): Parsley[T] = reason.foldLeft(p)(_.explain(_).ut())
    }

    /** Generic bridge trait enabling the `<#`/`from` combinator on this type:
      * this is useful when the constructor is not applied immediately,
      * like when using `precedence`. It does not track any metadata.
      *
      * @since 4.0.0
      */
    trait ParserSingletonBridge[+A] extends ErrorBridge {
        /** The abstract hook method: what value is the singleton representing?
          * @since 4.0.0
          */
        protected def con: A
        /** The syntax on this implementing type that performs the parser and
          * returns `con`.
          *
          * @param op the parser that should be parsed before returning `con`.
          * @note equivalent to `from`.
          */
        final def <#(op: Parsley[_]): Parsley[A] = this.from(op).uo(s"$this <#")
        /** The combinator on this implementing type that performs the parser and
          * returns `con`.
          *
          * @param op the parser that should be parsed before returning `con`.
          */
        infix final def from(op: Parsley[_]): Parsley[A] = error(op.as(con).ut()).uo(s"$this.from")
    }

    /** Generic bridge trait for singleton objects that simply return themselves
      * after running the parser provided to `from`.
      *
      * @since 4.0.0
      */
    trait ParserBridge0[+R] extends ParserSingletonBridge[R] { this: R =>
        /** @inheritdoc */
        override final def con: R = this
    }

    /** $bridgefor 1. */
    trait ParserBridge1[-T1, +R] extends ParserSingletonBridge[T1 => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1: Parsley[T1]): Parsley[R] = error(lift1(this.con, x1).ut()).uo(this.toString)
        /** @inheritdoc */
        override final def con: T1 => R = this.apply(_)
    }

    /** $bridgefor 2. */
    trait ParserBridge2[-T1, -T2, +R] extends ParserSingletonBridge[(T1, T2) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1: Parsley[T1], x2: =>Parsley[T2]): Parsley[R] = error(lift2(this.con, x1, x2).ut()).uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2) => R = this.apply(_, _)
    }

    /** $bridgefor 3. */
    trait ParserBridge3[-T1, -T2, -T3, +R] extends ParserSingletonBridge[(T1, T2, T3) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1: Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3]): Parsley[R] = error(lift3(this.con, x1, x2, x3).ut()).uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3) => R = this.apply(_, _, _)
    }

    /** $bridgefor 4. */
    trait ParserBridge4[-T1, -T2, -T3, -T4, +R] extends ParserSingletonBridge[(T1, T2, T3, T4) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1: Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4: =>Parsley[T4]): Parsley[R] = {
            error(lift4(this.con, x1, x2, x3, x4).ut()).uo(this.toString)
        }
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4) => R = this.apply(_, _, _, _)
    }

    /** $bridgefor 5. */
    trait ParserBridge5[-T1, -T2, -T3, -T4, -T5, +R] extends ParserSingletonBridge[(T1, T2, T3, T4, T5) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1: Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4: =>Parsley[T4], x5: =>Parsley[T5]): Parsley[R] = error {
            lift5(this.con, x1, x2, x3, x4, x5).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5) => R = this.apply(_, _, _, _, _)
    }

    /** $bridgefor 6. */
    trait ParserBridge6[-T1, -T2, -T3, -T4, -T5, -T6, +R] extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1: Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4: =>Parsley[T4], x5: =>Parsley[T5], x6: =>Parsley[T6]): Parsley[R] = error {
            lift6(this.con, x1, x2, x3, x4, x5, x6).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6) => R = this.apply(_, _, _, _, _, _)
    }

    /** $bridgefor 7. */
    trait ParserBridge7[-T1, -T2, -T3, -T4, -T5, -T6, -T7, +R] extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6, x7: T7): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:   Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4: =>Parsley[T4], x5: =>Parsley[T5], x6: =>Parsley[T6],
                  x7: =>Parsley[T7]): Parsley[R] = error {
            lift7(this.con, x1, x2, x3, x4, x5, x6, x7).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7) => R = this.apply(_, _, _, _, _, _, _)
    }

    /** $bridgefor 8. */
    trait ParserBridge8[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, +R] extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6, x7: T7, x8: T8): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:   Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4: =>Parsley[T4], x5: =>Parsley[T5], x6: =>Parsley[T6],
                  x7: =>Parsley[T7], x8: =>Parsley[T8]): Parsley[R] = error {
            lift8(this.con, x1, x2, x3, x4, x5, x6, x7, x8).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8) => R = this.apply(_, _, _, _, _, _, _, _)
    }

    /** $bridgefor 9. */
    trait ParserBridge9[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, +R] extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6, x7: T7, x8: T8, x9: T9): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:   Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4: =>Parsley[T4], x5: =>Parsley[T5], x6: =>Parsley[T6],
                  x7: =>Parsley[T7], x8: =>Parsley[T8], x9: =>Parsley[T9]): Parsley[R] = error {
            lift9(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9) => R = this.apply(_, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 10. */
    trait ParserBridge10[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, +R] extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6, x7: T7, x8: T8, x9: T9, x10: T10): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:   Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4:  =>Parsley[T4], x5: =>Parsley[T5], x6: =>Parsley[T6],
                  x7: =>Parsley[T7], x8: =>Parsley[T8], x9: =>Parsley[T9], x10: =>Parsley[T10]): Parsley[R] = error {
            lift10(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => R = this.apply(_, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 11. */
    trait ParserBridge11[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6, x7: T7, x8: T8, x9: T9, x10: T10, x11: T11): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:   Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4:  =>Parsley[T4],  x5: =>Parsley[T5], x6: =>Parsley[T6],
                  x7: =>Parsley[T7], x8: =>Parsley[T8], x9: =>Parsley[T9], x10: =>Parsley[T10], x11: =>Parsley[T11]): Parsley[R] = error {
            lift11(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => R = this.apply(_, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 12. */
    trait ParserBridge12[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6, x7: T7, x8: T8, x9: T9, x10: T10, x11: T11, x12: T12): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:   Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4:  =>Parsley[T4],  x5: =>Parsley[T5],   x6:  =>Parsley[T6],
                  x7: =>Parsley[T7], x8: =>Parsley[T8], x9: =>Parsley[T9], x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12]): Parsley[R] = error {
            lift12(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => R = this.apply(_, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 13. */
    trait ParserBridge13[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6, x7: T7, x8: T8, x9: T9, x10: T10, x11: T11, x12: T12, x13: T13): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1], x2: =>Parsley[T2], x3: =>Parsley[T3], x4:  =>Parsley[T4],  x5: =>Parsley[T5],   x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7], x8: =>Parsley[T8], x9: =>Parsley[T9], x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13]): Parsley[R] = error {
            lift13(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => R = this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 14. */
    trait ParserBridge14[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, -T14, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6, x7: T7, x8: T8, x9: T9, x10: T10, x11: T11, x12: T12, x13: T13, x14: T14): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1],  x2:  =>Parsley[T2], x3: =>Parsley[T3], x4:  =>Parsley[T4],  x5:  =>Parsley[T5],  x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7],  x8:  =>Parsley[T8], x9: =>Parsley[T9], x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13], x14: =>Parsley[T14]): Parsley[R] = error {
            lift14(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => R = this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 15. */
    trait ParserBridge15[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, -T14, -T15, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1: T1, x2: T2, x3: T3, x4: T4, x5: T5, x6: T6, x7: T7, x8: T8, x9: T9, x10: T10, x11: T11, x12: T12, x13: T13, x14: T14, x15: T15): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1],  x2:  =>Parsley[T2],  x3:  =>Parsley[T3], x4:  =>Parsley[T4],  x5:  =>Parsley[T5],  x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7],  x8:  =>Parsley[T8],  x9:  =>Parsley[T9], x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13], x14: =>Parsley[T14], x15: =>Parsley[T15]): Parsley[R] = error {
            lift15(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => R =
            this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 16. */
    trait ParserBridge16[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, -T14, -T15, -T16, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1:  T1,  x2:  T2,  x3:  T3,  x4:  T4,  x5:  T5, x6: T6, x7: T7, x8: T8, x9: T9, x10: T10, x11: T11,
                  x12: T12, x13: T13, x14: T14, x15: T15, x16: T16): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1],  x2:  =>Parsley[T2],  x3:  =>Parsley[T3],  x4:  =>Parsley[T4],  x5: =>Parsley[T5],   x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7],  x8:  =>Parsley[T8],  x9:  =>Parsley[T9],  x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13], x14: =>Parsley[T14], x15: =>Parsley[T15], x16: =>Parsley[T16]): Parsley[R] = error {
            lift16(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => R =
            this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 17. */
    trait ParserBridge17[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, -T14, -T15, -T16, -T17, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1:  T1,  x2:  T2,  x3:  T3,  x4:  T4,  x5:  T5,  x6: T6, x7: T7, x8: T8, x9: T9, x10: T10, x11: T11,
                  x12: T12, x13: T13, x14: T14, x15: T15, x16: T16, x17: T17): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1],  x2:  =>Parsley[T2],  x3:  =>Parsley[T3],  x4:  =>Parsley[T4],  x5:  =>Parsley[T5],  x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7],  x8:  =>Parsley[T8],  x9:  =>Parsley[T9],  x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13], x14: =>Parsley[T14], x15: =>Parsley[T15], x16: =>Parsley[T16], x17: =>Parsley[T17]): Parsley[R] = error {
            lift17(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16, x17).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => R =
            this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 18. */
    trait ParserBridge18[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, -T14, -T15, -T16, -T17, -T18, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1:  T1,  x2:  T2,  x3:  T3,  x4:  T4,  x5:  T5,  x6: T6,   x7: T7, x8: T8, x9: T9, x10: T10, x11: T11,
                  x12: T12, x13: T13, x14: T14, x15: T15, x16: T16, x17: T17, x18: T18): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1],  x2:  =>Parsley[T2],  x3:  =>Parsley[T3],  x4:  =>Parsley[T4],  x5:  =>Parsley[T5],  x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7],  x8:  =>Parsley[T8],  x9:  =>Parsley[T9],  x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13], x14: =>Parsley[T14], x15: =>Parsley[T15], x16: =>Parsley[T16], x17: =>Parsley[T17], x18: =>Parsley[T18]): Parsley[R] =
            error(lift18(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16, x17, x18).ut()).uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => R =
            this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 19. */
    trait ParserBridge19[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, -T14, -T15, -T16, -T17, -T18, -T19, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1:  T1,  x2:  T2,  x3:  T3,  x4:  T4,  x5:  T5,  x6: T6,   x7: T7,   x8: T8, x9: T9, x10: T10, x11: T11,
                  x12: T12, x13: T13, x14: T14, x15: T15, x16: T16, x17: T17, x18: T18, x19: T19): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1],  x2:  =>Parsley[T2],  x3:  =>Parsley[T3],  x4:  =>Parsley[T4],  x5:  =>Parsley[T5],  x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7],  x8:  =>Parsley[T8],  x9:  =>Parsley[T9],  x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13], x14: =>Parsley[T14], x15: =>Parsley[T15], x16: =>Parsley[T16], x17: =>Parsley[T17], x18: =>Parsley[T18],
                  x19: =>Parsley[T19]): Parsley[R] = error {
            lift19(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16, x17, x18, x19).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => R =
            this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 20. */
    trait ParserBridge20[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, -T14, -T15, -T16, -T17, -T18, -T19, -T20, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1:  T1,  x2:  T2,  x3:  T3,  x4:  T4,  x5:  T5,  x6: T6,   x7: T7,   x8: T8,   x9: T9, x10: T10, x11: T11,
                  x12: T12, x13: T13, x14: T14, x15: T15, x16: T16, x17: T17, x18: T18, x19: T19, x20: T20): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1],  x2:  =>Parsley[T2],  x3:  =>Parsley[T3],  x4:  =>Parsley[T4],  x5:  =>Parsley[T5],  x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7],  x8:  =>Parsley[T8],  x9:  =>Parsley[T9],  x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13], x14: =>Parsley[T14], x15: =>Parsley[T15], x16: =>Parsley[T16], x17: =>Parsley[T17], x18: =>Parsley[T18],
                  x19: =>Parsley[T19], x20: =>Parsley[T20]): Parsley[R] = error {
            lift20(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16, x17, x18, x19, x20).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => R =
            this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 21. */
    trait ParserBridge21[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, -T14, -T15, -T16, -T17, -T18, -T19, -T20, -T21, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1:  T1,  x2:  T2,  x3:  T3,  x4:  T4,  x5:  T5,  x6: T6,   x7: T7,   x8: T8,   x9: T9,   x10: T10, x11: T11,
                  x12: T12, x13: T13, x14: T14, x15: T15, x16: T16, x17: T17, x18: T18, x19: T19, x20: T20, x21: T21): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1],  x2:  =>Parsley[T2],  x3:  =>Parsley[T3],  x4:  =>Parsley[T4],  x5:  =>Parsley[T5],  x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7],  x8:  =>Parsley[T8],  x9:  =>Parsley[T9],  x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13], x14: =>Parsley[T14], x15: =>Parsley[T15], x16: =>Parsley[T16], x17: =>Parsley[T17], x18: =>Parsley[T18],
                  x19: =>Parsley[T19], x20: =>Parsley[T20], x21: =>Parsley[T21]): Parsley[R] = error {
            lift21(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16, x17, x18, x19, x20, x21).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => R =
            this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)
    }

    /** $bridgefor 22. */
    trait ParserBridge22[-T1, -T2, -T3, -T4, -T5, -T6, -T7, -T8, -T9, -T10, -T11, -T12, -T13, -T14, -T15, -T16, -T17, -T18, -T19, -T20, -T21, -T22, +R]
        extends ParserSingletonBridge[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) => R] {
        /** The abstract hook method: this is the method that should be used
          * to combine the results of the parsers provided to the template method
          * into the result type `R`. */
        def apply(x1:  T1,  x2:  T2,  x3:  T3,  x4:  T4,  x5:  T5,  x6: T6,   x7: T7,   x8: T8,   x9: T9,   x10: T10, x11: T11,
                  x12: T12, x13: T13, x14: T14, x15: T15, x16: T16, x17: T17, x18: T18, x19: T19, x20: T20, x21: T21, x22: T22): R
        /** The template method: this is the method that can be used to
          * sequence and combine the results of all the parsers. */
        def apply(x1:    Parsley[T1],  x2:  =>Parsley[T2],  x3:  =>Parsley[T3],  x4:  =>Parsley[T4],  x5:  =>Parsley[T5],  x6:  =>Parsley[T6],
                  x7:  =>Parsley[T7],  x8:  =>Parsley[T8],  x9:  =>Parsley[T9],  x10: =>Parsley[T10], x11: =>Parsley[T11], x12: =>Parsley[T12],
                  x13: =>Parsley[T13], x14: =>Parsley[T14], x15: =>Parsley[T15], x16: =>Parsley[T16], x17: =>Parsley[T17], x18: =>Parsley[T18],
                  x19: =>Parsley[T19], x20: =>Parsley[T20], x21: =>Parsley[T21], x22: =>Parsley[T22]): Parsley[R] = error {
            lift22(this.con, x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16, x17, x18, x19, x20, x21, x22).ut()
        }.uo(this.toString)
        /** @inheritdoc */
        override final def con: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) => R =
            this.apply(_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _)
    }
    // scalastyle:on parameter.number ensure.single.space.after.token
    // $COVERAGE-ON$
}
