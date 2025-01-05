/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.expr

import parsley.Parsley

/** This class allows for the description of a single layer of operators in the precedence tree.
  *
  * Describes the operators at a specific level in the precedence tree, such that these ops
  * consume `B`s, possibly `A`s and produce `B`s: this depends on the [[Fixity]] of the operators.
  *
  * @tparam A the base type consumed by the operators.
  * @tparam B the type produced/consumed by the operators.
  * @note for less complex types `Ops[A, A]` is sufficient.
  * @since 2.2.0
  * @group Table
  */
sealed abstract class Ops[-A, B] {
    private [expr] def chain(p: Parsley[A]): Parsley[B]
}

/** This helper object is used to build values of `Ops[A, A]`, for homogeneous precedence parsing.
  *
  * @since 2.2.0
  * @group Builders
  */
object Ops {
    /** This function builds an `Ops` object representing many operators found at the same precedence level, with a given fixity.
      *
      * The operators found on the level constructed by this function are homogeneous: the type of the level below must
      * match the types of values produced at this level.
      *
      * Using path-dependent typing, the given fixity describes the shape of the operators expected. For more information see
      * [[https://github.com/j-mie6/Parsley/wiki/Building-Expression-Parsers the Parsley wiki]].
      *
      * @tparam A the type associated with the operators (which it consumes and produces)
      * @param fixity the fixity of the operators described.
      * @param op0 The first operator.
      * @param ops The operators themselves, in varargs.
      * @see [[Fixity `Fixity`]]
      * @note currently a bug in scaladoc incorrect displays this functions type, it should be: `fixity.Op[A, A]`, NOT `Op[A, A]`.
      * @since 2.2.0
      */
    def apply[A](fixity: Fixity)(op0: Parsley[fixity.Op[A, A]], ops: Parsley[fixity.Op[A, A]]*): Ops[A, A] = GOps[A, A](fixity)(op0, ops: _*)

    private [expr] def apply[A, B](fixity: Fixity)(op: Parsley[fixity.Op[A, B]])(implicit wrap: A => B): Ops[A, B] = new Ops[A, B] {
        private [expr] def chain(p: Parsley[A]): Parsley[B] = fixity.chain(p, op)
    }
}
