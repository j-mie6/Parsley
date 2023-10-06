/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend

import parsley.debug.{Breakpoint, Profiler}
import parsley.errors.ErrorBuilder
import parsley.registers.Reg
import parsley.token.descriptions.SpaceDesc
import parsley.token.descriptions.numeric.PlusSignPresence
import parsley.token.errors.{ErrorConfig, LabelConfig, SpecialisedFilterConfig}
import parsley.token.predicate.CharPredicate

import parsley.internal.collection.immutable.Trie
import parsley.internal.deepembedding.Sign.SignType
import parsley.internal.deepembedding.singletons._
import parsley.internal.deepembedding.singletons.token._
import parsley.internal.errors.CaretWidth

/** Visitor class template for the processing of parsers without fully explicit exhaustive pattern
  * matching.
  *
  * This particular visitor is indexed on its return type to allow the preservation of relevant type
  * information, such as setting it to [[LazyParsley]] to tell the type system that you want to
  * produce new parsers using the visitor.
  *
  * @tparam T Context type for holding processing information as the visitor visits parsers.
  * @tparam U Return value wrapper for the results of visiting the parsers.
  */
private [parsley] abstract class LazyParsleyIVisitor[-T, +U[+_]] { // scalastyle:ignore number.of.methods
    // Singleton parser visitors.
    def visit[A](self: Pure[A], context: T)(x: A): U[A]
    def visit[A](self: Fresh[A], context: T)(x: =>A): U[A]
    def visit(self: Satisfy, context: T)(f: Char => Boolean, expected: LabelConfig): U[Char]
    def visit(self: Line.type, context: T): U[Int]
    def visit(self: Col.type, context: T): U[Int]
    def visit(self: Offset.type, context: T): U[Int]
    def visit[S](self: Get[S], context: T)(reg: Reg[S]): U[S]
    def visit(self: WhiteSpace, context: T)(ws: Char => Boolean, desc: SpaceDesc, errorConfig: ErrorConfig): U[Unit]
    def visit(self: SkipComments, context: T)(desc: SpaceDesc, errorConfig: ErrorConfig): U[Unit]
    def visit(self: Comment, context: T)(desc: SpaceDesc, errorConfig: ErrorConfig): U[Unit]
    def visit[A](self: Sign[A], context: T)(ty: SignType, signPresence: PlusSignPresence): U[A => A]
    def visit(self: NonSpecific, context: T)(name: String,
                                             ue: String => String,
                                             start: Char => Boolean,
                                             letter: Char => Boolean,
                                             illegal: String => Boolean): U[String]
    def visit[A](self: CharTok[A], context: T)(c: Char, x: A, exp: LabelConfig): U[A]
    def visit[A](self: SupplementaryCharTok[A], context: T)(codepoint: Int, x: A, exp: LabelConfig): U[A]
    def visit[A](self: StringTok[A], context: T)(s: String, x: A, exp: LabelConfig): U[A]
    def visit(self: Eof.type, context: T): U[Unit]
    def visit(self: UniSatisfy, context: T)(f: Int => Boolean, exp: LabelConfig): U[Int]
    def visit[S](self: Modify[S], context: T)(reg: Reg[S], f: S => S): U[Unit]
    def visit(self: Empty, context: T)(width: Int): U[Nothing]
    def visit(self: Fail, context: T)(width: CaretWidth, msgs: Seq[String]): U[Nothing]
    def visit(self: Unexpected, context: T)(msg: String, width: CaretWidth): U[Nothing]
    def visit[A](self: VanillaGen[A], context: T)(gen: parsley.errors.VanillaGen[A]): U[((A, Int)) => Nothing]
    def visit[A](self: SpecialisedGen[A], context: T)(gen: parsley.errors.SpecialisedGen[A]): U[((A, Int)) => Nothing]
    def visit(self: EscapeMapped, context: T)(escTrie: Trie[Int], escs: Set[String]): U[Int]
    def visit(self: EscapeAtMost, context: T)(n: Int, radix: Int): U[BigInt]
    def visit(self: EscapeOneOfExactly, context: T)(radix: Int, ns: List[Int], ie: SpecialisedFilterConfig[Int]): U[BigInt]
    def visit(self: SoftKeyword, context: T)(specific: String,
                                             letter: CharPredicate,
                                             caseSensitive: Boolean,
                                             expected: LabelConfig,
                                             expectedEnd: String): U[Unit]
    def visit(self: SoftOperator, context: T)(specific: String, letter: CharPredicate, ops: Trie[Unit], expected: LabelConfig, expectedEnd: String): U[Unit]

    // Primitive parser visitors.
    def visit[A](self: Attempt[A], context: T)(p: LazyParsley[A]): U[A]
    def visit[A](self: Look[A], context: T)(p: LazyParsley[A]): U[A]
    def visit[A](self: NotFollowedBy[A], context: T)(p: LazyParsley[A]): U[Unit]
    def visit[S](self: Put[S], context: T)(reg: Reg[S], p: LazyParsley[S]): U[Unit]
    def visit[S, A](self: NewReg[S, A], context: T)(reg: Reg[S], init: LazyParsley[S], body: =>LazyParsley[A]): U[A]
    def visit(self: Span, context: T)(p: LazyParsley[_]): U[String]
    def visit[A](self: Debug[A], context: T)(p: LazyParsley[A], name: String, ascii: Boolean, break: Breakpoint, watchedRegs: Seq[(Reg[_], String)]): U[A]
    def visit[A](self: DebugError[A], context: T)(p: LazyParsley[A], name: String, ascii: Boolean, errBuilder: ErrorBuilder[_]): U[A]
    def visit[A](self: Profile[A], context: T)(p: LazyParsley[A], name: String, profiler: Profiler): U[A]

    // Selective parser visitors.
    def visit[A, B, C](self: Branch[A, B, C], context: T)(b: LazyParsley[Either[A, B]], p: =>LazyParsley[A => C], q: =>LazyParsley[B => C]): U[C]
    def visit[A](self: If[A], context: T)(b: LazyParsley[Boolean], p: =>LazyParsley[A], q: =>LazyParsley[A]): U[A]
    def visit[A](self: Filter[A], context: T)(p: LazyParsley[A], pred: A => Boolean, err: =>LazyParsley[((A, Int)) => Nothing]): U[A]
    def visit[A, B](self: MapFilter[A, B], context: T)(p: LazyParsley[A], pred: A => Option[B], err: =>LazyParsley[((A, Int)) => Nothing]): U[B]

    // Alternative parser visitors.
    def visit[A](self: <|>[A])(context: T, p: LazyParsley[A], q: LazyParsley[A]): U[A]

    // Intrinsic parser visitors.
    def visit[A, B, C](self: Lift2[A, B, C], context: T)(f: (A, B) => C, p: LazyParsley[A], q: =>LazyParsley[B]): U[C]
    def visit[A, B, C, D](self: Lift3[A, B, C, D], context: T)(f: (A, B, C) => D, p: LazyParsley[A], q: =>LazyParsley[B], r: =>LazyParsley[C]): U[D]
    def visit[S, A](self: Local[S, A], context: T)(reg: Reg[S], p: LazyParsley[S], q: =>LazyParsley[A]): U[A]

    // Sequence parser visitors.
    def visit[A, B](self: A <*> B, context: T)(pf: LazyParsley[A => B], px: =>LazyParsley[A]): U[B]
    def visit[A, B](self: A >>= B, context: T)(p: LazyParsley[A], f: A => LazyParsley[B]): U[B]
    def visit[A](self: *>[A], context: T)(p: LazyParsley[_], _q: =>LazyParsley[A]): U[A]
    def visit[A](self: <*[A], context: T)(p: LazyParsley[A], _q: =>LazyParsley[_]): U[A]

    // Iterative parser visitors.
    def visit[A](self: Many[A], context: T)(p: LazyParsley[A]): U[List[A]]
    def visit[A](self: ChainPost[A], context: T)(p: LazyParsley[A], _op: =>LazyParsley[A => A]): U[A]
    def visit[A](self: ChainPre[A], context: T)(p: LazyParsley[A], op: =>LazyParsley[A => A]): U[A]
    def visit[A, B](self: Chainl[A, B], context: T)(init: LazyParsley[B], p: =>LazyParsley[A], op: =>LazyParsley[(B, A) => B]): U[B]
    def visit[A, B](self: Chainr[A, B], context: T)(p: LazyParsley[A], op: =>LazyParsley[(A, B) => B], wrap: A => B): U[B]
    def visit[A](self: SepEndBy1[A], context: T)(p: LazyParsley[A], sep: =>LazyParsley[_]): U[List[A]]
    def visit[A](self: ManyUntil[A], context: T)(body: LazyParsley[Any]): U[List[A]]
    def visit(self: SkipManyUntil, context: T)(body: LazyParsley[Any]): U[Unit]

    // Error parser visitors.
    def visit[A](self: ErrorLabel[A], context: T)(p: LazyParsley[A], labels: Seq[String]): U[A]
    def visit[A](self: ErrorHide[A], context: T)(p: LazyParsley[A]): U[A]
    def visit[A](self: ErrorExplain[A], context: T)(p: LazyParsley[A], reason: String): U[A]
    def visit[A](self: ErrorAmend[A], context: T)(p: LazyParsley[A], partial: Boolean): U[A]
    def visit[A](self: ErrorEntrench[A], context: T)(p: LazyParsley[A]): U[A]
    def visit[A](self: ErrorDislodge[A], context: T)(n: Int, p: LazyParsley[A]): U[A]
    def visit[A](self: ErrorLexical[A], context: T)(p: LazyParsley[A]): U[A]
}

