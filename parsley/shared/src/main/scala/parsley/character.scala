/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import scala.collection.immutable.NumericRange

import parsley.Parsley.{atomic, empty, many, transPure => pure, some}
import parsley.combinator.choice
import parsley.errors.combinator.ErrorMethods
import parsley.token.errors.{Label, LabelConfig, NotConfigured}

import parsley.internal.deepembedding.singletons

/** This module contains many parsers to do with reading one or more characters. Almost every parser will need something from this module.
  *
  * In particular, this module contains: combinators that can read specific characters; combinators that represent character classes and their negations;
  * combinators for reading specific strings; as well as a selection of pre-made parsers to parse specific kinds of character, like digits and letters.
  *
  * @since 2.2.0
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
  *     These parsers are only able to parse unicode characters in the range `'\u0000'` to `'\uffff'`, known as
  *     the ''Basic Multilingual Plane (BMP)''. Unicode characters wider than a single 16-bit character should be
  *     parsed using multi-character combinators such as `string`, or, alternatively, combinators found in [[unicode `unicode`]].
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
object character extends character
private [parsley] trait character {
    /** This combinator tries to parse a single specific character `c` from the input.
      *
      * Attempts to read the given character `c` from the input stream at the current
      * position. If this character can be found, it is consumed and returned. Otherwise,
      * no input is consumed and this combinator will fail.
      *
      * @example {{{
      * scala> import parsley.character.char
      * scala> char('a').parse("")
      * val res0 = Failure(..)
      * scala> char('a').parse("a")
      * val res1 = Success('a')
      * scala> char('a').parse("ba")
      * val res2 = Failure(..)
      * }}}
      *
      * @param c the character to parse
      * @return a parser that tries to read a single `c`, or fails.
      * @note this combinator can only handle 16-bit characters: for larger codepoints,
      *       consider using [[string `string`]] or [[unicode.char `unicode.char`]].
      * @group core
      */
    final def char(c: Char): Parsley[Char] = char(c, NotConfigured)
    private def char(c: Char, label: String): Parsley[Char] = char(c, Label(label))
    private def char(c: Char, label: LabelConfig): Parsley[Char] = new Parsley(new singletons.CharTok(c, c, label))

    /** This combinator tries to parse a single character from the input that matches the given predicate.
      *
      * Attempts to read a character from the input and tests it against the predicate `pred`. If a character `c`
      * can be read and `pred(c)` is true, then `c` is consumed and returned. Otherwise, no input is consumed
      * and this combinator will fail.
      *
      * @example {{{
      * scala> import parsley.character.satisfy
      * scala> satisfy(_.isDigit).parse("")
      * val res0 = Failure(..)
      * scala> satisfy(_.isDigit).parse("7")
      * val res1 = Success('7')
      * scala> satisfy(_.isDigit).parse("a5")
      * val res2 = Failure(..)
      * scala> def char(c: Char): Parsley[Char] = satisfy(_ == c)
      * }}}
      *
      * @param pred the predicate to test the next character against, should one exist.
      * @return a parser that tries to read a single character `c`, such that `pred(c)` is true, or fails.
      * @note this combinator can only handle 16-bit characters: for larger codepoints, consider using [[unicode.satisfy `unicode.satisfy`]].
      * @group core
      */
    final def satisfy(pred: Char => Boolean): Parsley[Char] = satisfy(pred, NotConfigured)
    private def satisfy(pred: Char => Boolean, label: String): Parsley[Char] = satisfy(pred, Label(label))
    private def satisfy(pred: Char => Boolean, label: LabelConfig) = new Parsley(new singletons.Satisfy(pred, label))

    /** This combinator tries to parse and process a character from the input if it is defined for the given function.
      *
      * Attempts to read a character from the input and tests to see if it is in the domain of `f`. If a character
      * `c` can be read and `f(c)` is defined, then `c` is consumed and `f(c)` is returned. Otherwise, no input is consumed
      * and this combinator will fail.
      *
      * @example {{{
      * scala> import parsley.character.satisfyMap
      * scala> val digit = satisfyMap {
      *   case c if c.isDigit => c.asDigit
      * }
      * scala> digit.parse("")
      * val res0 = Failure(..)
      * scala> digit.parse("7")
      * val res1 = Success(7)
      * scala> digit.parse("a5")
      * val res2 = Failure(..)
      * }}}
      *
      * @param f the function to test the next character against and transform it with, should one exist.
      * @return a parser that tries to read a single character `c`, such that `f(c)` is defined, and returns `f(c)` if so, or fails.
      * @since 4.4.0
      * @note this combinator can only handle 16-bit characters: for larger codepoints, consider using [[unicode.satisfyMap `unicode.satisfyMap`]].
      * @group core
      */
    final def satisfyMap[A](f: PartialFunction[Char, A]): Parsley[A] = satisfy(f.isDefinedAt(_)).map(f)

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
      * @group string
      */
    final def string(s: String): Parsley[String] = string(s, NotConfigured)
    private [parsley] def string(s: String, label: String): Parsley[String] = string(s, Label(label))
    private [parsley] def string(s: String, label: LabelConfig): Parsley[String] = {
        require(s.nonEmpty, "`string` may not be passed the empty string (`string(\"\")` is meaningless, perhaps you meant `pure(\"\")`?)")
        new Parsley(new singletons.StringTok(s, s, label))
    }

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
      * @note this combinator can only handle 16-bit characters: for larger codepoints, consider using [[unicode.oneOf(cs:Set* `unicode.oneOf`]].
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    final def oneOf(cs: Set[Char]): Parsley[Char] = cs.size match {
        case 0 => empty.uo("oneOf(Set.empty)")
        case 1 => char(cs.head).uo(s"oneOf($cs)")
        case _ => satisfy(cs, {
            val Some(label) = parsley.errors.helpers.disjunct(cs.map(renderChar).toList, oxfordComma = true): @unchecked
            s"one of $label"
        }).uo(s"oneOf($cs)")
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
      * @note this combinator can only handle 16-bit characters: for larger codepoints, consider using [[unicode.oneOf(cs:Int* `unicode.oneOf`]].
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    final def oneOf(cs: Char*): Parsley[Char] = oneOf(cs.toSet)

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
      * @note this combinator can only handle 16-bit characters: for larger codepoints, consider using [[unicode.oneOf(cs:Range* `unicode.oneOf`]].
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    final def oneOf(cs: NumericRange[Char]): Parsley[Char] = cs.size match {
        case 0 => empty.uo(s"oneOf($cs)")
        case 1 => char(cs.head).uo(s"oneOf($cs)")
        case _ if Math.abs(cs(0).toInt - cs(1).toInt) == 1 => satisfy(cs.contains(_),
            s"one of ${renderChar(cs.min)} to ${renderChar(cs.max)}"
        ).uo(s"oneOf($cs)")
        case _ => satisfy(cs.contains(_)).uo(s"oneOf($cs)")
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
      * @note this combinator can only handle 16-bit characters: for larger codepoints, consider using [[unicode.noneOf(cs:Set* `unicode.noneOf`]].
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    final def noneOf(cs: Set[Char]): Parsley[Char] = cs.size match {
        case 0 => item.uo("noneOf(Set.empty)")
        case 1 => satisfy(cs.head != _, s"anything except ${renderChar(cs.head)}").uo(s"noneOf($cs)")
        case _ => satisfy(!cs.contains(_), {
            val Some(label) = parsley.errors.helpers.disjunct(cs.map(renderChar).toList, oxfordComma = true): @unchecked
            s"anything except $label"
        }).uo(s"noneOf($cs)")
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
      * @note this combinator can only handle 16-bit characters: for larger codepoints, consider using [[unicode.noneOf(cs:Int* `unicode.noneOf`]].
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    final def noneOf(cs: Char*): Parsley[Char] = noneOf(cs.toSet)

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
      * @note this combinator can only handle 16-bit characters: for larger codepoints, consider using [[unicode.noneOf(cs:Range* `unicode.noneOf`]].
      * @see [[satisfy `satisfy`]]
      * @group class
      */
    final def noneOf(cs: NumericRange[Char]): Parsley[Char] = cs.size match {
        case 0 => item.uo(s"noneOf($cs)")
        case 1 => satisfy(cs.head != _, s"anything except ${renderChar(cs.head)}").uo(s"noneOf($cs)")
        case _ if Math.abs(cs(0).toInt - cs(1).toInt) == 1 => satisfy(!cs.contains(_), {
            s"anything outside of ${renderChar(cs.min)} to ${renderChar(cs.max)}"
        }).uo(s"noneOf($cs)")
        case _ => satisfy(!cs.contains(_)).uo(s"noneOf($cs)")
    }

    /** This combinator parses `pc` '''zero''' or more times, collecting its results into a string.
      *
      * Parses `pc` repeatedly until it fails. The resulting characters are placed into a string,
      * which is then returned. This is ''morally'' equivalent to `many(pc).map(_.mkString)`, but
      * it uses `StringBuilder`, which makes it much more efficient.
      *
      * @example {{{
      * scala> import parsley.character.{letter, letterOrDigit, stringOfMany}
      * scala> import parsley.syntax.zipped._
      * scala> val ident = (letter, stringOfMany(letterOrDigit)).zipped((c, s) => s"&#36;c&#36;s")
      * scala> ident.parse("abdc9d")
      * val res0 = Success("abdc9d")
      * scala> ident.parse("a")
      * val res1 = Success("a")
      * scala> ident.parse("9")
      * val res2 = Failure(..)
      * }}}
      *
      * @param pc the parser whose results make up the string
      * @return a parser that parses a string whose letters consist of results from `pc`.
      * @since 4.0.0
      * @group string
      */
    final def stringOfMany(pc: Parsley[Char]): Parsley[String] = many(pc, StringFactories.charFactory).uo("stringOfMany")

    // TODO: optimise, this can be _really_ tightly implemented with a substring on the input
    /** This combinator parses characters matching the given predicate '''zero''' or more times, collecting
      * the results into a string.
      *
      * Repeatly reads characters that satisfy the given predicate `pred`. When no more characters
      * can be successfully read, the results are stitched together into a `String` and returned.
      * This combinator can never fail, since `satisfy` can never fail having consumed input.
      *
      * @example {{{
      * scala> import parsley.character.{letter, stringOfMany}
      * scala> import parsley.syntax.zipped._
      * scala> val ident = (letter, stringOfMany(_.isLetterOrDigit)).zipped((c, s) => s"&#36;c&#36;s")
      * scala> ident.parse("abdc9d")
      * val res0 = Success("abdc9d")
      * scala> ident.parse("a")
      * val res1 = Success("a")
      * scala> ident.parse("9")
      * val res2 = Failure(..)
      * }}}
      *
      * @param pred the predicate to test characters against.
      * @return a parser that returns the span of characters satisfying `pred`
      * @note this acts exactly like `stringOfMany(satisfy(pred))`, but may be more efficient.
      * @note analogous to the `megaparsec` `takeWhileP` combinator.
      * @since 4.4.0
      * @group string
      */
    final def stringOfMany(pred: Char => Boolean): Parsley[String] = many(satisfy(pred).ut()).ut().span.uo("stringOfMany")

    /** This combinator parses `pc` '''one''' or more times, collecting its results into a string.
      *
      * Parses `pc` repeatedly until it fails. The resulting characters are placed into a string,
      * which is then returned. This is ''morally'' equivalent to `many(pc).map(_.mkString)`, but
      * it uses `StringBuilder`, which makes it much more efficient. The result string must have
      * at least one character in it.
      *
      * @example {{{
      * scala> import parsley.character.{letter, stringOfSome}
      * scala> val ident = stringOfSome(letter)
      * scala> ident.parse("abdc9d")
      * val res0 = Success("abdc")
      * scala> ident.parse("")
      * val res1 = Failure(..)
      * }}}
      *
      * @param pc the parser whose results make up the string
      * @return a parser that parses a string whose letters consist of results from `pc`.
      * @since 4.0.0
      * @group string
      */
    final def stringOfSome(pc: Parsley[Char]): Parsley[String] = some(pc, StringFactories.charFactory).uo("stringOfSome")

    // TODO: optimise, this can be _really_ tightly implemented with a substring on the input
    /** This combinator parses characters matching the given predicate '''one''' or more times, collecting
      * the results into a string.
      *
      * Repeatly reads characters that satisfy the given predicate `pred`. When no more characters
      * can be successfully read, the results are stitched together into a `String` and returned.
      * This combinator can never fail having consumed input, since `satisfy` can never fail having
      * consumed input.
      *
      * @example {{{
      * scala> import parsley.character.{stringOfSome}
      * scala> val ident = stringOfSome(_.isLetter)
      * scala> ident.parse("abdc9d")
      * val res0 = Success("abdc")
      * scala> ident.parse("")
      * val res1 = Failure(..)
      * }}}
      *
      * @param pred the predicate to test characters against.
      * @return a parser that returns the span of characters satisfying `pred`
      * @note this acts exactly like `stringOfSome(satisfy(pred))`, but may be more efficient.
      * @note analogous to the `megaparsec` `takeWhile1P` combinator.
      * @since 4.4.0
      * @group string
      */
    final def stringOfSome(pred: Char => Boolean): Parsley[String] = some(satisfy(pred).ut()).ut().span.uo("stringOfSome")

    /** This combinator tries to parse each of the strings `strs` (and `str0`), until one of them succeeds.
      *
      * Unlike `choice`, or more accurately `atomicChoice`, this combinator will not
      * necessarily parse the strings in the order provided. It will avoid strings that have another string
      * as a prefix first, so that it has ''Longest Match'' semantics. It will try to minimise backtracking
      * too, making it a much more efficient option than `atomicChoice`.
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
      * @since 4.0.0
      * @group string
      */
    final def strings(str0: String, strs: String*): Parsley[String] = strings(str0 -> pure(str0), strs.map(s => s -> pure(s)): _*)

    /** This combinator tries to parse each of the key-value pairs `kvs` (and `kv0`), until one of them succeeds.
      *
      * Each argument to this combinator is a pair of a string and a parser to perform if that string can be parsed.
      * `strings(s0 -> p0, ...)` can be thought of as `atomicChoice(string(s0) *> p0, ...)`, however, the given
      * ordering of key-value pairs does not dictate the order in which the parses are tried. In particular, it
      * will avoid keys that are the prefix of another key first, so that it has ''Longest Match'' semantics.
      * It will try to minimise backtracking too, making it a much more efficient option than `atomicChoice`.
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
      * key parses correctly, the branch has been committed to. Putting an `atomic` around the values will not affect
      * this behaviour.
      *
      * @param kv0 the first key-value pair to try to parse.
      * @param kvs the remaining key-value pairs to try to parse.
      * @return a parser that tries to parse all the given key-value pairs, returning the (possibly failing) result
      *         of the value that corresponds to the longest matching key.
      * @since 4.0.0
      * @group string
      */
    final def strings[A](kv0: (String, Parsley[A]), kvs: (String, Parsley[A])*): Parsley[A] = {
        // this isn't the best we could do: it's possible to eliminate backtracking with a Trie...
        // can this be done in a semantic preserving way without resorting to a new instruction?
        // I don't think it's worth it. Down the line a general Trie-backed optimisation would be
        // more effective.
        val ss = kv0 +: kvs
        choice(ss.groupBy(_._1.head).toList.sortBy(_._1).view.map(_._2).flatMap { s =>
            val (sLast, pLast) :: rest = s.toList.sortBy(_._1.length): @unchecked
            ((string(sLast).ut() *> pLast.ut()).ut() :: rest.map { case (s, p) => (atomic(string(s).ut()).ut() *> p).ut() }).reverse
        }.toSeq: _*).uo((kv0._1 +: kvs.map(_._1)).mkString("strings(", ", ", ")"))
    }

    /** This parser will parse '''any''' single character from the input, failing if there is no input remaining.
      *
      * @note this combinator can only handle 16-bit characters: for larger codepoints, consider using [[unicode.item `unicode.item`]].
      * @group core
      */
    final val item: Parsley[Char] = satisfy(_ => true, "any character").uo("item")

    /** This parser tries to parse a space or tab character, and returns it if successful.
      *
      * @see [[isSpace `isSpace`]]
      * @group spec
      */
    final val space: Parsley[Char] = _space.uo("space")
    private def _space = satisfy(isSpace(_), "space/tab")

    /** This parser skips zero or more space characters using [[space `space`]].
      *
      * @group skip
      */
    final val spaces: Parsley[Unit] = many(_space.ut()).ut().void.uo("spaces")

    /** This parser tries to parse a whitespace character, and returns it if successful.
      *
      * A whitespace character is one of:
      *   1. a space (`' '`)
      *   1. a tab (`'\t'`)
      *   1. a line feed (`'\n'`)
      *   1. a carriage return (`'\r'`)
      *   1. a form feed (`'\f'`)
      *   1. a vertical tab (`'\u000B'`)
      *
      * @group spec
      */
    final val whitespace: Parsley[Char] = _whitespace.uo("whitespace")
    private def _whitespace = satisfy(_.isWhitespace, "whitespace")

    /** This parser skips zero or more space characters using [[whitespace `whitespace`]].
      *
      * @group skip
      */
    final val whitespaces: Parsley[Unit] = many(_whitespace.ut()).ut().void.uo("whitespaces")

    /** This parser tries to parse a line feed newline (`'\n'`) character, and returns it if successful.
      *
      * This parser will not accept a carriage return (`CR`) character or `CRLF`.
      *
      * @group spec
      */
    final val newline: Parsley[Char] = _newline.uo("newline")
    private def _newline = char('\n', "newline")

    /** This parser tries to parse a `CRLF` newline character pair, returning `'\n'` if successful.
      *
      * A `CRLF` character is the pair of carriage return (`'\r'`) and line feed (`'\n'`). These
      * two characters will be parsed together or not at all. The parser is made atomic using `atomic`.
      *
      * @group spec
      */
    final val crlf: Parsley[Char] = _crlf.uo("crlf")
    private def _crlf = atomic(string("\r\n", "end of crlf").ut()).ut().as('\n')

    /** This parser will parse either a line feed (`LF`) or a `CRLF` newline, returning `'\n'` if successful.
      *
      * @group spec
      * @see [[crlf `crlf`]]
      */
    final val endOfLine: Parsley[Char] = (_newline.ut() <|> _crlf.ut()).ut().label("end of line").uo("endOfLine")

    /** This parser tries to parse a tab (`'\t'`) character, and returns it if successful.
      *
      * This parser does not recognise vertical tabs, only horizontal ones.
      *
      * @group spec
      */
    final val tab: Parsley[Char] = char('\t', "tab").uo("tab")

    /** This parser tries to parse an uppercase letter, and returns it if successful.
      *
      * An uppercase letter is any character `c <= '\uffff'` whose Unicode ''Category Type'' is Uppercase Letter (`Lu`).
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
    final val upper: Parsley[Char] = satisfy(_.isUpper, "uppercase letter").uo("upper")

    /** This parser tries to parse a lowercase letter, and returns it if successful.
      *
      * A lowercase letter is any character `c <= '\uffff'` whose Unicode ''Category Type'' is Lowercase Letter (`Ll`).
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
    final val lower: Parsley[Char] = satisfy(_.isLower, "lowercase letter").uo("lower")

    /** This parser tries to parse either a letter or a digit, and returns it if successful.
      *
      * A letter or digit is anything that would parse in either `letter` or `digit`.
      *
      * @see documentation for [[letter `letter`]].
      * @see documentation for [[digit `digit`]].
      * @group spec
      */
    final val letterOrDigit: Parsley[Char] = satisfy(_.isLetterOrDigit, "alpha-numeric character").uo("letterOrDigit")

    /** This parser tries to parse a letter, and returns it if successful.
      *
      * A letter is any character `c <= '\uffff'` whose Unicode ''Category Type'' is any of the following:
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
    final val letter: Parsley[Char] = satisfy(_.isLetter, "letter").uo("letter")

    /** This parser tries to parse a digit, and returns it if successful.
      *
      * A digit is any character `c <= '\uffff'` whose Unicode ''Category Type'' is Decimal Number (`Nd`).
      * Examples of (inclusive) ranges within this category include:
      *   - the Latin digits `'0'` through `'9'`
      *   - the Arabic-Indic digits `'\u0660'` through `'\u0669'`
      *   - the Extended Arabic-Indic digits `'\u06f0'` through `'\u06f9'`
      *   - the Devangari digits `'\u0966'` through `'\u096f'`
      *   - the Fullwidth digits `'\uff10'` through `'\uff19'`
      *
      * $categories
      *
      * @group spec
      */
    final val digit: Parsley[Char] = satisfy(_.isDigit, "digit").uo("digit")

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
    final val hexDigit: Parsley[Char] = satisfy(isHexDigit(_), "hexadecimal digit").uo("hexDigit")

    /** This parser tries to parse an octal digit, and returns it if successful.
      *
      * An octal digit is one of `'0'` to `'7'` (inclusive).
      *
      * @see [[isOctDigit ``isOctDigit``]]
      * @group spec
      */
    final val octDigit: Parsley[Char] = satisfy(isOctDigit(_), "octal digit").uo("octDigit")

    /** This parser tries to parse a binary digit (bit) and returns it if successful.
      *
      * A bit is either `'0'` or `'1'`.
      *
      * @group spec
      */
    final val bit: Parsley[Char] = satisfy(c => Character.digit(c, 2) != -1, "bit").uo("bit")

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
    final def isHexDigit(c: Char): Boolean = Character.digit(c, 16) != -1

    /** This function returns true if a character is an octal digit.
      *
      * An octal digit is one of `'0'` to `'7'` (inclusive).
      *
      * @group pred
      * @see [[octDigit `octDigit`]]
      */
    final def isOctDigit(c: Char): Boolean = Character.digit(c, 8) != -1

    /** This function returns true if a character is either a space or a tab character.
      *
      * @group pred
      * @see [[space `space`]]
      */
    final def isSpace(c: Char): Boolean = c == ' ' || c == '\t'

    // Sue me.
    private def renderChar(c: Char): String = parsley.errors.helpers.renderRawString(s"$c")
}
