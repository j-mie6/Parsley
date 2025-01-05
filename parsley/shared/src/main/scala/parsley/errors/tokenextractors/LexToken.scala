/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.errors.tokenextractors

import scala.collection.immutable.WrappedString

import parsley.Parsley, Parsley.{atomic, lookAhead, notFollowedBy}
import parsley.Success
import parsley.XAssert.assert
import parsley.character.{item, stringOfSome}
import parsley.combinator.{option, traverse}
import parsley.errors.{ErrorBuilder, Token}
import parsley.position

import org.typelevel.scalaccompat.annotation.unused

/** This extractor mixin provides an implementation for
  * [[parsley.errors.ErrorBuilder.unexpectedToken `ErrorBuilder.unexpectedToken`]] when mixed into
  * an error builder: it will try and parse the residual input to identify a valid lexical token
  * to report.
  *
  * When parsing a grammar that as a dedicated lexical distinction, it is nice to be able to report
  * problematic tokens relevant to that grammar as opposed to generic input lifted straight from the
  * input stream. The easiest way of doing this would be having a pre-lexing pass and parsing based
  * on tokens, but this is deliberately not how Parsley is designed. Instead, this extractor can
  * try and parse the remaining input to try and identify a token on demand.
  *
  * If the `lexicalError` flag of the `unexpectedToken` method is not set, which would indicate a
  * problem within a token reported by a classical lexer and not the parser, the extractor will
  * try to parse each of the provided `tokens` in turn: whichever is the longest matched of these
  * tokens will be reported as the problematic one (this can be changed by overriding `selectToken`).
  * For best effect, these tokens should not consume
  * whitespace (which would otherwise be included at the end of the token!): this means that, if
  * using the `Lexer` class, the functionality in '''`nonlexeme`''' should be used. If one of the
  * givens tokens cannot be parsed, the input until the ''next'' valid parsable token (or end of input)
  * is returned as a `Token.Raw`.
  *
  * Currently, if `lexicalError` is true, this extractor will just return the next character
  * as the problematic item (this may be changed by overriding the `extractItem` method).
  *
  * @since 4.0.0
  */
trait LexToken { this: ErrorBuilder[_] =>
    /** The tokens that should be recognised by this extractor: each parser should return the
      * intended name of the token exactly as it should appear in the `Named` token.
      *
      * This '''should''' include a whitespace parser for "unexpected whitespace".
      *
      * @since 4.0.0
      * @note with the exception of the whitespace parser, these tokens should not consume
      *       trailing (and certainly not leading) whitespace: if using definitions from
      *       `parsley.token.Lexer` functionality, the `nonlexeme` versions of the tokens
      *       should be used.
      */
    def tokens: Seq[Parsley[String]]

    // this parser cannot and must not fail
    private lazy val makeParser: Parsley[Either[::[(String, Int)], String]] = tokens match {
        case t0 +: ts =>
            val toks = traverse(t0, ts: _*)(p => option(lookAhead(atomic(p) <~> position.offset))).map(_.flatten).collect { case toks@(_::_) => toks }
            // this can only fail if either there is no input (which there must be), or there is a token at the front, in which case `rawTok` is not parsed anyway
            val rawTok = stringOfSome(traverse(t0, ts: _*)(notFollowedBy) *> item)
            toks <+> rawTok
        case _ => stringOfSome(_ => true).map(Right(_))
    }

    /** If the extractor is successful in identifying tokens that can be parsed from
      * the residual input, this function will select ''one'' of them to report back.
      *
      * The default behaviour is to take the longest matched token (i.e. the one with
      * the largest paired position). In case of a tie, the first token is chosen:
      * this means that more specific tokens should be put sooner in the `tokens` list.
      *
      * @param matchedToks the list of tokens successfully parsed, along with the position
      *                    at the end of that parse (careful: this position starts back at
      *                    `(1, 1)`, ''not'' where the original parser left off!)
      * @return the chosen token and position pair
      * @note the `matchedToks` list is guaranteed to be non-empty
      * @since 4.0.0
      */
    def selectToken(matchedToks: List[(String, Int)]): (String, Int) = matchedToks.maxBy(_._2)

    private final def selectTokenAndBuild(matchedToks: ::[(String, Int)]): Token = {
        val (name, offset) = selectToken(matchedToks)
        Token.Named(name, offset)
    }

    private final def extractToken(cs: Iterable[Char]): Token = {
        assert(cs.nonEmpty, "we promised that the input is non-empty!")
        val Success(rawOrToks) = makeParser.parse {
            cs match {
                case cs: WrappedString => cs.toString
                case cs => cs.mkString
            }
        }: @unchecked
        rawOrToks.fold(selectTokenAndBuild, Token.Raw.apply)
    }

    /** If the parser failed during the parsing of a token, this function extracts the problematic
      * item from the remaining input.
      *
      * The default behaviour mimics [[parsley.errors.tokenextractors.SingleChar `SingleChar`]].
      *
      * @since 4.0.0
      */
    def extractItem(cs: Iterable[Char], @unused amountOfInputParserWanted: Int): Token = SingleChar.unexpectedToken(cs)

    /** @see [[parsley.errors.ErrorBuilder.unexpectedToken `ErrorBuilder.unexpectedToken`]] */
    override final def unexpectedToken(cs: Iterable[Char], amountOfInputParserWanted: Int, lexicalError: Boolean): Token = {
        if (!lexicalError) extractToken(cs)
        // No lexical extraction should occur here!
        else extractItem(cs, amountOfInputParserWanted)
    }
}

/** This object contains helper functions useful for interacting with `LexToken`.
  * @since 4.0.0
  */
object LexToken {
    /** A convenient way to map a bunch of constant symbols to their names for the
      * the generated error.
      * @since 4.0.0
      */
    def constantSymbols(ps: (Parsley[_], String)*): Seq[Parsley[String]] = ps.map {
        case (p, n) => p.as(n)
    }
}
