/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token

import parsley.Parsley, Parsley.{eof, many, transPure => pure}
import parsley.combinator.{sepBy, sepBy1}
import parsley.errors.combinator.{markAsToken, ErrorMethods}
import parsley.state.Ref
import parsley.token.names.{ConcreteNames, LexemeNames}
import parsley.token.numeric.{CombinedParsers, IntegerParsers,
                              LexemeCombined, LexemeInteger, LexemeReal,
                              RealParsers,
                              SignedCombined, SignedInteger, SignedReal,
                              UnsignedCombined, UnsignedInteger, UnsignedReal}
import parsley.token.symbol.{ConcreteSymbol, LexemeSymbol}
import parsley.token.text.{CharacterParsers, ConcreteCharacter, ConcreteString,
                           EscapableCharacter, Escape, LexemeCharacter, LexemeString,
                           RawCharacter, StringParsers}
import parsley.unicode.satisfy

import parsley.internal.deepembedding.singletons

/** This class is just to allow for a polymorphic apply for the lexeme versions of each lexing category
  *
  * It is designed to be extended by `Lexer#lexeme` only (or within the tests!)
  */
private [token] abstract class Lexeme {
    def apply[A](p: Parsley[A]): Parsley[A]
}

// TODO: grouping for numeric and text? we can use documentation tags to group them in the high-level docs again :)
/** This class provides a large selection of functionality concerned
  * with lexing.
  *
  * This class provides lexing functionality to `parsley`, however
  * it is guaranteed that nothing in this class is not implementable
  * purely using `parsley`'s pre-existing functionality. These are
  * regular parsers, but constructed in such a way that they create
  * a clear and logical separation from the rest of the parser.
  *
  * The class is broken up into several internal "modules" that group
  * together similar kinds of functionality. Importantly, the `lexemes`
  * and `nonlexemes` objects separate the underlying token implementations
  * based on whether or not they consume whitespace or not. Functionality
  * is broadly duplicated across both of these modules: `lexemes` should
  * be used by a wider parser, to ensure whitespace is handled uniformly;
  * and `nonlexemes` should be used to define further composite tokens or
  * in special circumstances where whitespace should not be consumed.
  *
  * It is possible that some of the implementations of
  * parsers found within this class may have been hand-optimised for
  * performance: care '''will''' have been taken to ensure these
  * implementations precisely match the semantics of the originals.
  *
  * @define numeric
  *     These objects contain lexing functionality relevant to the parsing
  *     of numbers. This is sub-divided into different categories:
  *
  *       - integers (both signed and unsigned)
  *       - reals    (signed only)
  *       - a combination of the two (signed and unsigned)
  *
  *     These contain relevant functionality for the processing of
  *     decimal, hexadecimal, octal, and binary literals; or some
  *     mixed combination thereof (as specified by `desc.numericDesc`).
  *     Additionally, it is possible to ensure literals represent known
  *     sizes or precisions.
  *
  * @define text
  *     These object contain lexing functionality relevant to the parsing
  *     of text. This is sub-divided into different categories:
  *
  *       - string literals (both with escapes and raw)
  *       - multi-line string literals (both with escapes and raw)
  *       - character literals
  *
  *     These contain the relevant functionality required to specify the
  *     degree of unicode support for the underlying language, from
  *     ASCII to full UTF-16.
  *
  * @define symbol
  *     This object contains lexing functionality relevant to the parsing
  *     of atomic symbols.
  *
  *     Symbols are characterised by their "unitness", that is, every parser
  *     inside returns `Unit`. This is because they all parse a specific
  *     known entity, and, as such, the result of the parse is irrelevant.
  *     These can be things such as reserved names, or small symbols like
  *     parentheses. This object also contains a means of creating new symbols
  *     as well as implicit conversions to allow for Scala's string literals to serve
  *     as symbols within a parser.
  *
  * @define names
  *     This object contains lexing functionality relevant to the parsing
  *     of names, which include operators or identifiers.
  *
  *     The parsing of names is mostly concerned with finding the longest
  *     valid name that is not a reserved name, such as a hard keyword or
  *     a special operator.
  *
  * @define natural
  *     This is a collection of parsers concerned with handling unsigned (positive) integer literals.
  *
  *     Natural numbers are described generally as follows:
  *       - '''`desc.numericDesc.literalBreakChar`''': determines whether or not it
  *         is legal to "break up" the digits within a literal, for example: is `1_000_000` allowed?
  *         If this is legal, describes what the break character is, and whether it can appear after
  *         a hexadecimal/octal/binary prefix
  *       - '''`desc.numericDesc.leadingZerosAllowed`''': determines whether or not it is
  *         possible to add extraneous zero digits onto the front of a number or not. In some languages,
  *         like C, this is disallowed, as numbers starting with `0` are octal numbers.
  *       - '''`desc.numericDesc.integerNumbersCanBe{Hexadecimal/Octal/Binary}`''': these flags
  *         control what kind of literals can appear within the `number` parser. Each type of literal
  *         can be individually parsed with its corresponding parser, regardless of the value of the
  *         flag
  *       - '''`desc.numericDesc.{hexadecimal/octal/binary}Leads`''': controls what character must
  *         follow a `0` when starting a number to change it from decimal into another base. This
  *         set may be empty, in which case the literal is described purely with leading zero (C style
  *         octals would set `octalLeads` to `Set.empty`)
  *
  *     Additional to the parsing of decimal, hexadecimal, octal, and binary literals, each parser can
  *     be given a bit-width from 8- to 64-bit: this will check the parsed literal to ensure it is
  *     a legal literal of that size.
  * @define integer
  *     This is a collection of parsers concerned with handling signed integer literals.
  *
  *     Signed integer literals are an extension of unsigned integer literals with the following
  *     extra configuration:
  *       - '''`desc.numericDesc.positiveSign`''': describes whether or not literals are
  *         allowed to omit `+` for positive literals, must write a `+`, or can never write a `+`.
  * @define real
  *     This is a collection of parsers concerned with handling signed real numbers (like floats and doubles).
  *
  *     These literals consist of a (possibly optional) integer prefix, with at least one of a fractional component (with `.`)
  *     or an exponential component.
  *
  *     Real numbers are an extension of signed integers with the following additional configuration:
  *       - '''`desc.numericDesc.leadingDotAllowed`''': determines whether a literal like `.0` would be considered legal
  *       - '''`desc.numericDesc.trailingDotAllowed`''': determines whether a literal like `0.` would be considered legal
  *       - '''`desc.numericDesc.realNumbersCanBe{Hexadecimal/Octal/Binary}`''': these flags control
  *         what kind of literals can appear within the `number` parser. Each type of literal
  *         may still be individually parsed with its corresponding parser, regardless of the value of
  *         the flag
  *       - '''`desc.numericDesc.{decimal/hexadecimal/octal/binary}ExponentDesc`''': describes how the
  *         exponential syntax works for each kind of base. If the syntax is legal, then this describes:
  *         which characters start it (classically, this would be `e` or `E` for decimals); whether or
  *         not it is compulsory for the literal (in Java and C, hexadecimal floats are ''only'' valid
  *         when they have an exponent attached); and whether or not a `+` sign is mandatory, optional,
  *         or illegal for positive exponents
  *
  *   Additional to the parsing of decimal, hexadecimal, octal, and binary floating literals, each
  *   parser can be given a precision of IEEE 754 float or double. This can either be achieved by
  *   rounding to the nearest representable value, or by ensuring that the literal must be precisely
  *   representable as one of these numbers (which is defined as being one of binary, decimal
  *   or exact `float` and `double` values as described by Java)
  *
  * @define unsignedCombined
  *     This is a collection of parsers concerned with handling numeric literals that may either be
  *     unsigned integers ''or'' unsigned reals.
  *
  *     There is no additional configuration offered over that found in `natural` or `real`.
  *
  *     the bit-bounds and precision of the integer or real parts of the result can be specified
  *     in any pairing.
  * @define signedCombined
  *     This is a collection of parsers concerned with handling numeric literals that may either be
  *     signed integers ''or'' signed reals.
  *
  *     There is no additional configuration offered over that found in `integer` or `real`.
  *
  *     the bit-bounds and precision of the integer or real parts of the result can be specified
  *     in any pairing.
  *
  * @define character
  *     This is a collection of parsers concerned with handling character literals.
  *
  *     Character literals are described generally as follows:
  *       - '''`desc.textDesc.characterLiteralEnd`''': the character that starts and ends
  *         the literal (for example in many languages this is `'`)
  *       - '''`desc.textDesc.graphicCharacter`''': describes the legal characters that may appear
  *         in the literal directly. Usually, this excludes control characters and newlines,
  *         but permits most other things. Escape sequences can represent non-graphic
  *         characters
  *       - '''`desc.textDesc.escapeSequences`''': describes the legal escape sequences that
  *         that can appear in a character literal (for example `\n` or `\u000a`)
  *
  *     Aside from the generic configuration, characters can be parsed in accordance with
  *     varying levels of unicode support, from ASCII-only to full UTF-16 characters. Parsers
  *     for each of four different vareties are exposed by this object.
  * @define string
  *     This is a collection of parsers concerned with handling single-line string literals.
  *
  *     String literals are described generally as follows:
  *       - '''`desc.textDesc.stringEnds`''':  the sequence of characters that can begin or
  *         end a string literal. Regardless of which of these is used for a specific literal,
  *         the end of the literal ''must'' use the same sequence
  *       - '''`desc.textDesc.graphicCharacter`''': describes the legal characters that may appear
  *         in the literal directly. Usually, this excludes control characters and newlines,
  *         but permits most other things. Escape sequences can represent non-graphic
  *         characters for non-raw strings
  *       - '''`desc.textDesc.escapeSequences`''': describes the legal escape sequences that
  *         that can appear in a string literal (for example `\n` or `\u000a`)
  * @define multiString
  *    This is a collection of parsers concerned with handling multi-line string literals.
  *
  *     String literals are described generally as follows:
  *       - '''`desc.textDesc.multiStringEnds`''':  the sequence of characters that can begin or
  *         end a multi-line string literal. Regardless of which of these is used for a specific literal,
  *         the end of the literal ''must'' use the same sequence
  *       - '''`desc.textDesc.graphicCharacter`''': describes the legal characters that may appear
  *         in the literal directly. Usually, this excludes control characters and newlines,
  *         but permits most other things. Escape sequences can represent non-graphic
  *         characters for non-raw strings
  *       - '''`desc.textDesc.escapeSequences`''': describes the legal escape sequences that
  *         that can appear in a string literal (for example `\n` or `\u000a`)
  * @define raw this will be parsed without handling any escape sequences,
  *         this includes literal-end characters and the escape prefix
  *         (often `"` and `\` respectively)
  *
  * @constructor Builds a new lexer with a given description for the lexical structure as
  *              well as how error messages should be specialised.
  * @param desc the configuration for the lexer, specifying the lexical
  *             rules of the grammar being parsed.
  * @param errConfig the configuration for error messages generated within
  *                  the lexer.
  * @since 4.0.0
  */
