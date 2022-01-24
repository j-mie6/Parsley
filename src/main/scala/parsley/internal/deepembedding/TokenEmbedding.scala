package parsley.internal.deepembedding

import Sign.SignType
import parsley.internal.machine.instructions

private [parsley] final class WhiteSpace(ws: Char => Boolean, start: String, end: String, line: String, nested: Boolean)
    extends Singleton[Unit]("whiteSpace", new instructions.TokenWhiteSpace(ws, start, end, line, nested))

private [parsley] final class SkipComments(start: String, end: String, line: String, nested: Boolean)
    extends Singleton[Unit]("skipComments", new instructions.TokenSkipComments(start, end, line, nested))

private [parsley] final class Comment(start: String, end: String, line: String, nested: Boolean)
    extends Singleton[Unit]("comment", new instructions.TokenComment(start, end, line, nested))

private [parsley] final class Sign[A](ty: SignType)
    extends Singleton[A => A]("sign", new instructions.TokenSign(ty))

private [parsley] object Natural
    extends Singleton[Int]("natural", instructions.TokenNatural)

private [parsley] object Float
    extends Singleton[Double]("float", instructions.TokenFloat)

private [parsley] object Escape
    extends Singleton[Char]("escape", new instructions.TokenEscape)

private [parsley] final class StringLiteral(ws: Char => Boolean)
    extends Singleton[String]("stringLiteral", new instructions.TokenString(ws))

private [parsley] object RawStringLiteral
    extends Singleton[String]("rawStringLiteral", instructions.TokenRawString)

private [parsley] class NonSpecific(combinatorName: String, name: String, illegalName: String, start: Char => Boolean,
                                    letter: Char => Boolean, illegal: String => Boolean)
    extends Singleton[String](combinatorName, new instructions.TokenNonSpecific(name, illegalName)(start, letter, illegal))

private [parsley] final class Specific(name: String, private [Specific] val specific: String,
                                       letter: Char => Boolean, val caseSensitive: Boolean)
    extends Singleton[Unit](s"$name($specific)", new instructions.TokenSpecific(specific, letter, caseSensitive))

private [parsley] final class MaxOp(private [MaxOp] val operator: String, ops: Set[String])
    extends Singleton[Unit](s"maxOp($operator)", new instructions.TokenMaxOp(operator, ops))

private [parsley] object Sign {
    private [parsley] sealed trait SignType {
        type resultType
    }
    private [parsley] case object DoubleType extends SignType {
        override type resultType = Double
    }
    private [parsley] case object IntType extends SignType {
        override type resultType = Int
    }
}

// $COVERAGE-OFF$
private [deepembedding] object Specific {
    def unapply(self: Specific): Some[String] = Some(self.specific)
}
private [deepembedding] object MaxOp {
    def unapply(self: MaxOp): Some[String] = Some(self.operator)
}
// $COVERAGE-ON$