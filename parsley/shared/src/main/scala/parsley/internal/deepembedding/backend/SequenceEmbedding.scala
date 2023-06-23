/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.backend

import parsley.XAssert._

import parsley.internal.collection.mutable.DoublyLinkedList
import parsley.internal.deepembedding.ContOps, ContOps.{result, suspend, ContAdapter}
import parsley.internal.deepembedding.frontend
import parsley.internal.deepembedding.singletons._
import parsley.internal.machine.instructions

import StrictParsley.InstrBuffer

// Core Embedding
private [deepembedding] final class <*>[A, B](var left: StrictParsley[A => B], var right: StrictParsley[A]) extends StrictParsley[B] {
    def inlinable: Boolean = false
    // TODO: Refactor
    // FIXME: Needs more interation with .safe
    override def optimise: StrictParsley[B] = (left, right) match {
        // Fusion laws
        case (uf, ux@Pure(x)) if (uf.isInstanceOf[Pure[_]] || uf.isInstanceOf[_ <*> _]) && uf.safe && ux.safe => uf match {
            // first position fusion
            case Pure(f) => new Pure(f(x))
            // second position fusion
            case Pure(f: Function1[t, A => B] @unchecked) <*> (uy/*: StrictParsley[t]*/) => // scalastyle:ignore disallow.space.before.token
                left = new Pure((y: t) => f(y)(x)).asInstanceOf[StrictParsley[A => B]]
                right = uy.asInstanceOf[StrictParsley[A]]
                this
            // third position fusion
            case Pure(f: Function1[t, Function1[u, A => B] @unchecked]) <*>
                 (uy/*: StrictParsley[t]*/) <*> // scalastyle:ignore disallow.space.before.token
                 (uz/*: StrictParsley[u]*/) => // scalastyle:ignore disallow.space.before.token
                left = <*>(new Pure((y: t) => (z: u) => f(y)(z)(x)), uy.asInstanceOf[StrictParsley[t]]).asInstanceOf[StrictParsley[A => B]]
                right = uz.asInstanceOf[StrictParsley[A]]
                this
            // interchange law: u <*> pure y == pure ($y) <*> u == ($y) <$> u (single instruction, so we benefit at code-gen)
            case _ =>
                left = new Pure((f: A => B) => f(x)).asInstanceOf[StrictParsley[A => B]]
                right = uf.asInstanceOf[StrictParsley[A]]
                this
        }
        // functor law: fmap f (fmap g p) == fmap (f . g) p where fmap f p = pure f <*> p from applicative
        case (Pure(f), Pure(g: Function[t, A] @unchecked) <*> (u/*: StrictParsley[t]*/)) => // scalastyle:ignore disallow.space.before.token
            left = new Pure(f.compose(g)).asInstanceOf[StrictParsley[A => B]]
            right = u.asInstanceOf[StrictParsley[A]]
            this
        // TODO: functor law with lift2!
        // right absorption law: mzero <*> p = mzero
        case (z: MZero, _) => z
        /* RE-ASSOCIATION LAWS */
        // re-association law 1: (q *> left) <*> right = q *> (left <*> right)
        case (q *> uf, ux) => *>(q, <*>(uf, ux).optimise)
        case (uf, seq: Seq[A] @unchecked) => seq match {
            // re-association law 2: left <*> (right <* q) = (left <*> right) <* q
            case ux <* v => <*(<*>(uf, ux).optimise, v).optimise
            // re-association law 3: p *> pure x = pure x <* p
            // consequence of re-association law 3: left <*> (q *> pure x) = (left <*> pure x) <* q
            case v *> (ux: Pure[_]) => <*(<*>(uf, ux).optimise, v).optimise
            case _ => this
        }
        // consequence of left zero law and monadic definition of <*>, preserving error properties of left
        case (u, z: MZero) => *>(u, z)
        // interchange law: u <*> pure y == pure ($y) <*> u == ($y) <$> u (single instruction, so we benefit at code-gen)
        case (uf, Pure(x)) =>
            left = new Pure((f: A => B) => f(x)).asInstanceOf[StrictParsley[A => B]]
            right = uf.asInstanceOf[StrictParsley[A]]
            this
        case _ => this
    }
    override def codeGen[Cont[_, _]: ContOps, R](implicit instrs: InstrBuffer, state: CodeGenState): Cont[R, Unit] = left match {
        // pure f <*> p = f <$> p
        case Pure(f) => right match {
            case ct@CharTok(c) => result(instrs += instructions.CharTokFastPerform[Char, B](c, f.asInstanceOf[Char => B], ct.expected))
            case ct@SupplementaryCharTok(c) => result(instrs += instructions.SupplementaryCharTokFastPerform[Int, B](c, f.asInstanceOf[Int => B], ct.expected))
            case st@StringTok(s) => result(instrs += instructions.StringTokFastPerform(s, f.asInstanceOf[String => B], st.expected))
            case _ =>
                suspend(right.codeGen[Cont, R]) |>
                (instrs += instructions.Lift1(f))
        }
        case _ =>
            suspend(left.codeGen[Cont, R]) >>
            suspend(right.codeGen[Cont, R]) |>
            (instrs += instructions.Apply)
    }
    // $COVERAGE-OFF$
    final override def pretty: String = s"(${left.pretty} <*> ${right.pretty})"
    // $COVERAGE-ON$
}

