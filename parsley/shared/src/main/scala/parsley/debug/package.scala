/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import parsley.errors.ErrorBuilder
import parsley.state.Ref

import parsley.internal.deepembedding.frontend

/** This module contains the very useful debugging combinator, as well as breakpoints.
  *
  * @groupprio comb 0
  * @groupname comb (Vanilla) Debug Combinator Extension Methods
  * @groupdesc comb
  *     These are the debugging combinators, which are enabled by bringing these implicit classes
  *     into scope. These are part of base parsley.
  *
  * @groupprio debugcomb 2
  * @groupname debugcomb (parsley-debug) Debug Combinators
  * @groupdesc debugcomb
  *     These are additional debug combinators added by `parsley-debug`,
  *     which allow the attachment of a debug-view, or otherwise use of specialist
  *     debuggers like `detectDivergence`.
  *
  * @groupprio debugview 4
  * @groupname debugview (parsley-debug) Debug Views
  * @groupdesc debugview
  *     These are implementations of renderers for debug traces for `parsley-debug`.
  *
  * @groupprio ctrl 5
  * @groupname ctrl (Vanilla) Debug Control
  * @groupdesc ctrl
  *     These methods can control how the debug mechanism functions in a general way.
  *
  * @groupprio break 10
  * @groupname break (Vanilla) Breakpoints
  * @groupdesc break
  *     These can be used to control how the `debug` combinator operates: when a breakpoint is used
  *     it can halt the execution of the combinator and print out information about the parsers state.
  *
  */