/** Generalised version of [[LazyParsleyIVisitor]] that allows you to define default implementations
  * for parser classes that live under a base trait (e.g. [[Unary]]).
  *
  * This visitor should only require implementations for [[Singleton]], [[Unary]], [[Binary]],
  * [[Ternary]], [[<|>]], and [[ChainPre]].
  *
  * Unless a specific override is needed, all other visitor methods are implemented relative to
  * these six default implementations.
  */
private [frontend] abstract class GenericLazyParsleyIVisitor[-T, +U[+_]] extends LazyParsleyIVisitor[T, U] { // scalastyle:ignore number.of.methods
    // Default methods for the four base parser types.
    // XXX: These names are different as otherwise some visit methods recurse in an unwanted manner.
    def visitSingleton[A](self: Singleton[A], context: T): U[A]
    def visitUnary[A, B](self: Unary[A, B], context: T)(p: LazyParsley[A]): U[B]
    def visitBinary[A, B, C](self: Binary[A, B, C], context: T)(l: LazyParsley[A], r: =>LazyParsley[B]): U[C]
    def visitTernary[A, B, C, D](self: Ternary[A, B, C, D], context: T)(f: LazyParsley[A], s: =>LazyParsley[B], t: =>LazyParsley[C]): U[D]

    // Singleton overrides.
    override def visit[A](self: Pure[A], context: T)(x: A): U[A] = visitSingleton(self, context)
    override def visit[A](self: Fresh[A], context: T)(x: =>A): U[A] = visitSingleton(self, context)
    override def visit(self: Satisfy, context: T)(f: Char => Boolean, expected: LabelConfig): U[Char] = visitSingleton(self, context)
    override def visit(self: Line.type, context: T): U[Int] = visitSingleton(self, context)
    override def visit(self: Col.type, context: T): U[Int] = visitSingleton(self, context)
    override def visit(self: Offset.type, context: T): U[Int] = visitSingleton(self, context)
    override def visit[S](self: Get[S], context: T)(reg: Reg[S]): U[S] = visitSingleton(self, context)
    override def visit(self: WhiteSpace, context: T)(ws: Char => Boolean, desc: SpaceDesc, errorConfig: ErrorConfig): U[Unit] = visitSingleton(self, context)
    override def visit(self: SkipComments, context: T)(desc: SpaceDesc, errorConfig: ErrorConfig): U[Unit] = visitSingleton(self, context)
    override def visit(self: Comment, context: T)(desc: SpaceDesc, errorConfig: ErrorConfig): U[Unit] = visitSingleton(self, context)
    override def visit[A](self: Sign[A], context: T)(ty: SignType, signPresence: PlusSignPresence): U[A => A] = visitSingleton(self, context)
    override def visit(self: NonSpecific, context: T)(name: String,
                                                      ue: String => String,
                                                      start: Char => Boolean,
                                                      letter: Char => Boolean,
                                                      illegal: String => Boolean): U[String] = visitSingleton(self, context)
    override def visit[A](self: CharTok[A], context: T)(c: Char, x: A, exp: LabelConfig): U[A] = visitSingleton(self, context)
    override def visit[A](self: SupplementaryCharTok[A], context: T)(codepoint: Int, x: A, exp: LabelConfig): U[A] = visitSingleton(self, context)
    override def visit[A](self: StringTok[A], context: T)(s: String, x: A, exp: LabelConfig): U[A] = visitSingleton(self, context)
    override def visit(self: Eof.type, context: T): U[Unit] = visitSingleton(self, context)
    override def visit(self: UniSatisfy, context: T)(f: Int => Boolean, exp: LabelConfig): U[Int] = visitSingleton(self, context)
    override def visit[S](self: Modify[S], context: T)(reg: Reg[S], f: S => S): U[Unit] = visitSingleton(self, context)
    override def visit(self: Empty, context: T)(width: Int): U[Nothing] = visitSingleton(self, context)
    override def visit(self: Fail, context: T)(width: CaretWidth, msgs: Seq[String]): U[Nothing] = visitSingleton(self, context)
    override def visit(self: Unexpected, context: T)(msg: String, width: CaretWidth): U[Nothing] = visitSingleton(self, context)
    override def visit[A](self: VanillaGen[A], context: T)(gen: parsley.errors.VanillaGen[A]): U[((A, Int)) => Nothing] = visitSingleton(self, context)
    override def visit[A](self: SpecialisedGen[A], context: T)(gen: parsley.errors.SpecialisedGen[A]): U[((A, Int)) => Nothing] = {
        visitSingleton(self, context)
    }
    override def visit(self: EscapeMapped, context: T)(escTrie: Trie[Int], escs: Set[String]): U[Int] = visitSingleton(self, context)
    override def visit(self: EscapeAtMost, context: T)(n: Int, radix: Int): U[BigInt] = visitSingleton(self, context)
    override def visit(self: EscapeOneOfExactly, context: T)(radix: Int, ns: List[Int], ie: SpecialisedFilterConfig[Int]): U[BigInt] = {
        visitSingleton(self, context)
    }
    override def visit(self: SoftKeyword, context: T)(specific: String,
                                                      letter: CharPredicate,
                                                      caseSensitive: Boolean,
                                                      expected: LabelConfig,
                                                      expectedEnd: String): U[Unit] = visitSingleton(self, context)
    override def visit(self: SoftOperator, context: T)(specific: String,
                                                       letter: CharPredicate,
                                                       ops: Trie[Unit],
                                                       expected: LabelConfig,
                                                       expectedEnd: String): U[Unit] = visitSingleton(self, context)

    // Primitive overrides.
    override def visit[A](self: Attempt[A], context: T)(p: LazyParsley[A]): U[A] = visitUnary(self, context)(p)
    override def visit[A](self: Look[A], context: T)(p: LazyParsley[A]): U[A] = visitUnary(self, context)(p)
    override def visit[A](self: NotFollowedBy[A], context: T)(p: LazyParsley[A]): U[Unit] = visitUnary(self, context)(p)
    override def visit[S](self: Put[S], context: T)(reg: Reg[S], p: LazyParsley[S]): U[Unit] = visitUnary(self, context)(p)
    override def visit[S, A](self: NewReg[S, A], context: T)(reg: Reg[S], init: LazyParsley[S], body: =>LazyParsley[A]): U[A] = {
        visitBinary(self, context)(init, body)
    }
    override def visit(self: Span, context: T)(p: LazyParsley[_]): U[String] = visitUnary[Any, String](self, context)(p)
    override def visit[A](self: Debug[A], context: T)
                         (p: LazyParsley[A], name: String, ascii: Boolean, break: Breakpoint, watchedRegs: Seq[(Reg[_], String)]): U[A] = {
        visitUnary(self, context)(p)
    }
    override def visit[A](self: DebugError[A], context: T)(p: LazyParsley[A], name: String, ascii: Boolean, errBuilder: ErrorBuilder[_]): U[A] = {
        visitUnary(self, context)(p)
    }
    override def visit[A](self: Profile[A], context: T)(p: LazyParsley[A], name: String, profiler: Profiler): U[A] = visitUnary(self, context)(p)

    // Selective overrides.
    override def visit[A, B, C](self: Branch[A, B, C], context: T)(b: LazyParsley[Either[A, B]], p: =>LazyParsley[A => C], q: =>LazyParsley[B => C]): U[C] = {
        visitTernary(self, context)(b, p, q)
    }
    override def visit[A](self: If[A], context: T)(b: LazyParsley[Boolean], p: =>LazyParsley[A], q: =>LazyParsley[A]): U[A] = {
        visitTernary(self, context)(b, p, q)
    }
    override def visit[A](self: Filter[A], context: T)(p: LazyParsley[A], pred: A => Boolean, err: =>LazyParsley[((A, Int)) => Nothing]): U[A] = {
        visitBinary(self, context)(p, err)
    }
    override def visit[A, B](self: MapFilter[A, B], context: T)(p: LazyParsley[A], pred: A => Option[B], err: =>LazyParsley[((A, Int)) => Nothing]): U[B] = {
        visitBinary(self, context)(p, err)
    }

    // Intrinsic overrides.
    override def visit[A, B, C](self: Lift2[A, B, C], context: T)(f: (A, B) => C, p: LazyParsley[A], q: =>LazyParsley[B]): U[C] = {
        visitBinary(self, context)(p, q)
    }
    override def visit[A, B, C, D](self: Lift3[A, B, C, D], context: T)(f: (A, B, C) => D,
                                                                        p: LazyParsley[A],
                                                                        q: =>LazyParsley[B],
                                                                        r: =>LazyParsley[C]): U[D] = visitTernary(self, context)(p, q, r)
    override def visit[S, A](self: Local[S, A], context: T)(reg: Reg[S], p: LazyParsley[S], q: =>LazyParsley[A]): U[A] = visitBinary(self, context)(p, q)

    // Sequence overrides.
    override def visit[A, B](self: A <*> B, context: T)(pf: LazyParsley[A => B], px: =>LazyParsley[A]): U[B] = visitBinary(self, context)(pf, px)
    override def visit[A, B](self: A >>= B, context: T)(p: LazyParsley[A], f: A => LazyParsley[B]): U[B] = visitUnary(self, context)(p)
    override def visit[A](self: *>[A], context: T)(p: LazyParsley[_], _q: =>LazyParsley[A]): U[A] = visitBinary[Any, A, A](self, context)(p, _q)
    override def visit[A](self: <*[A], context: T)(p: LazyParsley[A], _q: =>LazyParsley[_]): U[A] = visitBinary[A, Any, A](self, context)(p, _q)

    // Iterative overrides.
    override def visit[A](self: Many[A], context: T)(p: LazyParsley[A]): U[List[A]] = visitUnary(self, context)(p)
    override def visit[A](self: ChainPost[A], context: T)(p: LazyParsley[A], _op: =>LazyParsley[A => A]): U[A] = visitBinary(self, context)(p, _op)
    override def visit[A, B](self: Chainl[A, B], context: T)(init: LazyParsley[B], p: =>LazyParsley[A], op: =>LazyParsley[(B, A) => B]): U[B] = {
        visitTernary(self, context)(init, p, op)
    }
    override def visit[A, B](self: Chainr[A, B], context: T)(p: LazyParsley[A], op: =>LazyParsley[(A, B) => B], wrap: A => B): U[B] = {
        visitBinary(self, context)(p, op)
    }
    override def visit[A](self: SepEndBy1[A], context: T)(p: LazyParsley[A], sep: =>LazyParsley[_]): U[List[A]] = {
        visitBinary[A, Any, List[A]](self, context)(p, sep)
    }
    override def visit[A](self: ManyUntil[A], context: T)(body: LazyParsley[Any]): U[List[A]] = visitUnary[Any, List[A]](self, context)(body)
    override def visit(self: SkipManyUntil, context: T)(body: LazyParsley[Any]): U[Unit] = visitUnary[Any, Unit](self, context)(body)

    // Error overrides.
    override def visit[A](self: ErrorLabel[A], context: T)(p: LazyParsley[A], labels: Seq[String]): U[A] = visitUnary(self, context)(p)
    override def visit[A](self: ErrorHide[A], context: T)(p: LazyParsley[A]): U[A] = visitUnary(self, context)(p)
    override def visit[A](self: ErrorExplain[A], context: T)(p: LazyParsley[A], reason: String): U[A] = visitUnary(self, context)(p)
    override def visit[A](self: ErrorAmend[A], context: T)(p: LazyParsley[A], partial: Boolean): U[A] = visitUnary(self, context)(p)
    override def visit[A](self: ErrorEntrench[A], context: T)(p: LazyParsley[A]): U[A] = visitUnary(self, context)(p)
    override def visit[A](self: ErrorDislodge[A], context: T)(n: Int, p: LazyParsley[A]): U[A] = visitUnary(self, context)(p)
    override def visit[A](self: ErrorLexical[A], context: T)(p: LazyParsley[A]): U[A] = visitUnary(self, context)(p)
}