final class Lexer(desc: descriptions.LexicalDesc, errConfig: errors.ErrorConfig) {
    /** Builds a new lexer with a given description for the lexical structure of the language.
      *
      * @param desc the configuration for the lexer, specifying the lexical
      *             rules of the grammar/language being parsed.
      * @since 4.0.0
      */
    def this(desc: descriptions.LexicalDesc) = this(desc, new errors.ErrorConfig)

    // Note: If any members with only public parsers are added into this class,
    //       please also add those objects into the safeLexerObjects list within CollectorImpl
    //       (found in Collector.scala in parsley-debug/src/shared). If those members are terms that
    //       contain other parsers, add them to that list too.
    //       Members with private members (in which those private members contain parsers) must be
    //       added to unsafeLexerObjects (where they will be reflected twice).

    private val generic = new numeric.Generic(errConfig)

    /** This object is concerned with ''lexemes'': these are tokens that are
      * treated as "words", such that whitespace will be consumed after each
      * has been parsed.
      *
      * Ideally, a wider parser should not be concerned with
      * handling whitespace, as it is responsible for dealing with a stream
      * of tokens. With parser combinators, however, it is usually not the
      * case that there is a separate distinction between the parsing phase
      * and the lexing phase. That said, it is good practice to establish
      * a logical separation between the two worlds. As such, this object
      * contains parsers that parse tokens, and these are whitespace-aware.
      * This means that whitespace will be consumed '''after''' any of these
      * parsers are parsed. It is not, however, required that whitespace be
      * present.
      *
      * @groupname numericg Numeric Parsers
      * @groupdesc numericg $numeric
      *
      * @groupname textg String Literal Parsers
      * @groupdesc textg $text
      *
      * @groupname separatorsg Separators
      * @groupdesc separatorsg
      *     These combinators are useful for separating a parser by commonly used
      *     bits, such as commas.
      *
      * @groupname enclosingg Enclosers
      * @groupdesc enclosingg
      *     These combinators abstract away different bracket-like constructions.
      *
      * @since 4.0.0
      */
    object lexeme extends Lexeme {
        /** This combinator turns a non-lexeme parser into a lexeme one by
          * ensuring whitespace is consumed after the parser.
          *
          * When using parser combinators, it is important to establish a
          * consistent whitespace consumption scheme: ideally, there is no
          * wasteful parsing, and whitespace consumption should not impact
          * backtracking. This leads to a convention that whitespace must
          * only be consumed ''after'' a token, and only once at the very
          * start of the parser (see [[fully `fully`]]). When manually
          * constructing tokens that are not supported by this lexer, use
          * this combinator to ensure it also follows the whitespace convention.
          *
          * @param p the token parser to ensure consumes trailing whitespace.
          * @since 4.0.0
          */
        def apply[A](p: Parsley[A]): Parsley[A] = (markAsToken(p) <* space.whiteSpace).uo("lexeme")

