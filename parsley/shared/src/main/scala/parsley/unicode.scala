/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import parsley.Parsley.{empty, fresh, pure}
import parsley.combinator.{skipMany, skipSome}
import parsley.errors.combinator.ErrorMethods
import parsley.token.errors.{Label, LabelConfig, NotConfigured}

import parsley.internal.deepembedding.singletons

// TODO: document
/** This module contains many parsers to do with reading one or more characters. Almost every parser will need something from this module.
  *
  * In particular, this module contains: combinators that can read specific characters; combinators that represent character classes and their negations;
  * combinators for reading specific strings; as well as a selection of pre-made parsers to parse specific kinds of character, like digits and letters.
  *
  * @since 4.4.0
  *
  * @groupprio pred 100
  * @groupname pred Character Predicates
  * @groupdesc pred
  *     These are useful for providing to the sub-descriptions of a [[token.descriptions.LexicalDesc]] to specify behaviour for the lexer.
  *     Other than that, they aren't ''particularly'' useful.
  *
  * @groupprio core 0
  * @groupname core Core Combinators and Parsers
  * @groupdesc core
  *     These are the most primitive combinators for consuming input capable of any input reading tasks.
  *
  * @groupprio skip 75
  * @groupname skip Whitespace Skipping Parsers
  * @groupdesc skip
  *     These parsers are designed to skip chunks of whitespace, for very rudimentary lexing tasks. It
  *     is probably better to use the functionality of [[parsley.token]].
  *
  * @groupprio class 20
  * @groupname class Character Class Combinators
  * @groupdesc class
  *     These combinators allow for working with ''character classes''. This means that a set, or range, of
  *     characters can be specified, and the combinator will return a parser that matches one of those characters
  *     (or conversely, any character that is ''not'' in that set). The parsed character is always returned.
  *
  * @groupprio spec 25
  * @groupname spec Specific Character Parsers
  * @groupdesc spec
  *     These parsers are special cases of [[satisfy `satisfy`]] or [[char `char`]]. They are worth using, as they are given special error labelling,
  *     producing nicer error messages than their primitive counterparts.
  *
  *     This documentation assumes JDK 17.
  *     JDK 17 is compliant with [[https://www.unicode.org/versions/Unicode13.0.0/UnicodeStandard-13.0.pdf Unicode® Specification 13.0]].
  *     As such, the descriptions of the parsers in this section are accurate with respect to Unicode® Specification 13.0:
  *     using a different JDK may affect the ''precise'' definitions of the parsers below. If in doubt, check the documentation
  *     for `java.lang.Character` to see which Unicode version is supported by your JVM. A table of the Unicode versions
  *     up to JDK 17 can be found [[https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html here]].
  *
  *     These parsers are able to parse unicode code points in the range `'\u0000'` to `'\u1ffff'`. Unicode characters consisting
  *     of multiple code points/modifiers should be parsed using `string`.
  *
  * @groupprio string 22
  * @groupname string String Combinators
  * @groupdesc string
  *     These combinators allow for working with, or building, strings. This means that they can
  *     parse specific strings, specific sets of strings, or can read characters repeatedly to
  *     generate strings. They are united in all returning `String` as their result.
  *
  * @define oneOf
  *     This combinator tries to parse any character from supplied set of characters `cs`, returning it if successful.
  * @define noneOf
  *     This combinator tries to parse any character '''not''' from supplied set of characters `cs`, returning it if successful.
  *
  * @define categories
  *     ''The full list of codepoints found in a category can be found in the
  *     [[https://www.unicode.org/Public/13.0.0/ucd/extracted/DerivedGeneralCategory.txt Unicode Character Database]]''.
  */
