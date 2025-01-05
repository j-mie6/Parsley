/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token

import Predef.{ArrowAssoc => _, _}

import parsley.{Success, ParsleyTest}
import parsley.Parsley.atomic

import descriptions.{SpaceDesc, LexicalDesc}
import parsley.character.{string, char}
import parsley.exceptions.UnfilledReferenceException
import parsley.VanillaError
import parsley.Failure
import parsley.TestError
import parsley.Raw

class SpaceTests extends ParsleyTest {
    private def makeLexer(space: SpaceDesc) = new Lexer(LexicalDesc.plain.copy(spaceDesc = space))
    private def makeSpace(space: SpaceDesc) = makeLexer(space).space

    val basicNoComments = SpaceDesc.plain.copy(space = Basic(Character.isWhitespace(_)))
    val unicodeNoComments = basicNoComments.copy(space = Unicode(Character.isWhitespace(_)))

    "whiteSpace" should "parse spaces when no comments are defined" in cases(makeSpace(basicNoComments).whiteSpace *> string("a")) (
        "a" -> Some("a"),
        "      a" -> Some("a"),
        "\n   \ta" -> Some("a"),
        "/**/ a" -> None,
    )

    it should "supported unicode definition" in cases(makeSpace(unicodeNoComments).whiteSpace *> string("a")) (
        "a" -> Some("a"),
        "      a" -> Some("a"),
        "\n   \ta" -> Some("a"),
        "/**/ a" -> None,
    )

    val basicLine = basicNoComments.copy(lineCommentStart = "--")
    val unicodeLine = unicodeNoComments.copy(lineCommentStart = "--")

    it should "parse spaces and line comments when defined" in {
        cases(makeSpace(basicLine).whiteSpace *> string("a")) (
            "a" -> Some("a"),
            "      a" -> Some("a"),
            "\n   \ta" -> Some("a"),
            "--ab\n --hi\n a" -> Some("a"),
            "--aba" -> None,
        )
        cases(makeSpace(unicodeLine).whiteSpace *> string("a")) (
            "a" -> Some("a"),
            "      a" -> Some("a"),
            "\n   \ta" -> Some("a"),
            "--ab\n --hi\n a" -> Some("a"),
            "--aba" -> None,
        )
    }

    val basicMulti = basicNoComments.copy(multiLineCommentStart = "/*", multiLineCommentEnd = "*/")
    val unicodeMulti = unicodeNoComments.copy(multiLineCommentStart = "/*", multiLineCommentEnd = "*/")

    it should "parse spaces and multi-line comment when defined" in {
        cases(makeSpace(basicMulti).whiteSpace *> string("a")) (
            "a" -> Some("a"),
            "      a" -> Some("a"),
            "\n   \ta" -> Some("a"),
            "/*ab*/ /*hi*/ a" -> Some("a"),
            "/*aba" -> None,
        )
        cases(makeSpace(unicodeMulti).whiteSpace *> string("a")) (
            "a" -> Some("a"),
            "      a" -> Some("a"),
            "\n   \ta" -> Some("a"),
            "/*ab*/ /*hi*/ a" -> Some("a"),
            "/*aba" -> None,
        )
    }

    val basicMixed = basicNoComments.copy(lineCommentStart = "#", multiLineCommentStart = "##", multiLineCommentEnd = "##")
    val unicodeMixed = unicodeNoComments.copy(lineCommentStart = "#", multiLineCommentStart = "##", multiLineCommentEnd = "##")

    it should "parse spaces and mixed comments when defined" in {
        cases(makeSpace(basicMixed).whiteSpace *> string("a")) (
            "a" -> Some("a"),
            "      a" -> Some("a"),
            "\n   \ta" -> Some("a"),
            "##ab## #hi\n a" -> Some("a"),
            "##aba" -> None,
            "#aba" -> None,
        )
        cases(makeSpace(unicodeMixed).whiteSpace *> string("a")) (
            "a" -> Some("a"),
            "      a" -> Some("a"),
            "\n   \ta" -> Some("a"),
            "##ab## #hi\n a" -> Some("a"),
            "##aba" -> None,
            "#aba" -> None,
        )
    }

    val basicCommentsOnly = basicMixed.copy(space = NotRequired)
    val unicodeCommentsOnly = unicodeMixed.copy(space = NotRequired)

