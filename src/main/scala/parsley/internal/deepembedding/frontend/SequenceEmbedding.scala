package parsley.internal.deepembedding.frontend

import parsley.internal.deepembedding.ContOps.{result, ContAdapter}
import parsley.internal.machine.instructions

import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.higherKinds
import parsley.internal.deepembedding.backend, backend.StrictParsley

// Core Embedding
private [parsley] final class <*>[A, B](pf: LazyParsley[A => B], px: =>LazyParsley[A])
    extends Binary[A => B, A, B](pf, px, (l, r) => s"($l <*> $r)", new backend.<*>(_, _))

private [parsley] final class >>=[A, B](p: LazyParsley[A], private [>>=] val f: A => LazyParsley[B])
    extends Unary[A, B](p, l => s"($l >>= ?)", new backend.>>=(_, f))

private [deepembedding] sealed abstract class Seq[A, B, Res](left: LazyParsley[A], right: =>LazyParsley[B], pretty: String,
                                                             make: (StrictParsley[A], StrictParsley[B]) => StrictParsley[Res])
    extends Binary[A, B, Res](left, right, (l, r) => s"($l $pretty $r)", make)
private [parsley] final class *>[A](_p: LazyParsley[_], _q: =>LazyParsley[A]) extends Seq[Any, A, A](_p, _q, "*>", new backend.*>(_, _))
private [parsley] final class <*[A](_p: LazyParsley[A], _q: =>LazyParsley[_]) extends Seq[A, Any, A](_p, _q, "<*", new backend.<*(_, _))