        /** $names
          *
          * @since 4.0.0
          */
        val names: parsley.token.names.Names = new LexemeNames(nonlexeme.names, this)

        /** $natural
          *
          * @since 4.5.0
          * @note alias for [[natural `natural`]].
          * @group numericg
          */
        // $COVERAGE-OFF$
        def unsigned: IntegerParsers = natural
        // $COVERAGE-ON$
        /** $natural
          *
          * @since 4.5.0
          * @group numericg
          */
        val natural: IntegerParsers = new LexemeInteger(nonlexeme.natural, this)

        /** $integer
          *
          * @since 4.5.0
          * @note alias for [[integer `integer`]]
          * @see [[unsigned `unsigned`]] for a full description of signed integer configuration
          * @group numericg
          */
        // $COVERAGE-OFF$
        def signed: IntegerParsers = integer
        // $COVERAGE-ON$
        /** $integer
          *
          * @since 4.5.0
          * @see [[natural `natural`]] for a full description of integer configuration
          * @group numericg
          */
        val integer: IntegerParsers = new LexemeInteger(nonlexeme.integer, this)

        /** $real
          *
          * @since 4.5.0
          * @note alias for [[real `real`]]
          * @see [[natural `natural`]] and [[integer `integer`]] for a full description of the configuration for the start of a real number
          * @group numericg
          */
        // $COVERAGE-OFF$
        def floating: RealParsers = real
        // $COVERAGE-ON$
        /** $real
          *
          * @since 4.5.0
          * @see [[natural `natural`]] and [[integer `integer`]] for a full description of the configuration for the start of a real number
          * @group numericg
          */
        val real: RealParsers = new LexemeReal(nonlexeme.real, this, errConfig)