    it should "be skipComments with no whitespace allowed" in {
        val basic = makeSpace(basicCommentsOnly)
        val unicode = makeSpace(unicodeCommentsOnly)
        basic.whiteSpace shouldBe basic.skipComments
        unicode.whiteSpace shouldBe unicode.skipComments
    }

    val basicLineEOF = basicLine.copy(lineCommentAllowsEOF = true)
    val basicLineNoEOF = basicLine.copy(lineCommentAllowsEOF = false)
    val basicMixedEOF = basicMixed.copy(lineCommentAllowsEOF = true)
    val basicMixedNoEOF = basicMixed.copy(lineCommentAllowsEOF = false)

    it should "allow for line comments to end in EOF" in {
        cases(makeSpace(basicLineEOF).whiteSpace)("--hello world" -> Some(()))
        cases(makeSpace(basicMixedEOF).whiteSpace)("#hello world" -> Some(()))
    }

    it should "or not allow EOF" in {
        cases(makeSpace(basicLineNoEOF).whiteSpace)("--hello world" -> None)
        cases(makeSpace(basicMixedNoEOF).whiteSpace)("--hello world" -> None)
    }

    val basicMultiNested = basicMulti.copy(multiLineNestedComments = true)
    // having the same start and end makes this... weird
    val basicMixedNested = basicMixed.copy(multiLineNestedComments = true, multiLineCommentStart = "#-", multiLineCommentEnd = "-#")

    it should "parse nested comments when applicable" in {
        cases(makeSpace(basicMultiNested).whiteSpace) (
            "/*/**/" -> None,
            "/*/**/*/" -> Some(()),
            "/*/*hello*/world*/" -> Some(())
        )
        cases(makeSpace(basicMixedNested).whiteSpace) (
            "#-#--#" -> None,
            "#-#--#-#" -> Some(()),
            "#-#-hello-#world-#" -> Some(())
        )
    }

    val basicMultiNonNested = basicMulti.copy(multiLineNestedComments = false)
    val basicMixedNonNested = basicMulti.copy(multiLineNestedComments = false)

    it should "not parse nested comments when applicable" in {
        cases(makeSpace(basicMultiNonNested).whiteSpace) (
            "/*/**/*/" -> None,
            "/*/**/" -> Some(()),
        )
    }

    "skipComments" should "parse single-line comments" in {
        cases(makeSpace(basicLine).skipComments <* char('\n')) (
            "--hello\n" -> Some(()),
            "--hello--hello" -> None,
        )
        cases(makeSpace(unicodeLine).skipComments <* char('\n')) (
            "--hello\n" -> Some(()),
            "--hello--hello" -> None,
        )
    }
    it should "parse multi-line comments" in {
        cases(makeSpace(basicMulti).skipComments) (
            "/*hello*//*world*/" -> Some(()),
            "/*hello*/j" -> None,
            "/**/" -> Some(()),
            "/*" -> None,
            "*/" -> None,
            "/**" -> None,
        )
        cases(makeSpace(unicodeMulti).skipComments) (
            "/*hello*//*world*/" -> Some(()),
            "/*hello*/j" -> None,
            "/**/" -> Some(()),
            "/*" -> None,
            "*/" -> None,
            "/**" -> None,
        )
    }
    it should "parse mixed comments" in cases(makeSpace(basicMixed).skipComments *> string("\na")) (
        "\na" -> Some("\na"),
        "##ab###hi\na" -> Some("\na"),
        "##ab\na" -> None,
    )
    it should "parse nested comments when applicable" in {
        cases(makeSpace(basicMultiNested).skipComments) (
            "/*/**/" -> None,
            "/*/**/*/" -> Some(()),
            "/*/*hello*/world*/" -> Some(())
        )
        cases(makeSpace(basicMixedNested).skipComments) (
            "#-#--#" -> None,
            "#-#--#-#" -> Some(()),
            "#-#-hello-#world-#" -> Some(())
        )
    }
    it should "not parse nested comments when applicable" in {
        cases(makeSpace(basicMultiNonNested).skipComments) (
            "/*/**/*/" -> None,
            "/*/**/" -> Some(()),
        )
    }
    it should "do nothing with no comments" in {
        cases(makeSpace(basicMulti).skipComments)("" -> Some(()))
        cases(makeSpace(basicMixed).skipComments)("" -> Some(()))
    }