private [deepembedding] final class >>=[A, B](val p: StrictParsley[A], private [>>=] val f: A => frontend.LazyParsley[B]) extends Unary[A, B] {
    override def optimise: StrictParsley[B] = p match {
        // monad law 1: pure x >>= f = f x: unsafe because it might expose recursion
        //case Pure(x) if safe => new Rec(() => f(x))
        // char/string x = char/string x *> pure x and monad law 1
        //case p@CharTok(c) => *>(p, new Rec(() => f(c.asInstanceOf[A]), expected))
        //case p@StringTok(s) => *>(p, new Rec(() => f(s.asInstanceOf[A]), expected))
        // (q *> p) >>= f = q *> (p >>= f)
        //case u *> v => *>(u, >>=(v, f).optimise)
        // monad law 3: (m >>= g) >>= f = m >>= (\x -> g x >>= f) Note: this *could* help if g x ended with a pure, since this would be optimised out!
        //case (m: Parsley[T] @unchecked) >>= (g: (T => A) @unchecked) =>
        //    p = m.asInstanceOf[Parsley[A]]
        //    f = (x: T) => >>=(g(x), f, expected).optimise
        //    this
        // monadplus law (left zero)
        case z: MZero => z
        case _ => this
    }
    override def codeGen[Cont[_, _]: ContOps, R](implicit instrs: InstrBuffer, state: CodeGenState): Cont[R, Unit] = {
        suspend(p.codeGen[Cont, R]) |> {
            instrs += instructions.DynCall[A](x => f(x).demandCalleeSave(state.numRegs).instrs)
        }
    }
    // $COVERAGE-OFF$
    final override def pretty(p: String): String = s"$p.flatMap(?)"
    // $COVERAGE-ON$
}

private [deepembedding] final class Seq[A](private [backend] var before: DoublyLinkedList[StrictParsley[_]],
                                           private [backend] var res: StrictParsley[A],
                                           private [backend] var after: DoublyLinkedList[StrictParsley[_]]) extends StrictParsley[A] {
    def inlinable: Boolean = false

    private def mergeIntoRight(p: StrictParsley[A]): this.type = p match {
        case Seq(rs1, rr, rs2) =>
            before.stealAll(rs1)
            assume(!rr.isInstanceOf[Pure[_]] || rs2.isEmpty, "if rr is pure, then rs2 is empty, which retains normalisation")
            after = rs2
            res = rr
            this
        case _ => this
    }

    private def mergeFromRight(p: Seq[_], into: DoublyLinkedList[StrictParsley[_]]): this.type = {
        into.stealAll(p.before)
        Seq.whenNonPure(p.res, into.addOne(_))
        into.stealAll(p.after)
        this
    }

    private def chooseInto(p: StrictParsley[A]): DoublyLinkedList[StrictParsley[_]] = p match {
        case _: Pure[_] => before
        case _          => after
    }

    override def optimise: StrictParsley[A] = this match {
        // Assume that this is eliminated first, so not other before or afters
        case (_: Pure[_]) **> u => u
        case (p: MZero) **> _ => p
        case u <** (_: Pure[_]) => u
        case (p: MZero) <** _ => p
        case u@Seq(bs1, br, bs2) **> r =>
            bs2.lastOption match {
                case Some(_: MZero) => u
                case _ =>
                    before = bs1
                    Seq.whenNonPure(br, before.addOne(_))
                    before.stealAll(bs2)
                    mergeIntoRight(r)
            }
        case _ **> q => mergeIntoRight(q)
        case u@Seq(rs1, rr, rs2) <** p =>
            rs2.lastOption match {
                case Some(_: MZero) => u
                case _ =>
                    before = rs1
                    res = rr
                    assume(!rr.isInstanceOf[Pure[_]] || rs2.isEmpty, "rs2 is empty when rr is Pure")
                    assume(after.size == 1 && (after.head eq p), "after can only contain just p, which is going to get flattened, so it can be dropped")
                    after = rs2
                    val into = chooseInto(rr)
                    p match {
                        case p: Seq[_] => mergeFromRight(p, into)
                        case p =>
                            into.addOne(p)
                            this
                    }
            }
        case r <** (p: Seq[_]) =>
            assume(after.size == 1 && (after.head eq p), "after can only contain just p, which is going to get flattened, so it can be cleared")
            after.clear()
            mergeFromRight(p, chooseInto(r))
        // shift pure to the right by swapping before and after (before is empty linked list!)
        case (_: Pure[_]) <* _ =>
            assume(before.isEmpty, "empty can reuse before instead of allocating a new list because before is empty")
            val empty = before
            before = after
            after = empty
            this
        case _ => this
    }

    override def codeGen[Cont[_, _]: ContOps, R](implicit instrs: InstrBuffer, state: CodeGenState): Cont[R, Unit] = res match {
        case Pure(x) =>
            // peephole here involves CharTokFastPerform, StringTokFastPerform, and Exchange
            assume(after.isEmpty, "The pure in question is normalised to the end: if result is pure, after is empty.")
            assume(before.nonEmpty, "before cannot be empty because after is empty")
            val last = before.last
            before.initInPlace()
            suspend(Seq.codeGenMany[Cont, R](before.iterator)) >> {
                last match {
                    case ct@CharTok(c) => result(instrs += instructions.CharTokFastPerform[Char, A](c, _ => x, ct.expected))
                    case st@StringTok(s) => result(instrs += instructions.StringTokFastPerform(s, _ => x, st.expected))
                    case st@Satisfy(f) => result(instrs += new instructions.SatisfyExchange(f, x, st.expected))
                    case _ =>
                        suspend(last.codeGen[Cont, R]) |> {
                            instrs += new instructions.Exchange(x)
                        }
                }
            }
        case _ =>
            suspend(Seq.codeGenMany[Cont, R](before.iterator)) >> {
                suspend(res.codeGen[Cont, R]) >> {
                    suspend(Seq.codeGenMany(after.iterator))
                }
            }
    }
    // $COVERAGE-OFF$
    final override def pretty: String = (before.map(_.pretty) ++ (res.pretty :: after.map(_.pretty).toList)).mkString("seq(", ", ", ")")
    // $COVERAGE-ON$
}