        /** $unsignedCombined
          *
          * @since 4.5.0
          * @group numericg
          */
        val unsignedCombined: CombinedParsers = new LexemeCombined(nonlexeme.unsignedCombined, this, errConfig)
        /** $signedCombined
          *
          * @since 4.5.0
          * @group numericg
          */
        val signedCombined: CombinedParsers = new LexemeCombined(nonlexeme.signedCombined, this, errConfig)

        /** $character
          *
          * @since 4.5.0
          * @group textg
          */
        def character: CharacterParsers = new LexemeCharacter(nonlexeme.character, this)
        /** $string
          *
          * @since 4.5.0
          * @group textg
          */
        def string: StringParsers = new LexemeString(nonlexeme.string, this)
        /** $string
          *
          * @note $raw
          * @since 4.5.0
          * @group textg
          */
        def rawString: StringParsers = new LexemeString(nonlexeme.rawString, this)
        /** $multiString
          *
          * @since 4.5.0
          * @group textg
          */
        def multiString: StringParsers = new LexemeString(nonlexeme.multiString, this)
        /** $multiString
          *
          * @note $raw
          * @since 4.5.0
          * @group textg
          */
        def rawMultiString: StringParsers = new LexemeString(nonlexeme.rawMultiString, this)

        /** $symbol
          *
          * @since 4.0.0
          */
        val symbol: parsley.token.symbol.Symbol = new LexemeSymbol(nonlexeme.symbol, this)

