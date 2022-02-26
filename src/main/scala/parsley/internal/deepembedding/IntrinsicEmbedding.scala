package parsley.internal.deepembedding

import ContOps.{result, ContAdapter}
import parsley.registers.Reg

import scala.language.higherKinds

private [parsley] final class CharTok(private [CharTok] val c: Char, val expected: Option[String])
    extends Singleton[Char](s"char($c)", new backend.CharTok(c, expected))

private [parsley] final class StringTok(private [StringTok] val s: String, val expected: Option[String])
    extends Singleton[String](s"string($s)", new backend.StringTok(s, expected))
private [parsley] final class Lift2[A, B, C](private [Lift2] val f: (A, B) => C, p: Parsley[A], q: =>Parsley[B])
    extends Binary[A, B, C](p, q, (l, r) => s"lift2(f, $l, $r)", new backend.Lift2(f, _, _))
private [parsley] final class Lift3[A, B, C, D](private [Lift3] val f: (A, B, C) => D, p: Parsley[A], q: =>Parsley[B], r: =>Parsley[C])
    extends Ternary[A, B, C, D](p, q, r, (f, s, t) => s"lift3(f, $f, $s, $t)", new backend.Lift3(f, _, _, _))

private [parsley] object Eof extends Singleton[Unit]("eof", backend.Eof)
private [parsley] final class Modify[S](val reg: Reg[S], f: S => S) extends Singleton[Unit](s"modify($reg, ?)", new backend.Modify(reg, f)) with UsesRegister
private [parsley] final class Local[S, A](val reg: Reg[S], p: Parsley[S], q: =>Parsley[A])
    extends Binary[S, A, A](p, q, (l, r) => s"local($reg, $l, $r)", new backend.Local(reg, _, _)) with UsesRegister