object unicode {
    /** This combinator tries to parse a single specific codepoint `c` from the input.
      *
      * Like [[character.char `character.char`]], except it may consume two characters from the input,
      * in the case where the code-point is greater than `0xffff`. This is parsed ''atomically''
      * so that no input is consumed if the first half of the codepoint is parsed and the second
      * is not.
      *
      * @example {{{
      * scala> import parsley.unicode.char
      * scala> char(0x1f643).parse("")
      * val res0 = Failure(..)
      * scala> char(0x1f643).parse("🙂")
      * val res1 = Success(0x1F643)
      * scala> char(0x1f643).parse("b🙂")
      * val res2 = Failure(..)
      * }}}
      *
      * @param c the code-point to parse
      * @return
      * @group core
      */
    def char(c: Int): Parsley[Int] = char(c, NotConfigured)
    private def char(c: Int, label: String): Parsley[Int] = char(c, Label(label))
    private def char(c: Int, label: LabelConfig): Parsley[Int] = {
        if (Character.isBmpCodePoint(c)) character.char(c.toChar).as(c)
        else new Parsley(new singletons.SupplementaryCharTok(c, label))
    }

    // TODO: document, test
    def satisfy(pred: Int => Boolean): Parsley[Int] = satisfy(pred, NotConfigured)
    private def satisfy(pred: Int => Boolean, label: String): Parsley[Int] = satisfy(pred, Label(label))
    private def satisfy(pred: Int => Boolean, label: LabelConfig) = new Parsley(new singletons.UniSatisfy(pred, label))

    // TODO: document, test
    def satisfyMap[A](pred: PartialFunction[Int, A]): Parsley[A] = satisfy(pred.isDefinedAt(_)).map(pred)

    // This should always just match up, so no need to test
    // $COVERAGE-OFF$
    /** This combinator attempts to parse a given string from the input, and fails otherwise.
      *
      * Attempts to read the given string ''completely'' from the input at the current position.
      * If the string is present, then the parser succeeds, and the entire string is consumed
      * from the input. Otherwise, if the input has too few characters remaining, or not all
      * the characters matched, the parser fails. On failure, '''all''' the characters that were
      * matched are consumed from the input.
      *
      * @example {{{
      * scala> import parsley.character.string
      * scala> string("abc").parse("")
      * val res0 = Failure(..)
      * scala> string("abc").parse("abcd")
      * val res1 = Success("abc")
      * scala> string("abc").parse("xabc")
      * val res2 = Failure(..)
      * }}}
      *
      * @param s the string to be parsed from the input
      * @return a parser that either parses the string `s` or fails at the first mismatched character.
      * @note the error messages generated by `string` do not reflect how far into the input it managed
      *       to get: this is because the error being positioned at the start of the string is more
      *       natural. However, input '''will''' still be consumed for purposes of backtracking.
      * @note just an alias for [[character.string `character.string`]], to allow for more ergonomic imports.
      * @group string
      */
    def string(s: String): Parsley[String] = character.string(s)
    // $COVERAGE-ON$