package object debug {
    // $COVERAGE-OFF$
    private [parsley] var renderAscii = false
    /** This method can be used to disable the colored debug output for terminals that don't support it.
      *
      * @group ctrl
      */
    def disableColorRendering(): Unit = renderAscii = true

    /** This class enables the `debug` combinator on parsers.
      *
      * This extension class operates on values that are convertible to parsers. It enables the use of
      * the `debug` combinator, which can be used to trace the execution through a parser.
      *
      * @constructor This constructor should not be called manually, it is designed to be used via Scala's implicit resolution.
      * @param p the value that this class is enabling methods on.
      * @param con a conversion that allows values convertible to parsers to be used.
      * @tparam P the type of base value that this class is used on (the conversion to `Parsley`) is summoned automatically.
      * @group comb
      *
      * @define debug This combinator allows this parser to be debugged by providing a trace through the execution.
      *
      * When this combinator is entered, it will print the name assigned to the parser,
      * as well as the current input context for a few characters on either side.
      * This parser is then executed. If it succeeded, this combinator again reports the
      * name along with "`Good`" and the input context. If it failed, it reports the name
      * along with "`Bad`" and the input context.
      *
      * When breakpoints are used, the execution of the combinator will pause on either entry,
      * exit, or both. The parse is resumed by entering a newline on standard input. Breakpoints
      * will cause additional information about the internal state of the parser to be reported.
      */
    implicit class DebugCombinators[P, A](p: P)(implicit con: P => Parsley[A]) {
        /** $debug
          *
          * @example {{{
          * scala> import parsley.debug.DebugCombinators, parsley.character.string, parsley.Parsley.attempt
          * scala> val abc = attempt(string("abc").debug("string abc")).debug("attempt")
          * scala> val abd = string("abd").debug("string abd")
          * scala> val p = (abc <|> abd).debug("or")
          * scala> p.parse("abd")
          * >or> (1, 1): abd•
          *              ^
          *   >attempt> (1, 1): abd•
          *                     ^
          *     >string abc> (1, 1): abd•
          *                          ^
          *     <string abc< (1, 3): abd• Fail
          *                            ^
          *   <attempt< (1, 1): abd• Fail
          *                     ^
          *   >string abd> (1, 1): abd•
          *                        ^
          *   <string abd< (1, 4): abd• Good
          *                           ^
          * <or< (1, 4): abd• Good
          *                 ^
          * val res0 = Success("abd")
          * }}}
          *
          * @param name The name to be assigned to this parser
          * @param break The breakpoint properties of this parser, defaults to NoBreak
          * @param colored Whether to render with  (default true: render colours)
          * @param watchedRefs Which references to also track the values of and their names, if any
          */
        def debug(name: String, break: Breakpoint, colored: Boolean, watchedRefs: (Ref[_], String)*): Parsley[A] = {
            new Parsley(new frontend.Debug[A](con(p).internal, name, !colored, break, watchedRefs))
        }

        private [parsley] def debug(name: String, break: Breakpoint, colored: Boolean): Parsley[A] = {
            debug(name, break, colored, Seq.empty[(Ref[_], String)]: _*): @org.typelevel.scalaccompat.annotation.nowarn3
        }

        /** $debug
          *
          * @example {{{
          * scala> import parsley.debug.DebugCombinators, parsley.character.string, parsley.Parsley.attempt
          * scala> val abc = attempt(string("abc").debug("string abc")).debug("attempt")
          * scala> val abd = string("abd").debug("string abd")
          * scala> val p = (abc <|> abd).debug("or")
          * scala> p.parse("abd")
          * >or> (1, 1): abd•
          *              ^
          *   >attempt> (1, 1): abd•
          *                     ^
          *     >string abc> (1, 1): abd•
          *                          ^
          *     <string abc< (1, 3): abd• Fail
          *                            ^
          *   <attempt< (1, 1): abd• Fail
          *                     ^
          *   >string abd> (1, 1): abd•
          *                        ^
          *   <string abd< (1, 4): abd• Good
          *                           ^
          * <or< (1, 4): abd• Good
          *                 ^
          * val res0 = Success("abd")
          * }}}
          *
          * Renders in colour.
          *
          * @param name The name to be assigned to this parser
          * @param break The breakpoint properties of this parser, defaults to NoBreak
          * @param watchedRefs Which references to also track the values of and their names, if any
          */
        def debug(name: String, break: Breakpoint, watchedRefs: (Ref[_], String)*): Parsley[A] =
            debug(name, break, colored = true, watchedRefs: _*): @org.typelevel.scalaccompat.annotation.nowarn3

        private [parsley] def debug(name: String, break: Breakpoint): Parsley[A] =
            debug(name, break, Seq.empty[(Ref[_], String)]: _*): @org.typelevel.scalaccompat.annotation.nowarn3

        /** $debug
          *
          * @example {{{
          * scala> import parsley.debug.DebugCombinators, parsley.character.string, parsley.Parsley.attempt
          * scala> val abc = attempt(string("abc").debug("string abc")).debug("attempt")
          * scala> val abd = string("abd").debug("string abd")
          * scala> val p = (abc <|> abd).debug("or")
          * scala> p.parse("abd")
          * >or> (1, 1): abd•
          *              ^
          *   >attempt> (1, 1): abd•
          *                     ^
          *     >string abc> (1, 1): abd•
          *                          ^
          *     <string abc< (1, 3): abd• Fail
          *                            ^
          *   <attempt< (1, 1): abd• Fail
          *                     ^
          *   >string abd> (1, 1): abd•
          *                        ^
          *   <string abd< (1, 4): abd• Good
          *                           ^
          * <or< (1, 4): abd• Good
          *                 ^
          * val res0 = Success("abd")
          * }}}
          *
          * No break-points.
          *
          * @param name The name to be assigned to this parser
          * @param colored Whether to render with colour
          * @param watchedRefs Which references to also track the values of and their names, if any
          */
        def debug(name: String, colored: Boolean, watchedRefs: (Ref[_], String)*): Parsley[A] =
            debug(name, break = NoBreak, colored, watchedRefs: _*): @org.typelevel.scalaccompat.annotation.nowarn3

        private [parsley] def debug(name: String, colored: Boolean): Parsley[A] =
            debug(name, colored, Seq.empty[(Ref[_], String)]: _*): @org.typelevel.scalaccompat.annotation.nowarn3

        /** $debug
          *
          * @example {{{
          * scala> import parsley.debug.DebugCombinators, parsley.character.string, parsley.Parsley.attempt
          * scala> val abc = attempt(string("abc").debug("string abc")).debug("attempt")
          * scala> val abd = string("abd").debug("string abd")
          * scala> val p = (abc <|> abd).debug("or")
          * scala> p.parse("abd")
          * >or> (1, 1): abd•
          *              ^
          *   >attempt> (1, 1): abd•
          *                     ^
          *     >string abc> (1, 1): abd•
          *                          ^
          *     <string abc< (1, 3): abd• Fail
          *                            ^
          *   <attempt< (1, 1): abd• Fail
          *                     ^
          *   >string abd> (1, 1): abd•
          *                        ^
          *   <string abd< (1, 4): abd• Good
          *                           ^
          * <or< (1, 4): abd• Good
          *                 ^
          * val res0 = Success("abd")
          * }}}
          *
          * Renders in colour with no break-point.
          *
          * @param name The name to be assigned to this parser
          * @param watchedRefs Which references to also track the values of and their names, if any
          */
        def debug(name: String, watchedRefs: (Ref[_], String)*): Parsley[A] =
            debug(name, break = NoBreak, colored = true, watchedRefs: _*): @org.typelevel.scalaccompat.annotation.nowarn3

        private [parsley] def debug(name: String): Parsley[A] = debug(name, Seq.empty[(Ref[_], String)]: _*): @org.typelevel.scalaccompat.annotation.nowarn3

        /** Display information about the error messages generated by this parser.
          *
          * This is an experimental debugger that provides internal information about error messages.
          * This provides more detail than one might normally see inside a regular error message, but
          * may help isolate the root cause of an error message not being as expected: this can form the
          * bulk of a specific question on the discussion board.
          *
          * @param name The name to be assigned to this parser
          * @param colored Whether the output should be colourful
          * @param errBuilder The error builder used for formatting messages in the "real parser",
          *                   which is used to help format information in the debugger.
          * @since 4.0.0
          */
        def debugError(name: String, colored: Boolean)(implicit errBuilder: ErrorBuilder[_]): Parsley[A] = {
            new Parsley(new frontend.DebugError[A](con(p).internal, name, !colored, errBuilder))
        }

        /** Display information about the error messages generated by this parser.
          *
          * This is an experimental debugger that provides internal information about error messages.
          * This provides more detail than one might normally see inside a regular error message, but
          * may help isolate the root cause of an error message not being as expected: this can form the
          * bulk of a specific question on the discussion board.
          *
          * @param name The name to be assigned to this parser
          * @param colored Whether the output should be colourful
          * @param errBuilder The error builder used for formatting messages in the "real parser",
          *                   which is used to help format information in the debugger.
          * @since 4.0.0
          */
        def debugError(name: String)(implicit errBuilder: ErrorBuilder[_]): Parsley[A] = debugError(name, colored = true)

        /** This combinator allows for the runtime of this parser to be measured.
          *
          * When this parser executes, its start and end times will be logged using `System.nanoTime()`,
          * which has a resolution of 100ns. These will be logged into the given `Profiler` object.
          *
          * @param name the ''unique'' name of this parser, which will represent it in the table
          * @param profiler the profiling object that will collect and process the data
          * @note usual disclaimers about profiling apply: results are just data; use your judgement
          * @see [[Profiler `Profiler`]]
          * @since 4.4.0
          */
        def profile(name: String)(implicit profiler: Profiler): Parsley[A] = new Parsley(new frontend.Profile[A](con(p).internal, name, profiler))
    }
    // $COVERAGE-ON$
}
