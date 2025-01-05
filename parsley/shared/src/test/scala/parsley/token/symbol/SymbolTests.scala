/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.symbol

import Predef.{ArrowAssoc => _, _}

import parsley._
import parsley.token.LexemeImpl._

import parsley.token.descriptions._
import parsley.token.errors.ErrorConfig
import parsley.character.{spaces, string}
import org.scalactic.source.Position
import parsley.token.{Basic, Unicode}

class SymbolTests extends ParsleyTest {
    val errConfig = new ErrorConfig {
        override def labelSymbol = Map(
            ("keyword", parsley.token.errors.Reason("bla bla"))
        )
    }
    def makeSymbol(nameDesc: NameDesc, symDesc: SymbolDesc): Symbol = new LexemeSymbol(new ConcreteSymbol(nameDesc, symDesc, errConfig), spaces)

    val plainName = NameDesc.plain.copy(identifierLetter = Basic(_.isLetter), operatorLetter = Basic(':'))
    val plainSym = SymbolDesc.plain.copy(hardKeywords = Set("keyword", "hard"), hardOperators = Set("+", "<", "<="))

    val plainSymbol = makeSymbol(plainName, plainSym)
    val unicodeSymbol = makeSymbol(plainName.copy(identifierLetter = Unicode(Character.isAlphabetic(_))), plainSym)
    val caseInsensitive = makeSymbol(plainName, plainSym.copy(caseSensitive = false))
    val caseInsensitiveUni = makeSymbol(plainName.copy(identifierLetter = Unicode(Character.isAlphabetic(_))), plainSym.copy(caseSensitive = false))

    def boolCases(p: Parsley[Unit])(tests: (String, Boolean, Position)*): Unit = cases(p, noEof = true)(tests.map { case (i, r, pos) => (i, if (r) Some(()) else None, pos) }: _*)
    def namedCases(sym: String => Parsley[Unit])(ktests: (String, Seq[(String, Boolean, Position)])*): Unit = {
        for ((key, tests) <- ktests) boolCases(sym(key))(tests: _*)
    }

    def keyCases(sym: Symbol)(ktests: (String, Seq[(String, Boolean, Position)])*): Unit = namedCases(sym.softKeyword)(ktests: _*)
    def opCases(sym: Symbol)(ktests: (String, Seq[(String, Boolean, Position)])*): Unit = namedCases(sym.softOperator)(ktests: _*)

    // ident
    "soft keywords" should "parse even when not in the keyword set" in keyCases(plainSymbol)(
        "hello".-->(
            "hello" -> true,
            "hello!" -> true,
            "hell" -> false,
            "helloworld" -> false,
            "Hello" -> false,
        ),
        "hard".-->(
            "hard" -> true,
            "hard1" -> true,
            "hardy" -> false,
            "hard " -> true,
            "hard water" -> true,
        ),
        "Χαίρετε".-->(
            "Χαίρετε" -> true,
            "Χαίρετεα" -> false,
        ),
        "🙂🙂🙂".-->(
            "🙂🙂🙂" -> true,
            "🙂🙂🙂a" -> false
        ),
    )

    they should "parse full utf-16" in {
        keyCases(unicodeSymbol)(
            "hello".-->(
                "hello" -> true,
                "hello!" -> true,
                "hell" -> false,
                "helloworld" -> false,
                "Hello" -> false,
            ),
            "hard".-->(
                "hard" -> true,
                "hard1" -> true,
                "hardy" -> false,
                "hard " -> true,
                "hard water" -> true,
            ),
        )
        keyCases(makeSymbol(plainName.copy(identifierLetter = Unicode(Set(0x1F642))), plainSym))(
            "hello".-->(
                "hello🙂" -> false,
                "hello 🙂" -> true,
                "hello🙃" -> true,
            ),
        )
    }

    they should "be able to be case-insensitive" in {
        keyCases(caseInsensitive)(
            "hello".-->(
                "hello" -> true,
                "Hello" -> true,
                "heLLo" -> true,
                "hell" -> false,
            ),
            "hell0".-->(
                "hell0" -> true,
                "hEll0" -> true,
                "hel0" -> false,
            ),
            "HELLO".-->(
                "hello" -> true,
                "hallo" -> false,
            ),
            "🙂🙂🙂".-->(
                "🙂🙂🙂" -> true
            ),
        )
        keyCases(caseInsensitiveUni)(
            "hello".-->(
                "hello" -> true,
                "Hello" -> true,
                "heLLo" -> true,
                "hell" -> false,
            ),
            "hell0".-->(
                "hell0" -> true,
                "hEll0" -> true,
                "hel0" -> false,
            ),
            "HELLO".-->(
                "hello" -> true,
                "hallo" -> false,
            ),
        )
    }

    they should "not consumed input when they fail" in {
        boolCases(plainSymbol.softKeyword("if") <|> string("iffy").void)(
            "iffy" -> true
        )
        boolCases(unicodeSymbol.softKeyword("if") <|> string("iffy").void)(
            "iffy" -> true
        )
    }

    they should "not be affected by tablification optimisation" in {
        boolCases(caseInsensitive.softKeyword("hi") <|> caseInsensitive.softKeyword("HELLo") <|> caseInsensitive.softKeyword("BYE"))(
            "bye" -> true,
            "Bye" -> true,
            "hi" -> true,
            "hello" -> true,
        )
    }

    "soft operators" should "parse even when not in the operators set" in opCases(plainSymbol)(
        "<".-->(
            "<" -> true,
            "<=" -> false,
            "<+" -> true,
        ),
        "+".-->(
            "+" -> true,
            "+<" -> true,
            "+:" -> false,
        ),
        "::".-->(
            "::" -> true,
            ":" -> false,
            ":::" -> false,
            "::+" -> true,
            ":: :" -> true,
        )
    )

    they should "not consume input when they fail" in {
        boolCases(plainSymbol.softOperator("++") <|> string("+").void)(
            "+" -> true,
        )

        boolCases(plainSymbol.softOperator("+") <|> string("+:").void)(
            "+:" -> true,
        )
    }

    "symbols" should "be parsed according to category" in {
        import plainSymbol.implicits._
        boolCases("keyword")(
            "keyword" -> true,
            "keyworda" -> false,
            "keyword a" -> true,
        )
        boolCases("<")(
            "<" -> true,
            "<=" -> false,
            "+" -> false,
            "<7" -> true,
        )
        boolCases("hello")(
            "helloworld" -> true,
        )
        boolCases(plainSymbol(';'))(";" -> true)
        boolCases(plainSymbol("if", "label"))("if" -> true)
        boolCases(plainSymbol(';', "label"))(";" -> true)
    }
}