    /** $oneOf
      *
      * If the next character in the input is a member of the set `cs`, it is consumed
      * and returned. Otherwise, no input is consumed and the combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.oneOf
      * scala> val p = oneOf(Set('a', 'b', 'c'))
      * scala> p.parse("a")
      * val res0 = Success('a')
      * scala> p.parse("c")
      * val res1 = Success('c')
      * scala> p.parse("xb")
      * val res2 = Failure(..)
      * }}}
      *
      * @param cs the set of characters to check.
      * @return a parser that parses one of the member of the set `cs`.
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    def oneOf(cs: Set[Int]): Parsley[Int] = cs.size match {
        case 0 => empty
        case 1 => char(cs.head)
        case _ => satisfy(cs, {
            val Some(label) = parsley.errors.helpers.disjunct(cs.map(renderChar).toList, oxfordComma = true): @unchecked
            s"one of $label"
        })
    }

    /** $oneOf
      *
      * If the next character in the input is an element of the list of characters `cs`, it is consumed
      * and returned. Otherwise, no input is consumed and the combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.oneOf
      * scala> val p = oneOf('a', 'b', 'c')
      * scala> p.parse("a")
      * val res0 = Success('a')
      * scala> p.parse("c")
      * val res1 = Success('c')
      * scala> p.parse("xb")
      * val res2 = Failure(..)
      * }}}
      *
      * @param cs the characters to check.
      * @return a parser that parses one of the elements of `cs`.
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    def oneOf(cs: Int*): Parsley[Int] = oneOf(cs.toSet)

    /** $oneOf
      *
      * If the next character in the input is within the range of characters `cs`, it is consumed
      * and returned. Otherwise, no input is consumed and the combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.oneOf
      * scala> val p = oneOf('a' to 'c')
      * scala> p.parse("a")
      * val res0 = Success('a')
      * scala> p.parse("b")
      * val res1 = Success('b')
      * scala> p.parse("c")
      * val res1 = Success('c')
      * scala> p.parse("xb")
      * val res2 = Failure(..)
      * }}}
      *
      * @param cs the range of characters to check.
      * @return a parser that parses a character within the range `cs`.
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    def oneOf(cs: Range): Parsley[Int] = cs.size match {
        case 0 => empty
        case 1 => char(cs.head)
        case _ if Math.abs(cs(0) - cs(1)) == 1 => satisfy(cs.contains(_),
            s"one of ${renderChar(cs.min)} to ${renderChar(cs.max)}"
        )
        case _ => satisfy(cs.contains(_))
    }

    /** $noneOf
      *
      * If the next character in the input is not a member of the set `cs`, it is consumed
      * and returned. Otherwise, no input is consumed and the combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.noneOf
      * scala> val p = noneOf(Set('a', 'b', 'c'))
      * scala> p.parse("a")
      * val res0 = Failure(..)
      * scala> p.parse("c")
      * val res1 = Failure(..)
      * scala> p.parse("xb")
      * val res2 = Success('x')
      * scala> p.parse("")
      * val res3 = Failure(..)
      * }}}
      *
      * @param cs the set of characters to check.
      * @return a parser that parses one character that is not a member of the set `cs`.
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    def noneOf(cs: Set[Int]): Parsley[Int] = cs.size match {
        case 0 => item
        case 1 => satisfy(cs.head != _, s"anything except ${renderChar(cs.head)}")
        case _ => satisfy(!cs.contains(_), {
            val Some(label) = parsley.errors.helpers.disjunct(cs.map(renderChar).toList, oxfordComma = true): @unchecked
            s"anything except $label"
        })
    }

    /** $noneOf
      *
      * If the next character in the input is not an element of the list of characters `cs`, it is consumed
      * and returned. Otherwise, no input is consumed and the combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.noneOf
      * scala> val p = noneOf('a', 'b', 'c')
      * scala> p.parse("a")
      * val res0 = Failure(..)
      * scala> p.parse("c")
      * val res1 = Failure(..)
      * scala> p.parse("xb")
      * val res2 = Success('x')
      * scala> p.parse("")
      * val res3 = Failure(..)
      * }}}
      *
      * @param cs the set of characters to check.
      * @return a parser that parses one character that is not an element of `cs`.
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    def noneOf(cs: Int*): Parsley[Int] = noneOf(cs.toSet)

    /** $noneOf
      *
      * If the next character in the input is outside of the range of characters `cs`, it is consumed
      * and returned. Otherwise, no input is consumed and the combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.noneOf
      * scala> val p = noneOf('a' to 'c')
      * scala> p.parse("a")
      * val res0 = Failure(..)
      * scala> p.parse("b")
      * val res1 = Failure(..)
      * scala> p.parse("c")
      * val res1 = Failure(..)
      * scala> p.parse("xb")
      * val res2 = Success('x')
      * scala> p.parse("")
      * val res3 = Failure(..)
      * }}}
      *
      * @param cs the range of characters to check.
      * @return a parser that parses a character outside the range `cs`.
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    def noneOf(cs: Range): Parsley[Int] = cs.size match {
        case 0 => item
        case 1 => satisfy(cs.head != _, s"anything except ${renderChar(cs.head)}")
        case _ if Math.abs(cs(0) - cs(1)) == 1 => satisfy(!cs.contains(_), {
            s"anything outside of ${renderChar(cs.min)} to ${renderChar(cs.max)}"
        })
        case _ => satisfy(!cs.contains(_))
    }

    // TODO: document
    def stringOfMany(pc: Parsley[Int]): Parsley[String] = {
        val pf = pure(addCodepoint(_, _))
        // Can't use the regular foldLeft here, because we need a fresh StringBuilder each time.
        expr.infix.secretLeft1(fresh(new StringBuilder), pc, pf).map(_.toString)
    }

    // TODO: document, test
    def stringOfMany(pred: Int => Boolean): Parsley[String] = skipMany(satisfy(pred)).span//stringOfMany(satisfy(pred))

    // TODO: document
    def stringOfSome(pc: Parsley[Int]): Parsley[String] = {
        val pf = pure(addCodepoint(_, _))
        // Can't use the regular foldLeft1 here, because we need a fresh StringBuilder each time.
        expr.infix.secretLeft1(pc.map(addCodepoint(new StringBuilder, _)), pc, pf).map(_.toString)
    }

    // TODO: document, test
    def stringOfSome(pred: Int => Boolean): Parsley[String] = skipSome(satisfy(pred)).span//stringOfSome(satisfy(pred))

    // These should always just match up, so no need to test
    // $COVERAGE-OFF$
    /** This combinator tries to parse each of the strings `strs` (and `str0`), until one of them succeeds.
      *
      * Unlike `choice`, or more accurately `attemptChoice`, this combinator will not
      * necessarily parse the strings in the order provided. It will favour strings that have another string
      * as a prefix first, so that it has ''Longest Match'' semantics. It will try to minimise backtracking
      * too, making it a much more efficient option than `attemptChoice`.
      *
      * The longest succeeding string will be returned. If no strings match then the combinator fails.
      *
      * @example {{{
      * scala> import parsley.character.strings
      * scala> val p = strings("hell", "hello", "goodbye", "g", "abc")
      * scala> p.parse("hell")
      * val res0 = Success("hell")
      * scala> p.parse("hello")
      * val res1 = Success("hello")
      * scala> p.parse("good")
      * val res2 = Success("g")
      * scala> p.parse("goodbye")
      * val res3 = Success("goodbye")
      * scala> p.parse("a")
      * val res4 = Failure(..)
      * }}}
      *
      * @param str0 the first string to try to parse.
      * @param strs the remaining strings to try to parse.
      * @return a parser that tries to parse all the given strings returning the longest one that matches.
      * @note just an alias for [[parsley.character.strings(str0* `character.strings`]], to allow for more ergonomic imports.
      * @group string
      */
    def strings(str0: String, strs: String*): Parsley[String] = character.strings(str0, strs: _*)

