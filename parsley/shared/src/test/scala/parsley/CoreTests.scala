/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import Predef.{ArrowAssoc => _, _}

import parsley.Parsley.{empty => pempty, _}
import parsley.combinator.ifS
import parsley.lift._
import parsley.character.{char, satisfy, digit, item, string}
import parsley.syntax.character.{charLift, stringLift}
import parsley.syntax.all._
import parsley.state._
import parsley.errors.combinator.{fail => pfail}

class CoreTests extends ParsleyTest {
    private val add: (Int, Int) => Int = _+_
    private val add1 = (x: Int) => x + 1
    private val mult5 = (x: Int) => x * 5
    private val toUpper = (c: Char) => c.toUpper

    "Character parsers" should "fail on no input" in {
        'a'.parse("") should not be a [Success[_]]
    }

    they should "fail when given incorrect input" in {
        'a'.parse("b") should not be a [Success[_]]
    }

    they should "succeed when given correct input" in {
        'a'.parse("a") should be (Success('a'))
    }

    they must "only consume a single character of input at most" in {
        var res = (satisfy(_ == 'a') *> 'b').parse("aaaaaa")
        res should matchPattern { case Failure(TestError((1, 2), _)) => }
        res = ('a' ~> 'b').parse("bc")
        res should matchPattern { case Failure(TestError((1, 1), _)) => }
    }

    "Pure parsers" should "not require input" in {
        unit.parse("") should not be a [Failure[_]]
    }

    they must "result in their correct value" in {
        pure(42).parse("") should be (Success(42))
        pure("hello world").parse("") should be (Success("hello world"))
        pure(add).parse("") should be (Success(add))
    }

    they must "not consume any input" in {
        (pure('a') <~ 'a').parse("a") should be (Success('a'))
    }

    "Fresh parsers" should "generate unique objects on every run" in {
        val p = fresh(new Object)
        p.parse("") should not be p.parse("")
    }

    // APPLICATIVE LAWS
    they must "obey the homomorphism law: pure f <*> pure x = pure (f x)" in {
        (pure(add1) <*> pure(42)).parse("") should be (Success(43))
        (pure(42) <**> pure(add1)).parse("") should be (Success(43))
    }

    they must "obey the fmap law: pure f <*> p = f <$> p" in {
        (pure(toUpper) <*> 'a').parse("a") should equal ((toUpper.lift('a')).parse("a"))
        (pure(toUpper) <*> ('a' <|> 'b')).parse("a") should equal (('a' <|> 'b').map(toUpper).parse("a"))
        (pure(toUpper) <*> ('a' <|> 'b')).parse("b") should equal (('a' <|> 'b').map(toUpper).parse("b"))
    }