        /**  This combinator parses '''zero''' or more occurrences of `p`, separated by semi-colons.
          *
          * Behaves just like `semiSep1`, except does not require an initial `p`, returning the empty list instead.
          *
          * @example {{{
          * scala> ...
          * scala> val stmts = lexer.lexeme.separators.semiSep(int)
          * scala> stmts.parse("7; 3;2")
          * val res0 = Success(List(7; 3; 2))
          * scala> stmts.parse("")
          * val res1 = Success(Nil)
          * scala> stmts.parse("1")
          * val res2 = Success(List(1))
          * scala> stmts.parse("1; 2; ")
          * val res3 = Failure(..) // no trailing semi-colon allowed
          * }}}
          *
          * @param p the parser whose results are collected into a list.
          * @return a parser that parses `p` delimited by semi-colons, returning the list of `p`'s results.
          * @since 4.5.0
          * @group separatorsg
          */
        def semiSep[A](p: Parsley[A]): Parsley[List[A]] = sepBy(p, symbol.semi)
        /**  This combinator parses '''one''' or more occurrences of `p`, separated by semi-colons.
          *
          * First parses a `p`. Then parses a semi-colon followed by `p` until there are no more  semi-colons.
          * The results of the `p`'s, `x,,1,,` through `x,,n,,`, are returned as `List(x,,1,,, .., x,,n,,)`.
          * If `p` fails having consumed input, the whole parser fails. Requires at least
          * one `p` to have been parsed.
          *
          * @example {{{
          * scala> ...
          * scala> val stmts = lexer.lexeme.separators.semiSep1(int)
          * scala> stmts.parse("7; 3;2")
          * val res0 = Success(List(7; 3; 2))
          * scala> stmts.parse("")
          * val res1 = Failure(..)
          * scala> stmts.parse("1")
          * val res2 = Success(List(1))
          * scala> stmts.parse("1; 2; ")
          * val res3 = Failure(..) // no trailing semi-colon allowed
          * }}}
          *
          * @param p the parser whose results are collected into a list.
          * @return a parser that parses `p` delimited by semi-colons, returning the list of `p`'s results.
          * @since 4.5.0
          * @group separatorsg
          */
        def semiSep1[A](p: Parsley[A]): Parsley[List[A]] = sepBy1(p, symbol.semi)
        /**  This combinator parses '''zero''' or more occurrences of `p`, separated by commas.
          *
          * Behaves just like `commaSep1`, except does not require an initial `p`, returning the empty list instead.
          *
          * @example {{{
          * scala> ...
          * scala> val stmts = lexer.lexeme.separators.commaSep(int)
          * scala> stmts.parse("7, 3,2")
          * val res0 = Success(List(7, 3, 2))
          * scala> stmts.parse("")
          * val res1 = Success(Nil)
          * scala> stmts.parse("1")
          * val res2 = Success(List(1))
          * scala> stmts.parse("1, 2, ")
          * val res3 = Failure(..) // no trailing comma allowed
          * }}}
          *
          * @param p the parser whose results are collected into a list.
          * @return a parser that parses `p` delimited by commas, returning the list of `p`'s results.
          * @since 4.5.0
          * @group separatorsg
          */
        def commaSep[A](p: Parsley[A]): Parsley[List[A]] = sepBy(p, symbol.comma)
        /**  This combinator parses '''one''' or more occurrences of `p`, separated by commas.
          *
          * First parses a `p`. Then parses a comma followed by `p` until there are no more  commas.
          * The results of the `p`'s, `x,,1,,` through `x,,n,,`, are returned as `List(x,,1,,, .., x,,n,,)`.
          * If `p` fails having consumed input, the whole parser fails. Requires at least
          * one `p` to have been parsed.
          *
          * @example {{{
          * scala> ...
          * scala> val stmts = lexer.lexeme.separators.commaSep1(int)
          * scala> stmts.parse("7, 3,2")
          * val res0 = Success(List(7, 3, 2))
          * scala> stmts.parse("")
          * val res1 = Failure(..)
          * scala> stmts.parse("1")
          * val res2 = Success(List(1))
          * scala> stmts.parse("1, 2, ")
          * val res3 = Failure(..) // no trailing comma allowed
          * }}}
          *
          * @param p the parser whose results are collected into a list.
          * @return a parser that parses `p` delimited by commas, returning the list of `p`'s results.
          * @since 4.5.0
          * @group separatorsg
          */
        def commaSep1[A](p: Parsley[A]): Parsley[List[A]] = sepBy1(p, symbol.comma)