    /** This combinator tries to parse each of the key-value pairs `kvs` (and `kv0`), until one of them succeeds.
      *
      * Each argument to this combinator is a pair of a string and a parser to perform if that string can be parsed.
      * `strings(s0 -> p0, ...)` can be thought of as `attemptChoice(string(s0) *> p0, ...)`, however, the given
      * ordering of key-value pairs does not dictate the order in which the parses are tried. In particular, it
      * will favour keys that are the prefix of another key first, so that it has ''Longest Match'' semantics.
      * it will try to minimise backtracking too, making it a much more efficient option than `attemptChoice`.
      *
      * @example {{{
      * scala> import parsley.character.strings
      * scala> val p = strings("hell" -> pure(4), "hello" -> pure(5), "goodbye" -> pure(7), "g" -> pure(1), "abc" -> pure(3))
      * scala> p.parse("hell")
      * val res0 = Success(4)
      * scala> p.parse("hello")
      * val res1 = Success(5)
      * scala> p.parse("good")
      * val res2 = Success(1)
      * scala> p.parse("goodbye")
      * val res3 = Success(7)
      * scala> p.parse("a")
      * val res4 = Failure(..)
      * }}}
      *
      * @note the scope of any backtracking performed is isolated to the key itself, as it is assumed that once a
      * key parses correctly, the branch has been committed to. Putting an `attempt` around the values will not affect
      * this behaviour.
      *
      * @param kv0 the first key-value pair to try to parse.
      * @param kvs the remaining key-value pairs to try to parse.
      * @return a parser that tries to parse all the given key-value pairs, returning the (possibly failing) result
      *         of the value that corresponds to the longest matching key.
      * @note just an alias for [[parsley.character.strings[A](kv0* `character.strings`]], to allow for more ergonomic imports.
      * @group string
      */
    def strings[A](kv0: (String, Parsley[A]), kvs: (String, Parsley[A])*): Parsley[A] = character.strings(kv0, kvs: _*)
    // $COVERAGE-ON$

