/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.symbol

import parsley.{Parsley, ParsleyTest, Success, Failure}
import parsley.token.Lexeme

import parsley.token.descriptions._
import parsley.token.predicate._
import parsley.token.symbol._
import parsley.character.{spaces, string}

class SymbolTests extends ParsleyTest {
    def makeSymbol(nameDesc: NameDesc, symDesc: SymbolDesc): Symbol = new LexemeSymbol(new ConcreteSymbol(nameDesc, symDesc), spaces)

    val plainName = NameDesc.plain.copy(identifierLetter = Basic(_.isLetter), operatorLetter = Basic(Set(':')))
    val plainSym = SymbolDesc.plain.copy(hardKeywords = Set("keyword", "hard"), hardOperators = Set("+", "<", "<="))

    val plainSymbol = makeSymbol(plainName, plainSym)
    val unicodeSymbol = makeSymbol(plainName.copy(identifierLetter = Unicode(Character.isAlphabetic)), plainSym)
    val caseInsensitive = makeSymbol(plainName, plainSym.copy(caseSensitive = false))
    val caseInsensitiveUni = makeSymbol(plainName.copy(identifierLetter = Unicode(Character.isAlphabetic)), plainSym.copy(caseSensitive = false))

    def boolCases(p: Parsley[Unit])(tests: (String, Boolean)*): Unit = cases(p, noEof = true)(tests.map { case (i, r) => i -> (if (r) Some(()) else None) }: _*)
    def namedCases(sym: String => Parsley[Unit])(ktests: (String, Seq[(String, Boolean)])*): Unit = {
        for ((key, tests) <- ktests) boolCases(sym(key))(tests: _*)
    }

    def keyCases(sym: Symbol)(ktests: (String, Seq[(String, Boolean)])*): Unit = namedCases(sym.softKeyword)(ktests: _*)
    def opCases(sym: Symbol)(ktests: (String, Seq[(String, Boolean)])*): Unit = namedCases(sym.softOperator)(ktests: _*)

    // ident
    "soft keywords" should "parse even when not in the keyword set" in keyCases(plainSymbol)(
        "hello" --> (
            "hello" -> true,
            "hello!" -> true,
            "hell" -> false,
            "helloworld" -> false,
            "Hello" -> false,
        ),
        "hard" --> (
            "hard" -> true,
            "hard1" -> true,
            "hardy" -> false,
            "hard " -> true,
            "hard water" -> true,
        ),
        "Χαίρετε" --> (
            "Χαίρετε" -> true,
            "Χαίρετεα" -> false,
        )
    )

    they should "parse full utf-16" in {
        keyCases(unicodeSymbol)(
            "hello" --> (
                "hello" -> true,
                "hello!" -> true,
                "hell" -> false,
                "helloworld" -> false,
                "Hello" -> false,
            ),
            "hard" --> (
                "hard" -> true,
                "hard1" -> true,
                "hardy" -> false,
                "hard " -> true,
                "hard water" -> true,
            ),
        )
        keyCases(makeSymbol(plainName.copy(identifierLetter = Unicode(Set(0x1F642))), plainSym))(
            "hello" --> (
                "hello🙂" -> false,
                "hello 🙂" -> true,
                "hello🙃" -> true,
            ),
        )
    }

    they should "be able to be case-insensitive" in {
        keyCases(caseInsensitive)(
            "hello" --> (
                "hello" -> true,
                "Hello" -> true,
                "heLLo" -> true,
                "hell" -> false,
            ),
            "hell0" --> (
                "hell0" -> true,
                "hEll0" -> true,
                "hel0" -> false,
            ),
            "HELLO" --> (
                "hello" -> true,
                "hallo" -> false,
            ),
        )
        keyCases(caseInsensitiveUni)(
            "hello" --> (
                "hello" -> true,
                "Hello" -> true,
                "heLLo" -> true,
                "hell" -> false,
            ),
            "hell0" --> (
                "hell0" -> true,
                "hEll0" -> true,
                "hel0" -> false,
            ),
            "HELLO" --> (
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
        "<" --> (
            "<" -> true,
            "<=" -> false,
            "<+" -> true,
        ),
        "+" --> (
            "+" -> true,
            "+<" -> true,
            "+:" -> false,
        ),
        "::" --> (
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