        /** This combinator parses a `p` enclosed within parentheses.
          *
          * First parse an open parenthesis, any whitespace, then parse, `p`, producing `x`. Finally, parse a closing parenthesis and any whitespace.
          * If all three parts succeeded, then return `x`. If any of them failed, this combinator fails.
          *
          * @example {{{
          * scala> ...
          * scala> val p = lexer.nonlexeme.enclosing.parens(int)
          * scala> p.parse("( 5)")
          * val res0 = Success(5)
          * scala> p.parse("(5")
          * val res1 = Failure(...)
          * scala> p.parse("5)")
          * val res2 = Failure(...)
          * }}}
          *
          * @param p the parser to parse between parentheses.
          * @return a parser that reads an open parenthesis, then `p`, then a closing parenthesis and returns the result of `p`.
          * @since 4.5.0
          * @group enclosingg
          */
        def parens[A](p: =>Parsley[A]): Parsley[A] = enclosing(p, symbol.openParen, symbol.closingParen, "parentheses")
        /** This combinator parses a `p` enclosed within braces.
          *
          * First parse an open brace, any whitespace, then parse, `p`, producing `x`. Finally, parse a closing brace and any whitespace.
          * If all three parts succeeded, then return `x`. If any of them failed, this combinator fails.
          *
          * @example {{{
          * scala> ...
          * scala> val p = lexer.nonlexeme.enclosing.braces(int)
          * scala> p.parse("{ 5}")
          * val res0 = Success(5)
          * scala> p.parse("{5")
          * val res1 = Failure(...)
          * scala> p.parse("5}")
          * val res2 = Failure(...)
          * }}}
          *
          * @param p the parser to parse between parentheses.
          * @return a parser that reads an open brace, then `p`, then a closing brace and returns the result of `p`.
          * @since 4.5.0
          * @group enclosingg
          */
        def braces[A](p: =>Parsley[A]): Parsley[A] = enclosing(p, symbol.openBrace, symbol.closingBrace, "braces")
        /** This combinator parses a `p` enclosed within angle brackets.
          *
          * First parse an open bracket, any whitespace, then parse, `p`, producing `x`. Finally, parse a closing bracket and any whitespace.
          * If all three parts succeeded, then return `x`. If any of them failed, this combinator fails.
          *
          * @example {{{
          * scala> ...
          * scala> val p = lexer.nonlexeme.enclosing.brackets(int)
          * scala> p.parse("< 5>")
          * val res0 = Success(5)
          * scala> p.parse("<5")
          * val res1 = Failure(...)
          * scala> p.parse("5>")
          * val res2 = Failure(...)
          * }}}
          *
          * @param p the parser to parse between parentheses.
          * @return a parser that reads an open bracket, then `p`, then a closing bracket and returns the result of `p`.
          * @since 4.5.0
          * @group enclosingg
          */
        def angles[A](p: =>Parsley[A]): Parsley[A] = enclosing(p, symbol.openAngle, symbol.closingAngle, "angle brackets")
        /** This combinator parses a `p` enclosed within square brackets.
          *
          * First parse an open bracket, any whitespace, then parse, `p`, producing `x`. Finally, parse a closing bracket and any whitespace.
          * If all three parts succeeded, then return `x`. If any of them failed, this combinator fails.
          *
          * @example {{{
          * scala> ...
          * scala> val p = lexer.nonlexeme.enclosing.brackets(int)
          * scala> p.parse("[ 5]")
          * val res0 = Success(5)
          * scala> p.parse("[5")
          * val res1 = Failure(...)
          * scala> p.parse("5]")
          * val res2 = Failure(...)
          * }}}
          *
          * @param p the parser to parse between parentheses.
          * @return a parser that reads an open bracket, then `p`, then a closing bracket and returns the result of `p`.
          * @since 4.5.0
          * @group enclosingg
          */
        def brackets[A](p: =>Parsley[A]): Parsley[A] = enclosing(p, symbol.openSquare, symbol.closingSquare, "square brackets")

        private def enclosing[A](p: =>Parsley[A], open: Parsley[Unit], close: Parsley[Unit], plural: String) = open ~> p <~ close.explain(s"unclosed $plural")
    }

    /** This object is concerned with ''non-lexemes'': these are tokens that
      * do not give any special treatment to whitespace.
      *
      * Whilst the functionality in `lexeme` is ''strongly'' recommended for
      * wider use in a parser, the functionality here may be useful for more
      * specialised use-cases. In particular, these may for the building blocks
      * for more complex tokens (where whitespace is not allowed between them, say),
      * in which case these compound tokens can be turned into lexemes manually.
      * For example, the lexer does not have configuration for trailing specifiers
      * on numeric literals (like, `1024L` in Scala, say): the desired numeric
      * literal parser could be extended with this functionality ''before'' whitespace
      * is consumed by using the variant found in this object.
      *
      * Alternatively, these tokens can be used for ''lexical extraction'', which
      * can be performed by the [[parsley.errors.ErrorBuilder `ErrorBuilder`]]
      * typeclass: this can be used to try and extract tokens from the input stream
      * when an error happens, to provide a more informative error. In this case,
      * it is desirable to ''not'' consume whitespace after the token to keep the
      * error tight and precise.
      *
      * @groupname numericg Numeric Parsers
      * @groupdesc numericg $numeric
      *
      * @groupname textg String Literal Parsers
      * @groupdesc textg $text
      *
      * @since 4.0.0
      */
    object nonlexeme {
        /** $names
          *
          * @since 4.0.0
          */
        val names: parsley.token.names.Names = new ConcreteNames(desc.nameDesc, desc.symbolDesc, errConfig)