    /** This parser will parse '''any''' single character from the input, failing if there is no input remaining.
      *
      * @group core
      */
    val item: Parsley[Int] = satisfy(_ => true, "any character")

    /** This parser tries to parse a space or tab character, and returns it if successful
      *
      * @see [[isSpace `isSpace`]]
      * @group spec
      */
    val space: Parsley[Int] = satisfy(isSpace(_), "space/tab")

    /** This parser skips zero or more space characters using [[space `space`]].
      *
      * @see [[combinator.skipMany `combinator.skipMany`]]
      * @group skip
      */
    val spaces: Parsley[Unit] = skipMany(space)

    /** This parser tries to parse a whitespace character, and returns it if successful.
      *
      * A whitespace character is one of:
      *   1. a space (`' '`)
      *   1. a tab (`'\t'`)
      *   1. a line feed (`'\n'`)
      *   1. a carriage return (`'\r'`)
      *   1. a form feed (`'\f'`)
      *   1. a vertical tab (`'\u000b'`)
      *
      * @group spec
      */
    val whitespace: Parsley[Int] = satisfy(Character.isWhitespace(_), "whitespace")

    /** This parser skips zero or more space characters using [[whitespace `whitespace`]].
      *
      * @see [[combinator.skipMany `combinator.skipMany`]]
      * @group skip
      */
    val whitespaces: Parsley[Unit] = skipMany(whitespace)

    /** This parser tries to parse a line feed newline (`'\n'`) character, and returns it if successful.
      *
      * This parser will not accept a carriage return (`CR`) character or `CRLF`.
      *
      * @group spec
      */
    val newline: Parsley[Int] = char('\n', "newline")

    /** This parser tries to parse a `CRLF` newline character pair, returning `'\n'` if successful.
      *
      * A `CRLF` character is the pair of carriage return (`'\r'`) and line feed (`'\n'`). These
      * two characters will be parsed together or not at all. The parser is made atomic using `attempt`.
      *
      * @group spec
      */
    val crlf: Parsley[Int] = character.crlf.as(0x0a)

    /** This parser will parse either a line feed (`LF`) or a `CRLF` newline, returning `'\n'` if successful.
      *
      * @group spec
      * @see [[crlf `crlf`]]
      */
    val endOfLine: Parsley[Int] = (newline <|> crlf).label("end of line")

    /** This parser tries to parse a tab (`'\t'`) character, and returns it if successful.
      *
      * This parser does not recognise vertical tabs, only horizontal ones.
      *
      * @group spec
      */
    val tab: Parsley[Int] = char('\t', "tab")

    /** This parser tries to parse an uppercase letter, and returns it if successful.
      *
      * An uppercase letter is any character whose Unicode ''Category Type'' is Uppercase Letter (`Lu`).
      * Examples of characters within this category include:
      *   - the Latin letters `'A'` through `'Z'`
      *   - Latin special character such as `'Å'`, `'Ç'`, `'Õ'`
      *   - Cryillic letters
      *   - Greek letters
      *   - Coptic letters
      *
      * $categories
      *
      * @group spec
      */
    val upper: Parsley[Int] = satisfy(Character.isUpperCase(_), "uppercase letter")

    /** This parser tries to parse a lowercase letter, and returns it if successful.
      *
      * A lowercase letter is any character whose Unicode ''Category Type'' is Lowercase Letter (`Ll`).
      * Examples of characters within this category include:
      *   - the Latin letters `'a'` through `'z'`
      *   - Latin special character such as `'é'`, `'ß'`, `'ð'`
      *   - Cryillic letters
      *   - Greek letters
      *   - Coptic letters
      *
      * $categories
      *
      * @group spec
      */
    val lower: Parsley[Int] = satisfy(Character.isLowerCase(_), "lowercase letter")