    it should "allow for line comments to end in EOF" in {
        cases(makeSpace(basicLineEOF).skipComments)("--hello world" -> Some(()))
        cases(makeSpace(basicMixedEOF).skipComments)("#hello world" -> Some(()))
    }

    it should "or not allow EOF" in {
        cases(makeSpace(basicLineNoEOF).skipComments)("--hello world" -> None)
        cases(makeSpace(basicMixedNoEOF).skipComments)("--hello world" -> None)
    }

    it should "not aggressively eat everything" in {
        val lexer1 = makeSpace(basicCommentsOnly.copy(multiLineCommentStart = "", multiLineCommentEnd = ""))
        val lexer2 = makeSpace(basicCommentsOnly.copy(lineCommentStart = ""))
        val lexer3 = makeSpace(unicodeCommentsOnly)
        (lexer1.skipComments *> char('a')).parse("a") shouldBe a [Success[_]]
        (lexer2.skipComments *> char('a')).parse("a") shouldBe a [Success[_]]
        (lexer3.skipComments *> char('a')).parse("a") shouldBe a [Success[_]]
    }

    val basicDependent = basicMixed.copy(whitespaceIsContextDependent = true)

    "context-dependent whitespace" must "be initialised" in {
        a [UnfilledReferenceException] must be thrownBy {
            makeSpace(basicDependent).whiteSpace.parse("     ")
        }
    }

    "init" should "initialise the space so it can be used" in {
        noException should be thrownBy {
            val space = makeSpace(basicDependent)
            (space.init *> space.whiteSpace).parse("    ")
        }
    }

    it should "initialise space to the default space definition" in {
        val space = makeSpace(basicDependent.copy(space = Basic(Set('a'))))
        cases(space.init *> space.whiteSpace)(
            "aaaaaa" -> Some(()),
            "aaa##hello##" -> Some(()),
        )
    }

    it should "not work if context-dependent whitespace is off" in {
        an [UnsupportedOperationException] should be thrownBy {
            makeSpace(basicMixed).init.parse("")
        }
    }

    "alter" should "not work if context-dependent whitespace is off" in {
        an [UnsupportedOperationException] should be thrownBy {
            makeSpace(basicMixed).alter(NotRequired)(char('a')).parse("")
        }
    }

    it should "temporarily alter how whitespace is parsed" in {
        val space = makeSpace(basicDependent)
        cases(space.init *> space.whiteSpace *> space.alter(Basic(Set('a'))) {
            char('b') *> space.whiteSpace *> char('b')
        } *> space.whiteSpace)(
            "bb" -> Some(()),
            "   bb   " -> Some(()),
            "ab" -> None,
            "  bba" -> None,
            "  baaaab  " -> Some(()),
        )
    }

    it should "not restore old whitespace if the given parser fails having consumed input" in {
        val space = makeSpace(basicDependent)
        val p = space.init *> (atomic(space.alter(Basic(Set('a')))(char('b') *> space.whiteSpace <* char('b'))) <|> char('b') *> space.whiteSpace)
        cases(p)(
            "baaab" -> Some(()),
            "baaaa" -> Some(()),
            "b    " -> None
        )
    }

    "fully" should "parse leading whitespace and ensure eof" in {
        cases(makeLexer(basicMixed).fully(char('a')), noEof = true)(
            "    a" -> Some('a'),
            "    ab" -> None,
        )
    }

    it should "initialise dependent whitespace" in {
        cases(makeLexer(basicDependent).fully(char('a')), noEof = true)(
            "    a" -> Some('a'),
            "    ab" -> None,
        )
    }

    "unicode spaces" should "not revive dead hints" in {
        import parsley.Parsley.{eof, many}
        val lexer = makeLexer(unicodeNoComments)
        val hintKiller = lexer.lexeme(string("!"))
        val hintMaker = many(string("a"))
        val p = hintMaker <* hintKiller <* eof
        inside(p.parse("aaaa! :(")) {
            case Failure(TestError(_, VanillaError(_, expecteds, _, _))) =>
                expecteds shouldNot contain (Raw("a"))
        }
    }
}