    they must "obey the interchange law: u <*> pure x = ($x) <$> u" in {
        (('a' #> add1) <*> pure(41)).parse("a") should equal (('a' #> add1).map(_(41)).parse("a"))
        ((('a' <|> 'b') #> add1) <*> pure(41)).parse("a") should equal ((('a' <|> 'b') #> add1).map(_(41)).parse("a"))
        ((('a' <|> 'b') #> add1) <*> pure(41)).parse("b") should equal ((('a' <|> 'b') #> add1).map(_(41)).parse("b"))
    }

    they must "obey the composition law: pure (.) <*> u <*> v <*> w = u <*> (v <*> w)" in {
        val u: Parsley[Int => Int] = 'a' #> add1
        val v: Parsley[Int => Int] = 'b' #> mult5
        val w: Parsley[Int] = 'c' #> 7
        val compose: (Int => Int) => (Int => Int) => Int => Int = f => g => f.compose(g)
        (pure(compose) <*> u <*> v <*> w).parse("abc") should equal ((u <*> (v <*> w)).parse("abc"))
    }

    "lift22" must "work correctly (and by extension 21-3)" in {
        val p = lift.lift22[Char, Char, Char, Char, Char, Char, Char, Char, Char, Char, Char,
                            Char, Char, Char, Char, Char, Char, Char, Char, Char, Char, Char, String](
            (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v) => s"$a$b$c$d$e$f$g$h$i$j$k$l$m$n$o$p$q$r$s$t$u$v",
            item, item, item, item, item, item, item, item, item, item, item,
            item, item, item, item, item, item, item, item, item, item, item)
        p.parse("abcdefghijklmnopqrstuv") shouldBe Success("abcdefghijklmnopqrstuv")
    }

    // SELECTIVE LAWS
    "Selective parsers" must "obey the selective left-branch law" in {
        branch[Int, Int, Int](pure(Left(7)), pure(_+1), pure(_-1)).parse("") shouldBe Success(8)
    }
    they must "obey the selective right-branch law" in {
        select(pure(Right(7)), Parsley.empty).parse("") shouldBe Success(7)
    }
    they must "obey the fold law" in {
        val p = ('a' #> 7) <+> ('b' #> 6)
        branch[Int, Int, Int](p, pure(_+1), pure(_-1)).parse("a") shouldBe Success(8)
        branch[Int, Int, Int](p, pure(_+1), pure(_-1)).parse("b") shouldBe Success(5)
    }

    // MONAD LAWS
    "Monadic parsers" must "obey the left identity law: pure x >>= f = f x" in {
        (pure('a') flatMap char).parse("a") should equal ('a'.parse("a"))
    }
    they must "obey the right identity law: m >>= pure = m" in {
        ('a' flatMap pure).parse("a") should equal ('a'.parse("a"))
    }
    they must "obey the associativity law: (m >>= f) >>= g = m >>= (x => f x >>= g)" in {
        val f: Int => Parsley[Int] = x => pure(x + 1)
        val g: Int => Parsley[Int] = x => pure(x/3)
        val m = '1' #> 4
        ((m flatMap f) flatMap g).parse("1") should equal ((m flatMap (x => f(x) flatMap g)).parse("1"))
    }
    they must "allow for flattening" in {
        pure(char('a')).flatten.parse("a") shouldBe Success('a')
        Parsley.empty.flatten.parse("") shouldBe a [Failure[_]]
    }

    "branch" must "work correctly for non-pure components" in {
        val p = ('a' #> 7) <+> ('b' #> 6)
        val q = ('+'.#>[Int => Int](_ + 1) <|> '-'.#>[Int => Int](_ - 1))
        cases(branch[Int, Int, Int](p, q, q))(
            "a+" -> Some(8),
            "b+" -> Some(7),
            "a-" -> Some(6),
            "b-" -> Some(5),
        )
    }

    "<|>" should "not try the second alternative if the first succeeded" in {
        ('a' | pfail("wrong!")).parse("a") shouldBe Success('a')
    }
    it should "only try second alternative if the first failed without consuming input" in {
        ('a' <+> 'b').parse("b") shouldBe Success(Right('b'))
    }
    it should "not try the second alternative if the first failed after consuming input" in {
        ("ab" | "ac").parse("ac") shouldBe a [Failure[_]]
    }
    it should "not be affected by an empty on the left" in {
        inside((Parsley.empty <|> 'a').parse("b")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("b"))
                exs should contain only (Raw("a"))
                rs shouldBe empty
        }
    }

    "atomic" should "cause <|> to try second alternative even if input consumed" in {
        atomic("ab").orElse("ac").parse("ac") should not be a [Failure[_]]
    }

    "notFollowedBy" must "succeed if p fails" in {
        notFollowedBy('a').parse("") should be (Success(()))
    }
    it must "succeed even if p consumed input" in {
        notFollowedBy("aa").parse("a") should be (Success(()))
    }
    it must "fail if p succeeds" in {
        notFollowedBy('a').parse("a") shouldBe a [Failure[_]]
    }
    it must "behave like empty if provided a parser that consumes no input" in {
        notFollowedBy(unit).parse("abc") shouldBe pempty.parse("abc")
        notFollowedBy(unit).parse("") shouldBe pempty.parse("")
    }

    "lookAhead" should "consume no input on success" in {
        lookAhead('a').parse("a") should not be a [Failure[_]]
        inside((lookAhead('a') *> 'b').parse("ab")) {
            case Failure(TestError((1, 1), VanillaError(unex, exs, rs, 1))) =>
                unex should contain (Raw("a"))
                exs should contain only (Raw("b"))
                rs shouldBe empty
        }
    }
    it must "fail when input is consumed, and input is consumed" in {
        lookAhead("ab").parse("ac") shouldBe a [Failure[_]]
    }
    /*it should "not affect the state of the registers on success" in {
        val r1 = Reg.make[Int]
        (r1.set(5) *> lookAhead(r1.set(7) *> 'a') *> r1.get).parse("a") should be {
            Success(5)
        }
        (r1.set(5) *> (lookAhead(r1.set(7) *> 'a') <|> 'b') *> r1.get).parse("b") should be {
            Success(7)
        }
    }*/

    "many" should "crash when given a parser that does not consume input" in {
       an [Exception] should be thrownBy many(pure(5)).parse("")
    }

    "stateful parsers" should "allow for persistent state" in {
        val p = 5.makeRef { r1 =>
            7.makeRef { r2 =>
                r1.set(lift2[Int, Int, Int](_+_, r1.get, r2.get)) *> (r1.get zip r2.gets(_+1))
            }
        }
        p.parse("") should be (Success((12, 8)))
    }
    they should "be modifiable" in {
        val p = 5.makeRef(r1 => r1.update(_+1) *> r1.get)
        p.parse("") should be (Success(6))
    }
    they should "provide localised context" in {
        val r1 = Ref.make[Int]
        val p = r1.set(5) *> (r1.updateDuring(_+1)(r1.get) zip r1.get)
        val q = r1.set(5) *> (r1.setDuring(6)(r1.get) <~> r1.get)
        p.parse("") should be (Success((6, 5)))
        q.parse("") should be (Success((6, 5)))
    }
    they should "be correctly allocated when found inside recursion" in {
        val r1 = Ref.make[Int]
        lazy val rec: Parsley[Unit] = char('a') *> r1.set(1) *> rec <|> unit
        val p = "hello :)".makeRef(r2 => rec *> r2.get)
        p.parse("a") shouldBe Success("hello :)")
    }
    they should "be correctly allocated when found inside sub-routines" in {
        val r1 = Ref.make[Int]
        val q = char('a') *> r1.set(1)
        val p = "hello :)".makeRef(r2 => q *> q *> r2.get)
        p.parse("aa") shouldBe Success("hello :)")
    }
    they should "be preserved by callee-save in flatMap" in {
        val p = "hello world".makeRef(r2 => {
            6.makeRef(r1 => {
                unit.flatMap(_ => 4.makeRef(_ => r2.set("hi"))) *>
                (r1.get <~> r2.get)
            })
        })
        p.parse("") shouldBe Success((6, "hi"))
    }
    they should "be preserved by callee-save in flatMap even when it fails" in {
        val p = "hello world".makeRef(r2 => {
            6.makeRef(r1 => {
                combinator.optional(unit.flatMap(_ => r2.set("hi") *> 4.makeRef(_ => Parsley.empty))) *>
                (r1.get zip r2.get)
            })
        })
        p.parse("") shouldBe Success((6, "hi"))
    }
    they should "be able to be rolled back if they fail softly" in {
        val p = 3.makeRef(r1 => (r1.rollback(r1.set(2) *> Parsley.empty) <|> unit) *> r1.get)
        p.parse("") shouldBe Success(3)
    }
    they should "but not roll back if they hard fail" in {
        val p = 3.makeRef(r1 => (atomic(r1.rollback('a' *> r1.set(2) *> Parsley.empty)) <|> unit) *> r1.get)
        p.parse("a") shouldBe Success(2)
    }
    they should "not rollback if successful" in {
        val p = 3.makeRef(r1 => r1.rollback(r1.set(2)) *> r1.get)
        p.parse("") shouldBe Success(2)
    }
    they should "support more than 4 registers" in {
        def loop(n: Int): Parsley[List[Char]] = {
            if (n == 0) pure(Nil)
            else item.fillRef { c => c.get <::> loop(n-1) }
        }
        val p = loop(16)
        p.parse("abcdefghijklmnop") shouldBe Success(List('a', 'b', 'c','d', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p'))
    }

    "fillReg" should "appear to create a fresh register every time its invoked" in {
        def inc(reg: Ref[Int]): Parsley[Int] = reg.get <* reg.update(_ + 1)
        val p = 0.makeRef { i =>
            // the register j is static, however, each recursive call should save its value, making it appear dynamic
            lazy val p: Parsley[List[Int]] = char('a') *> inc(i).fillRef(j => (p, j.get).zipped(_ :+ _) <* char('b')) <|> pure(Nil)
            p
        }
        p.parse("aaabbb") shouldBe Success(List(2, 1, 0))
    }

    it should "also appear to create a fresh register even in the presence of a hard failure" in {
        lazy val p: Parsley[Char] = item.fillRef(c => item *> (atomic(p) <|> c.get))
        p.parse("abc") shouldBe Success('a')
    }

    "ternary parsers" should "function correctly" in {
        val p = ifS(pure(true), 'a', 'b')
        p.parse("a") should be (Success('a'))
        val q = ifS(pure(false), 'a', 'b')
        q.parse("b") should be (Success('b'))
        val r = ifS(item.map(_.isLower), 'a', 'b')
        r.parse("aa") should be (Success('a'))
        r.parse("Ab") should be (Success('b'))
    }

    "filtered parsers" should "function correctly" in {
        val p = item.filterNot(_.isLower)
        p.parse("a") shouldBe a [Failure[_]]
        p.parse("A") shouldBe Success('A')
    }

    "the collect combinator" should "act like a filter then a map" in {
        val p = item.collect[Int] {
            case '+' => 0
            case c if c.isUpper => c - 'A' + 1
        }
        p.parse("+") shouldBe Success(0)
        p.parse("C") shouldBe Success(3)
        p.parse("a") shouldBe a [Failure[_]]
    }

    "foldRight" should "work correctly" in {
        val p = 'a'.foldRight[List[Char]](Nil)(_::_)

        p.parse("") should be (Success(Nil))
        p.parse("aaa") should be (Success(List('a', 'a', 'a')))
    }
    "foldRight1" should "work correctly" in {
        val p = 'a'.foldRight1[List[Char]](Nil)(_::_)
        p.parse("") shouldBe a [Failure[_]]
        p.parse("aaa") should be (Success(List('a', 'a', 'a')))
    }

    "foldLeft" should "work correctly" in {
        val p = digit.foldLeft(0)((x, d) => x * 10 + d.asDigit)

        p.parse("") should be (Success(0))
        p.parse("123") should be (Success(123))
    }
    "foldLeft1" should "work correctly" in {
        val p = digit.foldLeft1(0)((x, d) => x * 10 + d.asDigit)
        p.parse("") shouldBe a [Failure[_]]
        p.parse("123") should be (Success(123))
    }
    "reduceRightOption" should "return Some on success" in {
        val p = string("a").reduceRightOption((s1, s2) => s"$s1($s2)")
        p.parse("aaaaa") shouldBe Success(Some("a(a(a(a(a))))"))
    }
    it should "return None if there is no p" in {
        val p = string("a").reduceRightOption((s1, s2) => s"$s1($s2)")
        p.parse("") shouldBe Success(None)
    }
    "reduceLeftOption" should "return Some on success" in {
        val p = string("a").reduceLeftOption((s1, s2) => s"($s1)$s2")
        p.parse("aaaaa") shouldBe Success(Some("((((a)a)a)a)a"))
    }
    it should "return None if there is no p" in {
        val p = string("a").reduceLeftOption((s1, s2) => s"($s1)$s2")
        p.parse("") shouldBe Success(None)
    }

    "stack overflows" should "not be thrown by recursive parsers" in {
        lazy val p: Parsley[Int] = 'b' *> p
        def many_[A](p: Parsley[A]): Parsley[List[A]] = {
            lazy val manyp: Parsley[List[A]] = (p <::> manyp) </> Nil
            manyp
        }
        noException should be thrownBy many_('a' *> p).parse("")
    }
    they should "not be caused by bind optimisation" in {
        lazy val uhoh: Parsley[Unit] = 'a'.flatMap(_ => uhoh)
        noException should be thrownBy uhoh.parse("a")
    }

    "lazy parsley" should "be able to prevent overly strict combinators" in {
        import parsley.combinator.{sequence, optional}
        // without the ~, this would loop forever as skip is strict in position 2
        lazy val fishySkipMany: Parsley[Unit] = optional(sequence('a', ~fishySkipMany))
        (fishySkipMany *> 'b').parse("aaaaaab") shouldBe Success('b')
    }

    // This is commented out because it was breaking on scala-js
    /*they should "not occur with very large parsers either" in {
        import parsley.combinator
        val p = combinator.exactly(12000, 'a')
        noException should be thrownBy p.force()
    }*/

    "overflows" should "allow for forwarding via the Cont monad" in {
        val p = combinator.exactly(100, 'a')
        p.overflows()
        p.parse("a" * 100) shouldBe Success(List.fill(100)('a'))
    }

    "failures through call boundary" should "ensure that stateful instructions are restored correctly" in {
        import parsley.Parsley.some
        import parsley.combinator.whileS
        val n = Ref.make[Int]
        lazy val p: Parsley[Unit] = whileS(ifS(n.gets(_ % 2 == 0), some('a'), some('b')) *> n.update(_ - 1) *> n.gets(_ != 0))
        val q = atomic(n.set(4) *> p <* eof) | n.set(2) *> p <* eof
        q.parse("aaaabbb") shouldBe a [Success[_]]
    }

    "flatMap" should "consistently generate a callee-save instruction if needed" in {
        import parsley.state._
        val r = Ref.make[Int]
        val p = unit.flatMap { _ =>
            r.update(_ + 1) *> r.get
        }
        (unit.flatMap(_ => r.set(0)) *> p *> p).parse("") shouldBe Success(2)
    }

    "span" should "return all the input parsed by a parser, exactly as it was" in {
        import parsley.character.whitespaces
        whitespaces.span.parse("      \n\n\t\ta") shouldBe Success("      \n\n\t\t")
    }
}