    /** This parser tries to parse either a letter or a digit, and returns it if successful.
      *
      * A letter or digit is anything that would parse in either `letter` or `digit`.
      *
      * @see documentation for [[letter `letter`]].
      * @see documentation for [[digit `digit`]].
      * @group spec
      */
    val letterOrDigit: Parsley[Int] = satisfy(Character.isLetterOrDigit(_), "alpha-numeric character")

    /** This parser tries to parse a letter, and returns it if successful.
      *
      * A letter is any character whose Unicode ''Category Type'' is any of the following:
      *   1. Uppercase Letter (`Lu`)
      *   1. Lowercase Letter (`Ll`)
      *   1. Titlecase Letter (`Lt`)
      *   1. Modifier Letter (`Lm`)
      *   1. Other Letter (`Lo`)
      *
      * $categories
      *
      * @group spec
      */
    val letter: Parsley[Int] = satisfy(Character.isLetter(_), "letter")

    /** This parser tries to parse a digit, and returns it if successful.
      *
      * A digit is any character whose Unicode ''Category Type'' is Decimal Number (`Nd`).
      * Examples of (inclusive) ranges within this category include:
      *   - the Latin digits `'0'` through `'9'`
      *   - the Arabic-Indic digits `'\u0660'` through `'\u0669'`
      *   - the Extended Arabic-Indic digits `'\u06F0'` through `'\u06F9'`
      *   - the Devangari digits `'\u0966'` through `'\u096F'`
      *   - the Fullwidth digits `'\uFF10'` through `'\uFF19'`
      *
      * $categories
      *
      * @group spec
      */
    val digit: Parsley[Int] = satisfy(Character.isDigit(_), "digit")

    /** This parser tries to parse a hexadecimal digit, and returns it if successful.
      *
      * A hexadecimal digit is one of (all inclusive ranges):
      *   1. the digits `'0'` through `'9'`
      *   1. the letters `'a'` through `'f'`
      *   1. the letters `'A'` through `'Z'`
      *
      * @see [[isHexDigit ``isHexDigit``]]
      * @group spec
      */
    val hexDigit: Parsley[Int] = satisfy(isHexDigit(_), "hexadecimal digit")

    /** This parser tries to parse an octal digit, and returns it if successful.
      *
      * An octal digit is one of `'0'` to `'7'` (inclusive).
      *
      * @see [[isOctDigit ``isOctDigit``]]
      * @group spec
      */
    val octDigit: Parsley[Int] = satisfy(isOctDigit(_), "octal digit")

    /** This parser tries to parse a bit and returns it if successful.
      *
      * A bit (binary digit) is either `'0'` or `'1'`.
      *
      * @group spec
      */
    val bit: Parsley[Int] = satisfy(c => Character.digit(c, 2) != -1, "bit")

    // Functions
    /** This function returns true if a character is a hexadecimal digit.
      *
      * A hexadecimal digit is one of (all inclusive ranges):
      *   1. the digits `'0'` through `'9'`
      *   1. the letters `'a'` through `'f'`
      *   1. the letters `'A'` through `'Z'`
      *   1. an equivalent from another charset
      *
      * @see [[hexDigit `hexDigit`]]
      * @group pred
      */
    def isHexDigit(c: Int): Boolean = Character.digit(c, 16) != -1

    /** This function returns true if a character is an octal digit.
      *
      * An octal digit is one of `'0'` to `'7'` (inclusive).
      *
      * @group pred
      * @see [[octDigit `octDigit`]]
      */
    def isOctDigit(c: Int): Boolean = Character.digit(c, 8) != -1

    /** This function returns true if a character is either a space or a tab character.
      *
      * @group pred
      * @see [[space `space`]]
      */
    def isSpace(c: Int): Boolean = c == ' ' || c == '\t'

    // Sue me.
    private def renderChar(c: Int): String = parsley.errors.helpers.renderRawString(Character.toChars(c).mkString)

    private [parsley] def addCodepoint(sb: StringBuilder, codepoint: Int): StringBuilder = {
        if (Character.isSupplementaryCodePoint(codepoint)) {
            sb += Character.highSurrogate(codepoint)
            sb += Character.lowSurrogate(codepoint)
        }
        else sb += codepoint.toChar
    }
}