        /** $natural
          *
          * @since 4.5.0
          * @note alias for [[natural `natural`]].
          * @group numericg
          */
        // $COVERAGE-OFF$
        def unsigned: IntegerParsers = natural
        // $COVERAGE-ON$
        /** $natural
          *
          * @since 4.5.0
          * @group numericg
          */
        val natural: IntegerParsers = new UnsignedInteger(desc.numericDesc, errConfig, generic)

        /** $integer
          *
          * @since 4.5.0
          * @note alias for [[integer `integer`]]
          * @see [[unsigned `unsigned`]] for a full description of signed integer configuration
          * @group numericg
          */
        // $COVERAGE-OFF$
        def signed: IntegerParsers = integer
        // $COVERAGE-ON$
        /** $integer
          *
          * @since 4.5.0
          * @see [[natural `natural`]] for a full description of integer configuration
          * @group numericg
          */
        val integer: IntegerParsers = new SignedInteger(desc.numericDesc, natural, errConfig)

        /** $real
          *
          * @since 4.5.0
          * @note alias for [[real `real`]]
          * @see [[natural `natural`]] and [[integer `integer`]] for a full description of the configuration for the start of a real number
          * @group numericg
          */
        // $COVERAGE-OFF$
        def floating: RealParsers = real
        // $COVERAGE-ON$
        private [Lexer] val positiveReal = new UnsignedReal(desc.numericDesc, errConfig, generic)
        /** $real
          *
          * @since 4.5.0
          * @see [[natural `natural`]] and [[integer `integer`]] for a full description of the configuration for the start of a real number
          * @group numericg
          */
        val real: RealParsers = new SignedReal(desc.numericDesc, positiveReal, errConfig)

        /** $unsignedCombined
          *
          * @since 4.5.0
          * @group numericg
          */
        val unsignedCombined: CombinedParsers = new UnsignedCombined(desc.numericDesc, natural, positiveReal, errConfig)
        /** $signedCombined
          *
          * @since 4.5.0
          * @group numericg
          */
        val signedCombined: CombinedParsers = new SignedCombined(desc.numericDesc, unsignedCombined, errConfig)

        // These are not going to be picked up by the debugger... I removed that machinery... oh well
        private val escapes = new Escape(desc.textDesc.escapeSequences, errConfig, generic)
        private val escapeChar = new EscapableCharacter(desc.textDesc.escapeSequences, escapes, space.space, errConfig)
        private val rawChar = new RawCharacter(errConfig)

        /** $character
          *
          * @since 4.5.0
          * @group textg
          */
        val character: CharacterParsers = new ConcreteCharacter(desc.textDesc, escapes, errConfig)
        /** $string
          *
          * @since 4.5.0
          * @group textg
          */
        val string: StringParsers = new ConcreteString(desc.textDesc.stringEnds, escapeChar, desc.textDesc.graphicCharacter, false, errConfig)
        /** $string
          *
          * @note $raw
          * @since 4.5.0
          * @group textg
          */
        val rawString: StringParsers = new ConcreteString(desc.textDesc.stringEnds, rawChar, desc.textDesc.graphicCharacter, false, errConfig)
        /** $multiString
          *
          * @since 4.5.0
          * @group textg
          */
        val multiString: StringParsers = new ConcreteString(desc.textDesc.multiStringEnds, escapeChar, desc.textDesc.graphicCharacter, true, errConfig)
        /** $multiString
          *
          * @note $raw
          * @since 4.5.0
          * @group textg
          */
        val rawMultiString: StringParsers = new ConcreteString(desc.textDesc.multiStringEnds, rawChar, desc.textDesc.graphicCharacter, true, errConfig)

        /** $symbol
          *
          * @since 4.0.0
          */
        val symbol: parsley.token.symbol.Symbol = new ConcreteSymbol(desc.nameDesc, desc.symbolDesc, errConfig)
    }

    /** This combinator ensures a parser fully parses all available input, and consumes whitespace
      * at the start.
      *
      * This combinator should be used ''once'' as the outermost combinator in a parser. It is the
      * only combinator that should consume ''leading'' whitespace, and this must be the first
      * thing a parser does. It will ensure that, after the parser is complete, the end of the
      * input stream has been reached.
      *
      * @since 4.0.0
      */
    def fully[A](p: Parsley[A]): Parsley[A] = {
        val init = if (desc.spaceDesc.whitespaceIsContextDependent) space.init else pure(())
        (((init.ut() *> space.whiteSpace).ut() *> p).ut() <* eof).uo("fully")
    }

