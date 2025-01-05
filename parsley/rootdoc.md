This is the documentation for Parsley.

== Package structure ==

The [[parsley]] package contains the [[parsley.Parsley `Parsley`]] class, as well as
the [[parsley.Result `Result`]], [[parsley.Success `Success`]], and [[parsley.Failure `Failure`]]
types. In addition to these, it also contains the following packages and "modules" (a module
is defined as being an object which mocks a package):

  - [[parsley.Parsley$ `parsley.Parsley`]] contains the bulk of the core "function-style" combinators.
  - [[parsley.combinator$ `parsley.combinator`]] contains many helpful combinators that simplify some
    common parser patterns.
  - [[parsley.character$ `parsley.character`]] contains the combinators needed to read characters and
    strings, as well as combinators to match specific sub-sets of characters.
  - [[parsley.debug$ `parsley.debug`]] contains debugging combinators, helpful for identifying faults
    in parsers.
  - [[parsley.expr$ `parsley.expr`]] contains the following sub modules:
    - [[parsley.expr.chain$ `parsley.expr.chain`]] contains combinators used in expression parsing
    - [[parsley.expr.precedence$ `parsley.expr.precedence`]] is a builder for expression parsers built
      on a precedence table.
    - [[parsley.expr.infix$ `parsley.expr.infix`]] contains combinators used in expression parsing,
      but with more permissive types than their equivalents in `chain`.
    - [[parsley.expr.mixed$ `parsley.expr.mixed`]] contains combinators that can be used for
      expression parsing, but where different fixities may be mixed on the same level: this is rare
      in practice.
  - [[parsley.syntax `parsley.syntax`]] contains several implicits to add syntactic sugar
    to the combinators. These are sub-categorised into the following sub modules:
     - [[parsley.syntax.character$ `parsley.syntax.character`]] contains implicits to allow you
       to use character and string literals as parsers.
     - [[parsley.syntax.lift$ `parsley.syntax.lift`]] enables postfix application of the lift
       combinator onto a function (or value).
     - [[parsley.syntax.zipped$ `parsley.syntax.zipped`]] enables boths a reversed form of
       lift where the function appears on the right and is applied on a tuple (useful when type
       inference has failed) as well as a `.zipped` method for building tuples out of several
       combinators.
     - [[parsley.syntax.extension$ `parsley.syntax.extension`]] contains syntactic sugar combinators exposed as
        implicit classes.
  - [[parsley.errors `parsley.errors`]] contains modules to deal with error messages, their refinement
    and generation.
     - [[parsley.errors.combinator$ `parsley.errors.combinator`]] provides combinators that can be
       used to either produce more detailed errors as well as refine existing errors.
     - [[parsley.errors.tokenextractors `parsley.errors.tokenextractors`]] provides mixins for
       common token extraction strategies during error message generation: these can be used to
       avoid implementing [[parsley.errors.ErrorBuilder.unexpectedToken `unexpectedToken`]] in the
       `ErrorBuilder`.
  - [[parsley.lift$ `parsley.lift`]] contains functions which lift functions that work on regular
    types to those which now combine the results of parsers returning those same types. these are
    ubiquitous.
  - [[parsley.ap$ `parsley.ap`]] contains functions which allow for the application of a parser
    returning a function to several parsers returning each of the argument types.
  - [[parsley.state$ `parsley.state`]] contains combinators that interact with the
    context-sensitive functionality in the form of state.
  - [[parsley.token `parsley.token`]] contains the [[parsley.token.Lexer `Lexer`]] class that provides
    a host of helpful lexing combinators when provided with the description of a language.
  - [[parsley.position$ `parsley.position`]] contains parsers for extracting position information.
  - [[parsley.generic$ `parsley.generic`]] contains some basic implementations of
    the ''Parser Bridge'' pattern (see
    [[https://dl.acm.org/doi/10.1145/3550198.3550427 Design Patterns for Parser Combinators in Scala]],
    or the parsley wiki): these can be used before more specialised generic bridge traits can be
    constructed.
