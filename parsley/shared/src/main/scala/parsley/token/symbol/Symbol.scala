/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.symbol

import parsley.Parsley
import parsley.errors.combinator.ErrorMethods

/** This class provides implicit functionality to promote string
  * literals into tokens.
  *
  * @since 4.0.0
  */
abstract class ImplicitSymbol private [symbol] {
    /** This method takes the given string and turns it
      * into a parser for that token.
      *
      * This method can be brought into scope in a parser to
      * allow string literals to naturally serve as tokens.
      * In particular, it will correctly deal with known keywords
      * and operators, and otherwise handle other strings at
      * face-value.
      *
      * @note it is assumed that
      * the token's content is irrelevant, since it is
      * already known what it is, so `Unit` is returned.
      *
      * @since 4.0.0
      */
    implicit def implicitSymbol(s: String): Parsley[Unit]
}

/** This class defines a uniform interface for defining parsers for basic symbols, independent of how whitespace
  * should be handled after the symbol.
  *
  * @define stringApply
  *     This combinator parses a given string as a symbol and
  *     accounts for the possiblility that it is a defined hard
  *     keyword or operator.
  *
  *     A string is treated as a symbol by parsing it atomically
  *     (with `attempt`) as well as ensuring that if it is a known
  *     special symbol, like a keyword, that it is given the correct
  *     treatment. For keywords and operators, this means that the
  *     given string will only parse if it does not otherwise form
  *     a valid prefix of a larger legal identifier or operator.
  *
  * @define charApply
  *     This combinator parses a given character as a symbol.
  *
  *     There is no special treatment of the given character
  *     as a keyword or operator. The result of the parser
  *     is ignored by returning `Unit`.
  *
  * @since 4.0.0
  */
abstract class Symbol private[symbol] {
    /** $stringApply
      *
      * Additionally applies the given label as the name of the symbol.
      *
      * @param name the symbol to parse.
      * @param label the name to give to the symbol in error messages.
      * @since 4.0.0
      */
    final def apply(name: String, label: String): Parsley[Unit] = apply(name).label(label)
    /** $stringApply
      *
      * @param name the symbol to parse.
      * @since 4.0.0
      */
    def apply(name: String): Parsley[Unit]
    /** $charApply
      *
      * Additionally applies the given label as the name of the symbol.
      *
      * @param name the symbol to parse.
      * @param label the name to give to the symbol in error messages.
      * @since 4.0.0
      */
    final def apply(name: Char, label: String): Parsley[Unit] = apply(name).label(label)
    /** $charApply
      *
      * @param name the symbol to parse.
      * @since 4.0.0
      */
    def apply(name: Char): Parsley[Unit]

    /** This combinator parses a given soft keyword atomically:
      * the keyword is only valid if it is not followed directly
      * by a character which would make it a larger valid identifier.
      *
      * ''Soft keywords'' are keywords that are only reserved within
      * certain contexts. The [[parsley.token.symbol.Symbol.apply(name:String)* `apply(String)`]] combinator
      * handles so-called ''hard keywords'' automatically, as the given
      * string is checked to see what class of symbol it might belong to.
      * However, soft keywords are not included in this set, as they are
      * not always reserved in all situations. As such, when a soft keyword
      * does need to be parsed, this combinator should be used to do it
      * explicitly. Care should be taken to ensure that soft keywords
      * take parsing priority over identifiers when they do occur.
      *
      * @since 4.0.0
      */
    def softKeyword(name: String): Parsley[Unit]
    /** This combinator parses a given soft operator atomically:
      * the operator is only valid if it is not followed directly
      * by a character which would make it a larger valid operator
      * (reserved or otherwise).
      *
      * ''Soft operators'' are operators that are only reserved within
      * certain contexts. The [[parsley.token.symbol.Symbol.apply(name:String)* `apply(String)`]] combinator
      * handles so-called ''hard operators'' automatically, as the given
      * string is checked to see what class of symbol it might belong to.
      * However, soft operators are not included in this set, as they are
      * not always reserved in all situations. As such, when a soft operator
      * does need to be parsed, this combinator should be used to do it
      * explicitly.
      *
      * @since 4.0.0
      */
    def softOperator(name: String): Parsley[Unit]

    /** This object can be imported from to expose a way of converting raw Scala string literals
      * into a parser for that specific token.
      *
      * @since 4.0.0
      */
    val implicits: ImplicitSymbol = new ImplicitSymbol {
        /** @inheritdoc */
        implicit def implicitSymbol(s: String): Parsley[Unit] = apply(s).uo(s""""$s"""")
    }

    // $COVERAGE-OFF$
    /** This parser parses a semicolon `;` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val semi: Parsley[Unit] = apply(';')
    /** This parser parses a comma `,` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val comma: Parsley[Unit] = apply(',')
    /** This parser parses a colon `:` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val colon: Parsley[Unit] = apply(':')
    /** This parser parses a dot `.` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val dot: Parsley[Unit] = apply('.')

    /** This parser parses an open parenthesis `(` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val openParen: Parsley[Unit] = apply('(')
    /** This parser parses an open brace `{` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val openBrace: Parsley[Unit] = apply('{')
    /** This parser parses an open square bracket `[` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val openSquare: Parsley[Unit] = apply('[')
    /** This parser parses an open angle bracket `<` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val openAngle: Parsley[Unit] = apply('<')
    /** This parser parses a closing parenthesis `)` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val closingParen: Parsley[Unit] = apply(')')
    /** This parser parses a closing brace `}` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val closingBrace: Parsley[Unit] = apply('}')
    /** This parser parses a closing square bracket `]` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val closingSquare: Parsley[Unit] = apply(']')
    /** This parser parses a closing square bracket `>` as a symbol.
      *
      * @since 4.0.0
      */
    final lazy val closingAngle: Parsley[Unit] = apply('>')
    // $COVERAGE-ON$
}
