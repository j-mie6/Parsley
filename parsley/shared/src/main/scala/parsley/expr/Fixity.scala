/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.expr

import parsley.Parsley

/**
  * Denotes the fixity and associativity of an operator. Importantly, it also specifies the type of the
  * of the operations themselves.
  * @since 4.0.0
  * @group Fixities
  */
sealed trait Fixity {
    type Op[A, B]
    private [expr] def chain[A, B](p: Parsley[A], op: Parsley[Op[A, B]])(implicit wrap: A => B): Parsley[B]
}

/**
  * Describes left-associative binary operators
  * @since 4.0.0
  * @group Fixities
  */
case object InfixL extends Fixity {
    override type Op[-A, B] = (B, A) => B
    private [expr] def chain[A, B](p: Parsley[A], op: Parsley[Op[A, B]])(implicit wrap: A => B): Parsley[B] = infix.left1(p)(op)
}

/**
  * Describes right-associative binary operators
  * @since 4.0.0
  * @group Fixities
  */
case object InfixR extends Fixity {
    override type Op[-A, B] = (A, B) => B
    private [expr] def chain[A, B](p: Parsley[A], op: Parsley[Op[A, B]])(implicit wrap: A => B): Parsley[B] = infix.right1(p)(op)
}

/**
  * Describes unary prefix operators
  * @since 4.0.0
  * @group Fixities
  */
case object Prefix extends Fixity {
    override type Op[A, B] = B => B
    private [expr] def chain[A, B](p: Parsley[A], op: Parsley[Op[A, B]])(implicit wrap: A => B): Parsley[B] = infix.prefix(p)(op)
}

/**
  * Describes unary postfix operators
  * @since 4.0.0
  * @group Fixities
  */
case object Postfix extends Fixity {
    override type Op[A, B] = B => B
    private [expr] def chain[A, B](p: Parsley[A], op: Parsley[Op[A, B]])(implicit wrap: A => B): Parsley[B] = infix.postfix(p)(op)
}

/**
  * Describes non-associative operators
  * @since 4.0.0
  * @group Fixities
  */
case object InfixN extends Fixity {
    override type Op[-A, +B] = (A, A) => B
    private [expr] def chain[A, B](p: Parsley[A], op: Parsley[Op[A, B]])(implicit wrap: A => B): Parsley[B] = infix.nonassoc(p)(op)
}