    /** This object is concerned with special treatment of whitespace.
      *
      * For the vast majority of cases, the functionality within this
      * object shouldn't be needed, as whitespace is consistently handled
      * by `lexeme` and `fully`. However, for grammars where whitespace
      * is significant (like indentation-sensitive languages), this object
      * provides some more fine-grained control over how whitespace is
      * consumed by the parsers within `lexeme`.
      *
      * @since 4.0.0
      */
    object space {
        private [Lexer] lazy val space = desc.spaceDesc.space.toNative
        private lazy val wsImpl = Ref.make[Parsley[Unit]]

        /** This parser initialises the whitespace used by the lexer when
          * `spaceDesc.whiteSpaceIsContextDependent` is set to `true`.
          *
          * The whitespace is set to the implementation given by the lexical description.
          * This parser '''must''' be used, by `fully` or otherwise, as the first thing
          * the global parser does or an `UnfilledRegisterException` will occur.
          *
          * @note this parser is automatically invoked by the [[fully `fully`]] combinator when applicable.
          * @see [[alter `alter`]] for how to change whitespace during a parse.
          * @since 4.0.0
          */
        def init: Parsley[Unit] = {
            if (!desc.spaceDesc.whitespaceIsContextDependent) {
                throw new UnsupportedOperationException( // scalastyle:ignore throw
                    "Whitespace cannot be initialised unless `spaceDesc.whitespaceIsContextDependent` is true"
                )
            }
            wsImpl.set(configuredWhiteSpace).uo("space.init")
        }

        /** This combinator changes how whitespace is parsed by lexemes for the duration of
          * a given parser.
          *
          * So long as `spaceDesc.whiteSpaceIsContextDependent` is set to `true`, this combinator
          * will be able to locally change the definition of whitespace during the given parser.
          *
          * @example
          *  In indentation sensitive languages, the indentation sensitivity is often ignored
          *  within parentheses or braces. In these cases `lexeme.enclosing.parens(space.alter(withNewline)(p))`
          *  would allow unrestricted newlines within parentheses.
          *
          * @param newSpace the new implementation of whitespace to be used during the execution of `within`.
          * @param within the parser that should be parsed using the updated whitespace.
          * @note the whitespace will not be restored to its original implementation if the
          *       given parser fails having consumed input.
          * @since 4.0.0
          */
        def alter[A](newSpace: CharPred)(within: =>Parsley[A]): Parsley[A] = {
            if (!desc.spaceDesc.whitespaceIsContextDependent) {
                throw new UnsupportedOperationException( // scalastyle:ignore throw
                    "Whitespace cannot be altered unless `spaceDesc.whitespaceIsContextDependent` is true"
                )
            }
            wsImpl.rollback(wsImpl.setDuring(whiteSpace(newSpace))(whiteSpace ~> within))
        }

        /** This parser skips '''zero''' or more (insignificant) whitespace characters as well as comments.
          *
          * The implementation of this parser depends on whether `whitespaceIsContextDependent` is
          * set: when it is, this parser may change based on the use of the `alter` combinator.
          * This parser will always use the `hide` combinator as to not appear as a valid alternative
          * in an error message: it's likely always the case whitespace can be added at any given time,
          * but that doesn't make it a ''useful'' suggestion unless it is significant.
          *
          * @since 4.0.0
          */
        val whiteSpace: Parsley[Unit] = {
            if (desc.spaceDesc.whitespaceIsContextDependent) wsImpl.get.ut().flatten.ut()
            else configuredWhiteSpace.ut()
        }

        /** This parser skips '''zero''' or more comments.
          *
          * The implementation of this combinator does not vary with `whitespaceIsContextDependent`.
          * It will use the `hide` combinator as to not appear as a valid alternative in an error
          * message: adding a comment is often legal, but not a ''useful'' solution for how to make
          * the input syntactically valid.
          *
          * @since 4.0.0
          */
        lazy val skipComments: Parsley[Unit] = {
            if (!desc.spaceDesc.supportsComments) pure(())
            else new Parsley(new singletons.SkipComments(desc.spaceDesc, errConfig))
        }

        private def configuredWhiteSpace: Parsley[Unit] = whiteSpace(desc.spaceDesc.space)
        private def whiteSpace(impl: CharPred): Parsley[Unit] = impl match {
            case NotRequired => skipComments
            case Basic(ws) => new Parsley(new singletons.WhiteSpace(ws, desc.spaceDesc, errConfig))
            // satisfyUtf16 is effectively hidden, and so is Comment
            case Unicode(ws) if desc.spaceDesc.supportsComments =>
                many(new Parsley(new singletons.Comment(desc.spaceDesc, errConfig)) | satisfy(ws).void).void
            case Unicode(ws) => many(satisfy(ws)).void
        }
    }
}
