package parsley

import parsley.combinator.eof
import parsley.Parsley._
import parsley.implicits.{charLift, stringLift}
import parsley.character.{anyChar, digit}
import parsley.unsafe.ErrorLabel

import scala.language.implicitConversions

class ErrorMessageTests extends ParsleyTest {
    //TODO: Bind tests
    lazy val r: Parsley[List[String]] = "correct error message" <::> r
    /"label" should "affect base error messages" in {
        ('a' ? "ay!").runParser("b") should be (Failure("(line 1, column 1):\n  unexpected \"b\"\n  expected ay!\n  >b\n  >^"))
    }
    //FIXME: This test doesn't actually do the right thing anymore, because label acts differently
    it should "work across a recursion boundary" in {
        println(r.unsafeLabel("sup").internal.prettyAST)
        (r.unsafeLabel("nothing but this :)")).runParser("") should be {
            Failure("(line 1, column 1):\n  unexpected end of input\n  expected nothing but this :)\n  >\n  >^")
        }
        (r.unsafeLabel("nothing but this :)")).runParser("correct error messagec") should be {
            Failure("(line 1, column 23):\n  unexpected end of input\n  expected nothing but this :)\n  >correct error messagec\n  >                     ^")
        }
    }

    "fail" should "yield a raw message" in {
        Parsley.fail("hi").runParser("b") should be {
            Failure("(line 1, column 1):\n  hi\n  >b\n  >^")
        }
    }
    // Not anymore it doesn't!
    /*it should "produce an expected message under influence of ?, along with original message" in {
        ('a' <|> (Parsley.fail("oops") ? "hi")).runParser("b") should be {
            Failure("(line 1, column 1):\n  unexpected \"b\"\n  expected \"a\" or hi\n  oops\n\n    b\n    ^")
        }
    }*/

    "unexpected" should "yield changes to unexpected messages" in {
        unexpected("bee").runParser("b") should be {
            Failure("(line 1, column 1):\n  unexpected bee\n  >b\n  >^")
        }
    }
    it should "produce expected message under influence of ?, along with original message" in {
        ('a' <|> unexpected("bee") ? "something less cute").runParser("b") should be {
            Failure("(line 1, column 1):\n  unexpected bee\n  expected \"a\" or something less cute\n  >b\n  >^")
        }
    }

    "empty" should "produce unknown error messages" in {
        Parsley.empty.runParser("b") should be {
            Failure("(line 1, column 1):\n  unknown parse error\n  >b\n  >^")
        }
    }
    it should "produce no unknown message under influence of ?" in {
        (Parsley.empty ? "something, at least").runParser("b") should be {
            Failure("(line 1, column 1):\n  expected something, at least\n  >b\n  >^")
        }
    }
    it should "not produce an error message at the end of <|> chain" in {
        ('a' <|> Parsley.empty).runParser("b") should be {
            Failure("(line 1, column 1):\n  unexpected \"b\"\n  expected \"a\"\n  >b\n  >^")
        }
    }
    it should "produce an expected error under influence of ? in <|> chain" in {
        //println(internal.instructions.pretty(('a' <|> Parsley.empty ? "something, at least").internal.instrs))
        ('a' <|> Parsley.empty ? "something, at least").runParser("b") should be {
            Failure("(line 1, column 1):\n  unexpected \"b\"\n  expected \"a\" or something, at least\n  >b\n  >^")
        }
    }

    "eof" should "produce expected end of input" in {
        eof.runParser("a") should be {
            Failure("(line 1, column 1):\n  unexpected \"a\"\n  expected end of input\n  >a\n  >^")
        }
    }
    it should "change message under influence of ?" in {
        (eof ? "something more").runParser("a") should be {
            Failure("(line 1, column 1):\n  unexpected \"a\"\n  expected something more\n  >a\n  >^")
        }
    }

    /*"error position" should "be correctly reset in" in {
        val p = attempt('a' *> digit) <|> Parsley.fail("hello :)")
        p.runParser("aa") should be {
            Failure("(line 1, column 1):\n  unexpected end of input\n  expected any character\n  hello :)")
        }
        p.runParser("c") should be {
            Failure("")
        }
    }*/
}