private [backend] object Seq {
    def unapply[A](self: Seq[A]): Some[(DoublyLinkedList[StrictParsley[_]], StrictParsley[A], DoublyLinkedList[StrictParsley[_]])] = {
        Some((self.before, self.res, self.after))
    }

    private [Seq] def codeGenMany[Cont[_, _]: ContOps, R](it: Iterator[StrictParsley[_]])
                                                          (implicit instrs: InstrBuffer, state: CodeGenState): Cont[R, Unit] = {
        if (it.hasNext) {
            suspend(it.next().codeGen[Cont, R]) >> {
                instrs += instructions.Pop
                suspend(codeGenMany(it))
            }
        } else result(())
    }

    private [Seq] def whenNonPure(p: StrictParsley[_], f: StrictParsley[_] => Unit): Unit = p match {
        case _: Pure[_] =>
        case p          => f(p)
    }
}

private [backend] object <*> {
    def apply[A, B](left: StrictParsley[A=>B], right: StrictParsley[A]): <*>[A, B] = new <*>[A, B](left, right)
    def unapply[A, B](self: <*>[A, B]): Some[(StrictParsley[A=>B], StrictParsley[A])] = Some((self.left, self.right))
}
private [deepembedding] object *> {
    def apply[A](left: StrictParsley[_], right: StrictParsley[A]): Seq[A] = {
        val before = DoublyLinkedList.empty[StrictParsley[_]]
        before.addOne(left)
        *>(before, right)
    }
    private [backend] def apply[A](before: DoublyLinkedList[StrictParsley[_]], res: StrictParsley[A]): Seq[A] = new Seq(before, res, DoublyLinkedList.empty)
    private [backend] def unapply[A](self: Seq[A]): Option[(DoublyLinkedList[StrictParsley[_]], StrictParsley[A])] = {
        if (self.after.isEmpty) Some((self.before, self.res))
        else None
    }
}
private [backend] object **> {
    private [backend] def unapply[A](self: Seq[A]): Option[(StrictParsley[_], StrictParsley[A])] = *>.unapply(self).collect {
        case (before, res) if self.before.size == 1 => (before.head, res)
    }
}

private [deepembedding]  object <* {
    def apply[A](left: StrictParsley[A], right: StrictParsley[_]): Seq[A] = {
        val after = DoublyLinkedList.empty[StrictParsley[_]]
        after.addOne(right)
        <*(left, after)
    }
    private [backend] def apply[A](res: StrictParsley[A], after: DoublyLinkedList[StrictParsley[_]]): Seq[A] = new Seq(DoublyLinkedList.empty, res, after)
    private [backend] def unapply[A](self: Seq[A]): Option[(StrictParsley[A], DoublyLinkedList[StrictParsley[_]])] = {
        if (self.before.isEmpty) Some((self.res, self.after))
        else None
    }
}

private [backend] object <** {
    private [backend] def unapply[A](self: Seq[A]): Option[(StrictParsley[A], StrictParsley[_])] = <*.unapply(self).collect {
        case (res, after) if after.size == 1 => (res, after.head)
    }
}
