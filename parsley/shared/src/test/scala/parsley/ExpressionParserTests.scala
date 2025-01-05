/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import Predef.{ArrowAssoc => _, _}

import token.{descriptions => desc}
import parsley.character.digit
import parsley.syntax.character.{charLift, stringLift}
import parsley.expr.{chain, infix, mixed}
import parsley.expr.{precedence, Ops, GOps, SOps, InfixL, InfixR, Prefix, Postfix, InfixN, Atoms}
import parsley.position._
import parsley.generic._

class ExpressionParserTests extends ParsleyTest {
    "chain.postfix" must "require an initial value" in {
        chain.postfix('1' #> 1)('+' #> (_ + 1)).parseAll("1") should be (Success(1))
    }
    it must "parse all operators that follow" in {
        chain.postfix('1' #> 1)('+' #> (_ + 1)).parseAll("1++++++++++++++") should not be a [Failure[_]]
    }
    it must "apply the functions" in {
        chain.postfix('1' #> 1)('+' #> (_ + 1)).parseAll("1++++++++++++++") should be (Success(15))
    }
    it must "fail if an operator fails after consuming input" in {
        chain.postfix('1' #> 1)("++" #> (_ + 1)).parseAll("1+++++++++++++") shouldBe a [Failure[_]]
    }
    it must "not leave the stack in an inconsistent state on failure" in {
        val p = chain.postfix('1' #> 1)(col.as[Int => Int](_ + 1) <* '+')
        val q = chain.left1(p)('*' #> (_ * _))
        noException should be thrownBy q.parse("1+*1+")
    }

    "chain.postfix1" must "require and initial value AND an initial operator" in {
        cases(chain.postfix1('1' #> 1)('+'.as[Int => Int](_ + 1)))(
            "1" -> None,
            "1+" -> Some(2),
        )
    }
    it must "parse all operators that follow" in {
        chain.postfix1('1' #> 1)('+'.as[Int => Int](_ + 1)).parse("1++++++++++++++") should not be a [Failure[_]]
    }
    it must "apply the functions" in {
        chain.postfix1('1' #> 1)('+'.as[Int => Int](_ + 1)).parse("1++++++++++++++") should be (Success(15))
    }
    it must "fail if an operator fails after consuming input" in {
        chain.postfix1('1' #> 1)("++".as[Int => Int](_ + 1)).parse("1+++++++++++++") shouldBe a [Failure[_]]
    }

    "chain.prefix" must "parse an operatorless value" in {
        chain.prefix('1' #> 1)('+' #> (_ + 1)).parse("1") should be (Success(1))
    }
    it must "parse all operators that precede a value" in {
        chain.prefix('1' #> 1)('+' #> (_ + 1)).parse("+++++++++++1") should not be a [Failure[_]]
    }
    it must "fail if the final value is absent" in {
        chain.prefix('1' #> 1)('+' #> (_ + 1)).parse("+++++++++++") shouldBe a [Failure[_]]
    }
    it must "apply the functions" in {
        chain.prefix('1' #> 1)('+' #> (_ + 1)).parse("+++++++++++1") should be (Success(12))
    }

    "chain.prefix1" must "not parse an operatorless value" in {
        chain.prefix1('1' #> 1)('+'.as[Int => Int](_ + 1)).parse("1") shouldBe a [Failure[_]]
    }
    it must "parse all operators that precede a value" in {
        chain.prefix1('1' #> 1)('+'.as[Int => Int](_ + 1)).parse("+++++++++++1") should not be a [Failure[_]]
    }
    it must "fail if the final value is absent" in {
        chain.prefix1('1' #> 1)('+'.as[Int => Int](_ + 1)).parse("+++++++++++") shouldBe a [Failure[_]]
    }
    it must "apply the functions" in {
        chain.prefix1('1' #> 1)('+'.as[Int => Int](_ + 1)).parse("+++++++++++1") shouldBe Success(12)
    }

    "chain.right1" must "require an initial value" in {
        cases(chain.right1("11" #> 1)('+' #> (_ + _)))(
            "11" -> Some(1),
            "1"  -> None,
            "2"  -> None,
        )
    }
    it must "parse all operators and values that follow" in {
        chain.right1("11" #> 1)('+' #> (_ + _)).parse("11+11+11+11+11") shouldBe Success(5)
    }
    it must "apply the functions with correct associativity" in {
        chain.right1(digit.map(_.asDigit))('%' #> (_ % _)).parse("6%5%2%7") shouldBe Success(0)
    }
    it must "fail if an operator or p fails after consuming input" in {
        cases(chain.right1("11" #> 1)("++" #> (_ + _)))(
            "11+11+11+11+11" -> None,
            "11++11++11++1++11" -> None,
        )
    }
    it must "correctly accept the use of a wrapping function" in {
        sealed trait Expr
        case class Add(x: Int, y: Expr) extends Expr
        case class Num(x: Int) extends Expr
        object Add extends ParserBridge2[Int, Expr, Expr]
        cases(infix.right1("1" #> 1)(Add <# "+")(Num.apply))(
            "1+1+1" -> Some(Add(1, Add(1, Num(1)))),
            "1" -> Some(Num(1)),
        )
    }
    "chain.right" must "allow for no initial value" in {
        cases(chain.right("11" #> 1)('+' #> (_ + _), 0))("" -> Some(0), "1" -> None)
    }

    "chain.left1" must "require an initial value" in {
        cases(chain.left1("11" #> 1)('+' #> (_ + _)))("11" -> Some(1), "1" -> None, "2" -> None)
    }
    it must "parse all operators and values that follow" in {
        cases(chain.left1("11" #> 1)('+' #> (_ + _)))("11+11+11+11+11" -> Some(5))
    }
    it must "apply the functions with correct associativity" in {
        cases(chain.left1(digit.map(_.asDigit))('%' #> (_ % _)))("6%5%2%7" -> Some(1))
    }
    it must "fail if an operator fails after consuming input" in {
        cases(chain.left1("11" #> 1)("++" #> (_ + _)))("11+11+11+11+11" -> None, "11++11++11++1++11" -> None)
    }
    it must "not leave the stack in an inconsistent state on failure" in {
        val p = chain.left1('1' #> 1)((col.as[(Int, Int) => Int](_ + _)) <* '+')
        val q = chain.left1(p)('*'.as[(Int, Int) => Int](_ * _))
        noException shouldBe thrownBy (q.parse("1+1*1+1"))
    }
    it must "correctly accept the use of a wrapping function" in {
        sealed trait Expr
        case class Add(x: Expr, y: Int) extends Expr
        case class Num(x: Int) extends Expr
        object Add extends ParserBridge2[Expr, Int, Expr]
        cases(infix.left1("1" #> 1)(Add <# "+")(Num.apply))("1+1+1" -> Some(Add(Add(Num(1), 1), 1)))
    }
    "chain.left" must "allow for no initial value" in {
        val p =chain.left("11" #> 1)('+' #> (_ + _), 0)
        cases(p)("11" -> Some(1), "1" -> None)
        p.parse("2") shouldBe Success(0)
    }

    "expression parsers" should "result in correct precedence" in {
        val expr = precedence[Int](digit.map(_.asDigit))(Ops(InfixL)('*' #> (_*_)),
                                                         Ops(InfixL)('+' #> (_+_)))
        cases(expr)("1+2*3+4" -> Some(11), "1*2+3*4" -> Some(14))
    }
    they should "work for multiple operators at the same level" in {
        val expr = precedence[Int](digit.map(_.asDigit))(Ops(InfixL)('+' #> (_+_), '-' #> (_-_)))
        cases(expr)("1+2-3+4" -> Some(4), "1-2+3-4" -> Some(-2))
    }
    they should "work for mixed associativity operators" in {
        val expr = precedence[Int](digit.map(_.asDigit))(Ops(InfixL)('*' #> (_*_)),
                                                         Ops(InfixR)('+' #> (_+_)))
        cases(expr)("1+2*3+4" -> Some(11), "1*2+3*4" -> Some(14))
    }
    they should "parse mathematical expressions" in {
        lazy val expr: Parsley[Int] = precedence[Int](atom)(
            Ops(Prefix)('-' #> (x => -x)),
            Ops(InfixL)('/' #> (_/_)),
            Ops(InfixR)('*' #> (_*_)),
            Ops(InfixL)('+' #> (_+_), '-' #> (_-_)))
        lazy val atom: Parsley[Int] = digit.map(_.asDigit) <|> ('(' *> expr <* ')')
        cases(expr)(
            "(2+3)*8" -> Some(40),
            "-3+4" -> Some(1),
            "-(3+4)" -> Some(-7),
            "(3+-7)*(-2--4)/2" -> Some(-4),
        )
    }
    they should "parse prefix operators mixed with infix operators" in {
        lazy val expr = precedence[Int](atom)(Ops(Prefix)('-' #> (x => -x)),
                                              Ops(InfixL)('-' #> (_-_)))
        lazy val atom: Parsley[Int] = digit.map(_.asDigit) <|> ('(' *> expr <* ')')
        cases(expr)(
            "-1" -> Some(-1),
            "2-1" -> Some(1),
            "-2-1" -> Some(-3),
            "-(2-1)" -> Some(-1),
            "(-0)-1" -> Some(-1),
        )
    }
    they should "be able to parse prefix operators weaker than an infix" in {
        sealed trait Expr
        case class Lt(x: Expr, y: Expr) extends Expr
        case class Inc(x: Expr) extends Expr
        case class Num(x: Int) extends Expr
        val expr = precedence[Expr](digit.map(_.asDigit).map(Num.apply))(Ops(InfixL)('<' #> Lt.apply),
                                                                         Ops(Prefix)("++" #> Inc.apply))
        expr.parse("++1<2") shouldBe Success(Inc(Lt(Num(1), Num(2))))
    }
    they should "generalise to sub-typed structures" in {
        sealed trait Comp
        case class Less(x: Expr, y: Expr) extends Comp
        sealed trait Expr extends Comp
        case class Add(x: Expr, y: Term) extends Expr
        sealed trait Term extends Expr
        case class Mul(x: Factor, y: Term) extends Term
        sealed trait Factor extends Term
        case class Neg(x: Factor) extends Factor
        sealed trait Atom extends Factor
        case class Parens(x: Comp) extends Atom
        case class Num(x: Int) extends Atom

        object Less extends ParserBridge2[Expr, Expr, Comp]
        object Add extends ParserBridge2[Expr, Term, Expr]
        object Mul extends ParserBridge2[Factor, Term, Term]
        object Neg extends ParserBridge1[Factor, Factor]
        object Parens extends ParserBridge1[Comp, Atom]
        object Num extends ParserBridge1[Int, Atom]

        lazy val expr: Parsley[Comp] = precedence(
            SOps(InfixN)(Less <# '<') +:
            SOps(InfixL)(Add <# '+') +:
            SOps(InfixR)(Mul <# '*') +:
            SOps(Prefix)(Neg <# '-') +:
            Atoms(Num(digit.map(_.asDigit)), Parens('(' *> expr <* ')')))
        expr.parse("(7+8)*2+3+6*2") shouldBe Success(Add(Add(Mul(Parens(Add(Num(7), Num(8))), Num(2)), Num(3)), Mul(Num(6), Num(2))))
    }
    they should "generalise to non-monolithic structures" in {
        sealed trait Comp
        case class Less(x: Expr, y: Expr) extends Comp
        case class CompOf(x: Expr) extends Comp
        sealed trait Expr
        case class Add(x: Expr, y: Term) extends Expr
        case class ExprOf(x: Term) extends Expr
        sealed trait Term
        case class Mul(x: Atom, y: Term) extends Term
        case class TermOf(x: Atom) extends Term
        sealed trait Atom
        case class Parens(x: Comp) extends Atom
        case class Num(x: Int) extends Atom

        object Less extends ParserBridge2[Expr, Expr, Comp]
        object Add extends ParserBridge2[Expr, Term, Expr]
        object Mul extends ParserBridge2[Atom, Term, Term]
        object Parens extends ParserBridge1[Comp, Atom]
        object Num extends ParserBridge1[Int, Atom]

        lazy val expr: Parsley[Comp] = precedence(
            // The type ascriptions are unneeded for Scala 3
            GOps(InfixN)(Less <# '<')(CompOf.apply) +:
            GOps(InfixL)(Add <# '+')(ExprOf.apply) +:
            GOps(InfixR)(Mul <# '*')(TermOf.apply) +:
            Atoms(Num(digit.map(_.asDigit)), Parens('(' *> expr <* ')')))
        expr.parse("(7+8)*2+3+6*2<4") shouldBe {
            Success(Less(
                Add(
                    Add(
                        ExprOf(
                            Mul(
                                Parens(CompOf(Add(ExprOf(TermOf(Num(7))), TermOf(Num(8))))),
                                TermOf(Num(2)))),
                        TermOf(Num(3))),
                    Mul(Num(6), TermOf(Num(2)))),
                ExprOf(TermOf(Num(4)))))
        }
    }
    they should "generalise to non-monolithic structures with more than one chainl1" in {
        sealed trait Expr
        case class Add(x: Expr, y: Term) extends Expr
        case class ExprOf(x: Term) extends Expr
        sealed trait Term
        case class Mul(x: Term, y: Atom) extends Term
        case class TermOf(x: Atom) extends Term
        sealed trait Atom
        case class Parens(x: Expr) extends Atom
        case class Num(x: Int) extends Atom

        object Add extends ParserBridge2[Expr, Term, Expr]
        object Mul extends ParserBridge2[Term, Atom, Term]
        object Parens extends ParserBridge1[Expr, Atom]
        object Num extends ParserBridge1[Int, Atom]

        lazy val expr: Parsley[Expr] = precedence(
            Atoms(Num(digit.map(_.asDigit)), Parens('(' *> expr <* ')')) :+
            GOps[Atom, Term](InfixL)(Mul <# '*')(TermOf.apply) :+
            GOps[Term, Expr](InfixL)(Add <# '+')(ExprOf.apply))
        expr.parse("1*(2+3)") shouldBe a [Success[_]]
    }

    "mixed expressions" should "also be parsable" in {
        val lang = desc.LexicalDesc.plain.copy(
            nameDesc = desc.NameDesc.plain.copy(identifierStart = token.Basic(_.isLetter),
                                                identifierLetter = token.Basic(_.isLetter))
        )

        sealed trait Expr
        case class Binary(l: Expr, r: Expr) extends Expr
        case class Unary(c: Expr) extends Expr
        case class Constant(x: String) extends Expr

        object Binary extends ParserBridge2[Expr, Expr, Expr]
        object Constant extends ParserBridge1[String, Expr]

        object Call extends ParserBridge1[Expr, Expr => Expr] {
            def apply(x1: Expr): Expr => Expr = Binary(_, x1)
        }

        val tok = new token.Lexer(lang)

        lazy val op = Ops(InfixR)(Binary <# ',')
        lazy val ops = Seq(
            Ops(InfixR)(Binary <# ".="),
            Ops(InfixL)(Binary <# '.'),
            Ops(Postfix)(Call(tok.lexeme.parens(expr </> Constant(""))))
        )

        lazy val atom: Parsley[Expr] = Constant(tok.lexeme.names.identifier)
        lazy val expr: Parsley[Expr] = precedence(op, ops: _*)(atom)

        expr.parse("o.f()") shouldBe a [Success[_]]
        expr.parse("o.f(x,y)") shouldBe a [Success[_]]
    }

    "mixed chains" should "allow the mixing of prefix with infix-right" in {
        sealed trait Expr
        case class Binary(l: Constant, r: Expr) extends Expr
        case class Unary(c: Expr) extends Expr
        case class Constant(x: Char) extends Expr

        object Binary extends ParserBridge2[Constant, Expr, Expr]
        object Unary extends ParserBridge1[Expr, Expr]
        object Constant extends ParserBridge1[Char, Constant]

        val p = mixed.right1(Constant(digit), Unary <# '-', Binary <# '+')
        p.parse("-1+-2+-3") shouldBe Success(Unary(Binary(Constant('1'), Unary(Binary(Constant('2'), Unary(Constant('3')))))))
    }

    they should "allow the mixing of postfix with infix-left" in {
        sealed trait Expr
        case class Binary(l: Expr, r: Constant) extends Expr
        case class Unary(c: Expr) extends Expr
        case class Constant(x: Char) extends Expr

        object Binary extends ParserBridge2[Expr, Constant, Expr]
        object Unary extends ParserBridge1[Expr, Expr]
        object Constant extends ParserBridge1[Char, Constant]

        val p = mixed.left1(Constant(digit), Unary <# '?', Binary <# '+')
        p.parse("1?+2?+3?") shouldBe Success(Unary(Binary(Unary(Binary(Unary(Constant('1')), Constant('2'))), Constant('3'))))
    }
}
