{%
laika.versioned = true
laika.site.metadata.description = "An in-depth look at optimising a parser for a sub-set of Haskell."
%}
# Interlude 1: Building a Parser for Haskell

We've covered a lot of ground in the series so far! I think it's time to take a medium-length
break and implement a parser for a (somewhat) simplified Haskell from scratch. Haskell is a
deceptively simple looking language, but is actually a minefield of ambiguous parses and the
whitespace sensitivity in the language makes it even trickier. For now, I'm going to make a
couple of simplifications: firstly, I'm disallowing multi-line _anything_. We are going to make
a `\n` a significant character and not include it in the whitespace parsing (at least until
part 3!). Furthermore, I'm banning `where` clauses, more than one definition in a `let`, guards
on a new line, and `case` statements _must_ use `{}` (these are optional in Haskell, so long as
the code is well-indented). I'm also not going to deal with user defined operators, because the
introduction of these during the parser does create a context-sensitive grammar, which I'd
prefer to avoid for now. With that being said, here's the grammar. I wouldn't recommend spending
too much time looking at the grammar, and instead we'll get started with the fairly easy task of
lexing.

```ebnf
<program> ::= ((<data> | <declaration> | <clause>) NEWLINE)*

<data>         ::= 'data' <con-id> <var-id>* '=' <constructors>
<constructors> ::= <constructor> ['|' <constructors>]
<constructor>  ::= <con-id> <type-atom>*

<declaration> ::= <var-id> '::' <type>

<clause>     ::= <var-id> <pat-naked>* [<guard>] '=' <expr>
<pat-naked>  ::= <var-id> | <pat-con> | '()' | '[]' | <literal> | '_'
               | '(' <pat> ')'
               | '(' <pat> [',' <pat>]+ ')'
               | '[' <pat> [',' <pat>]* ']'
<pat>        ::= <pat-paren> [':' <pat>]
<pat-paren>  ::= <pat-app> | <pat-naked>
<pat-app>    ::= <pat-con> <pat>+
<pat-con>    ::= '(' ','+ ')' | <con-id> | '(' ':' ')'

<guard>     ::= '|' <expr>

<type>      ::= <type-app> ['->' <type>]
<type-app>  ::= <type-atom>+
<type-atom> ::= <type-con> | <var-id> | '()'  | '[' <type> ']'
              | '(' <type> (',' <type> ')')+ | '(' <type> ')'
<type-con>  ::= <con-id> | '[]' | '(' '->' ')' | '(' ','+ ')'

<expr>    ::= [<expr> '$'] <expr-1>
<expr-1>  ::= <expr-2> ['||' <expr-1>]
<expr-2>  ::= <expr-3> ['&&' <expr-2>]
<expr-3>  ::= <expr-4> [('<' | '<=' | '>' | '>=' | '==' | '/=') <expr-4>]
<expr-4>  ::= <expr-5> [(':' | '++') <expr-4>]
<expr-5>  ::= [<expr-5> ('+' | '-')] <expr-6>
<expr-6>  ::= '-' <expr-6> | <expr-7>
<expr-7>  ::= [<expr-7> ('*' | '/')] <expr-8>
<expr-8>  ::= <expr-9> ['^' <expr-8>]
<expr-9>  ::= <expr-10> ['.' <expr-9>]
<expr-10> ::= '\' <pat-naked>+ '->' <expr>
            | 'let' <clause> 'in' <expr>
            | 'if' <expr> 'then' <expr> 'else' <expr>
            | 'case' <expr> 'of' '{' <alt> [(';'|NEWLINE) <alt>]* '}'
            | <func-app>
<alt> ::= <pat> '->' <expr>
<func-app> ::= <term>+
<term> ::= <var-id> | <con-id> | '()' | '(' ','+ ')'
         | '(' <expr> ')'
         | '(' <expr> (',' <expr>)+) ')'
         | '[' [<expr> (',' <expr>)*] ']'
         | <literal>
<literal> ::= FLOAT | INTEGER | STRING | CHAR
<var-id> ::= VAR-ID
<con-id> ::= CON-ID
```

## Lexing
As it turns out, lexing Haskell in Parsley is particularly easy: the `Lexer` class is compliant
with the Haskell specification!

```scala mdoc:silent
import parsley.Parsley

object lexer {
    import parsley.token.{Lexer, Unicode, Basic}
    import parsley.token.descriptions.{LexicalDesc, NameDesc, SymbolDesc, SpaceDesc, NumericDesc, ExponentDesc, EscapeDesc, TextDesc}
    import parsley.character.newline
    private val haskellDesc = LexicalDesc(
        NameDesc.plain.copy(
            identifierStart = Unicode(c => Character.isLetter(c) || c == '_'),
            identifierLetter =
                Unicode(c => Character.isLetterOrDigit(c) || c == '_' || c == '\''),
        ),
        SymbolDesc.plain.copy(
            hardKeywords = Set("if", "then", "else", "data", "where",
                                "let", "in", "case", "of"),
            hardOperators = Set("$", "||", "&&", "<", "<=", ">", ">=", "==", "/=", ":",
                                "++", "+", "-", "*", "/", "^", "."),
        ),
        NumericDesc.plain.copy(
            octalExponentDesc = ExponentDesc.NoExponents,
            binaryExponentDesc = ExponentDesc.NoExponents,
        ),
        TextDesc.plain.copy(
            escapeSequences = EscapeDesc.haskell,
        ),
        SpaceDesc.plain.copy(
            lineCommentStart = "--",
            multiLineCommentStart = "{-",
            multiLineCommentEnd = "-}",
            multiLineNestedComments = true,
            space = Basic(c => c == ' ' || c == '\t'),
        )
    )

    private val lexer = new Lexer(haskellDesc)

    val CON_ID = lexer.lexeme.names.identifier(Basic(_.isUpper))
    val VAR_ID = lexer.lexeme.names.identifier(Basic(_.isLower))
    val INTEGER = lexer.lexeme.natural.number
    val FLOAT = lexer.lexeme.floating.number
    val INT_OR_FLOAT = lexer.lexeme.unsignedCombined.number
    // Strictly speaking, Haskell files are probably encoded as UTF-8, but this
    // is not supported by Parsley _yet_
    val STRING = lexer.lexeme.string.fullUtf16
    val CHAR = lexer.lexeme.character.fullUtf16

    val NEWLINE = lexer.lexeme(newline).void

    def fully[A](p: Parsley[A]) = lexer.fully(p)

    val implicits = lexer.lexeme.symbol.implicits
}
```
The only tricky bit here is identifiers. Ideally, we can make a distinction between so-called
constructor ids and variable ids: this is done by using the `identifier` combinator, which
refines what the first letter of the identifier is allowed to be. I've exposed `INT_OR_FLOAT` to our interface here, since it prevents any backtracking required by `INTEGER | FLOAT`. Also seen here, is the `NEWLINE` token, which we will use in the parser to deliberate delimit newlines, but still
ensure it consumes whitespace! The reason I have picked the shouty-case names is to mimic how tokens sometimes look in grammars. This is purely stylistic, but will help us distinguish between parts of our parser and the primitives of our lexer.

## The AST + _Parser Bridge_ Pattern
Now it's time to build the AST for our Haskell Parser to return. Since we'll be using the
_Parser Bridge_ pattern anyway, I get a choice about whether or not I want position tracking for each
node in the tree. Just to keep the AST looking simple, I'll not track anything. Of course, if I
did change my mind, I could do it here by changing which generic bridge trait is used. More
interesting will be what bridge constructor shapes I pick for each of the AST nodes. Let's start by
just outlining the datatypes themselves and why they are how they are:

```scala mdoc
object ast {
    import parsley.generic._

    case class HaskellProgram(lines: List[ProgramUnit])
    sealed trait ProgramUnit
    case class Data(id: ConId, tys: List[VarId], cons: List[Con]) extends ProgramUnit
    case class Con(id: ConId, tys: List[TyAtom])

    case class Decl(id: VarId, ty: Type) extends ProgramUnit

    case class Clause(id: VarId, pats: List[PatNaked], guard: Option[Expr], rhs: Expr)
        extends ProgramUnit
    sealed trait Pat
    case class PatCons(x: PatParen, xs: Pat) extends Pat
    sealed trait PatParen extends Pat
    case class PatApp(con: PatCon, args: List[PatNaked]) extends PatParen
    sealed trait PatNaked extends PatParen
    case object NilCon extends PatNaked with ParserBridge0[PatNaked]
    case object Wild extends PatNaked with ParserBridge0[PatNaked]
    case class NestedPat(pat: Pat) extends PatNaked
    case class PatTuple(xs: List[Pat]) extends PatNaked
    case class PatList(xs: List[Pat]) extends PatNaked
    sealed trait PatCon extends PatNaked
    case object ConsCon extends PatCon with ParserBridge0[PatCon]

    sealed trait Type
    case class FunTy(argTy: Type_, resTy: Type) extends Type
    sealed trait Type_ extends Type
    case class TyApp(tyF: Type_, tyX: TyAtom) extends Type_
    sealed trait TyAtom extends Type_
    case object UnitTy extends TyAtom with ParserBridge0[TyAtom]
    case class ListTy(ty: Type) extends TyAtom
    case class TupleTy(tys: List[Type]) extends TyAtom
    // This is needed if we want to maximise the well-typedness of the parser
    // For a parser as big as this one, it's definitely desirable: we can always
    // weaken the types later if we want to!
    case class ParenTy(ty: Type) extends TyAtom
    case object ListConTy extends TyAtom with ParserBridge0[TyAtom]
    case object FunConTy extends TyAtom with ParserBridge0[TyAtom]
    case class TupleConTy(arity: Int) extends TyAtom

    // We'll model this layer by layer, to maximise the flexiblity whilst maintaining
    // The type safety: by using subtyping, we can avoid useless wrapper constructors
    sealed trait Expr
    case class WeakApp(f: Expr, arg: Expr1) extends Expr
    sealed trait Expr1 extends Expr
    case class Or(x: Expr2, y: Expr1) extends Expr1
    sealed trait Expr2 extends Expr1
    case class And(x: Expr3, y: Expr2) extends Expr2
    sealed trait Expr3 extends Expr2
    // We could certainly compress this by factoring out the op!
    // Notice that these operators have Expr4 on both sides: this implies they are
    // not left _or_ right associative! x < y < z is not legal in Haskell
    case class Less(x: Expr4, y: Expr4) extends Expr3
    case class LessEqual(x: Expr4, y: Expr4) extends Expr3
    case class Greater(x: Expr4, y: Expr4) extends Expr3
    case class GreaterEqual(x: Expr4, y: Expr4) extends Expr3
    case class Equal(x: Expr4, y: Expr4) extends Expr3
    case class NotEqual(x: Expr4, y: Expr4) extends Expr3
    sealed trait Expr4 extends Expr3
    case class Cons(x: Expr5, xs: Expr4) extends Expr4
    case class Append(xs: Expr5, ys: Expr4) extends Expr4
    sealed trait Expr5 extends Expr4
    case class Add(x: Expr5, y: Expr6) extends Expr5
    case class Sub(x: Expr5, y: Expr6) extends Expr5
    sealed trait Expr6 extends Expr5
    case class Negate(x: Expr6) extends Expr6
    sealed trait Expr7 extends Expr6
    case class Mul(x: Expr7, y: Expr8) extends Expr7
    case class Div(x: Expr7, y: Expr8) extends Expr7
    sealed trait Expr8 extends Expr7
    case class Exp(x: Expr9, y: Expr8) extends Expr8
    sealed trait Expr9 extends Expr8
    case class Comp(f: Expr10, g: Expr9) extends Expr9
    sealed trait Expr10 extends Expr9
    case class Lam(args: List[Pat], body: Expr) extends Expr10
    case class Let(binding: Clause, in: Expr) extends Expr10
    case class If(cond: Expr, thenExpr: Expr, elseExpr: Expr) extends Expr10
    case class Case(scrutinee: Expr, cases: List[Alt]) extends Expr10
    case class Alt(pat: Pat, body: Expr)
    sealed trait Expr10_ extends Expr10
    case class StrongApp(f: Expr10_, arg: Term) extends Expr10_

    sealed trait Term extends Expr10_
    case class ConId(v: String) extends Term with PatCon with TyAtom
    case class VarId(v: String) extends Term with PatNaked with TyAtom
    case object UnitCon extends Term with PatNaked with ParserBridge0[Term with PatNaked]
    case class TupleCon(arity: Int) extends Term with PatCon
    case class ParensVal(x: Expr) extends Term
    case class TupleLit(xs: List[Expr]) extends Term
    case class ListLit(xs: List[Expr]) extends Term

    trait Literal extends Term with PatNaked
    case class HsInt(x: BigInt) extends Literal
    case class HsString(s: String) extends Literal
    case class HsChar(c: Int) extends Literal
    case class HsDouble(x: BigDecimal) extends Literal

    object WeakApp extends ParserBridge2[Expr, Expr1, Expr]
    object Or extends ParserBridge2[Expr2, Expr1, Expr1]
    object And extends ParserBridge2[Expr3, Expr2, Expr2]
    object Less extends ParserBridge2[Expr4, Expr4, Expr3]
    object LessEqual extends ParserBridge2[Expr4, Expr4, Expr3]
    object Greater extends ParserBridge2[Expr4, Expr4, Expr3]
    object GreaterEqual extends ParserBridge2[Expr4, Expr4, Expr3]
    object Equal extends ParserBridge2[Expr4, Expr4, Expr3]
    object NotEqual extends ParserBridge2[Expr4, Expr4, Expr3]
    object Cons extends ParserBridge2[Expr5, Expr4, Expr4]
    object Append extends ParserBridge2[Expr5, Expr4, Expr4]
    object Add extends ParserBridge2[Expr5, Expr6, Expr5]
    object Sub extends ParserBridge2[Expr5, Expr6, Expr5]
    object Negate extends ParserBridge1[Expr6, Expr6]
    object Mul extends ParserBridge2[Expr7, Expr8, Expr7]
    object Div extends ParserBridge2[Expr7, Expr8, Expr7]
    object Exp extends ParserBridge2[Expr9, Expr8, Expr8]
    object Comp extends ParserBridge2[Expr10, Expr9, Expr9]
    object FunTy extends ParserBridge2[Type_, Type, Type]
    object Lam extends ParserBridge2[List[Pat], Expr, Lam]
    object Let extends ParserBridge2[Clause, Expr, Let]
    object If extends ParserBridge3[Expr, Expr, Expr, If]
    object Case extends ParserBridge2[Expr, List[Alt], Case]
    object Alt extends ParserBridge2[Pat, Expr, Alt]
    object ConId extends ParserBridge1[String, ConId]
    object VarId extends ParserBridge1[String, VarId]
    object TupleCon extends ParserBridge1[Int, TupleCon]
    object ParensVal extends ParserBridge1[Expr, ParensVal]
    object TupleLit extends ParserBridge1[List[Expr], TupleLit]
    object ListLit extends ParserBridge1[List[Expr], ListLit]
    object HsInt extends ParserBridge1[BigInt, HsInt]
    object HsString extends ParserBridge1[String, HsString]
    object HsChar extends ParserBridge1[Int, HsChar]
    object HsDouble extends ParserBridge1[BigDecimal, HsDouble]
    object Data extends ParserBridge3[ConId, List[VarId], List[Con], Data]
    object Con extends ParserBridge2[ConId, List[TyAtom], Con]
    object Decl extends ParserBridge2[VarId, Type, Decl]
    object Clause extends ParserBridge4[VarId, List[PatNaked], Option[Expr], Expr, Clause]
    object PatCons extends ParserBridge2[PatParen, Pat, Pat]
    object PatApp extends ParserBridge2[PatCon, List[PatNaked], PatApp]
    object NestedPat extends ParserBridge1[Pat, NestedPat]
    object PatTuple extends ParserBridge1[List[Pat], PatTuple]
    object PatList extends ParserBridge1[List[Pat], PatList]
    object TupleConTy extends ParserBridge1[Int, TupleConTy]
    object ParenTy extends ParserBridge1[Type, ParenTy]
    object TupleTy extends ParserBridge1[List[Type], TupleTy]
    object ListTy extends ParserBridge1[Type, ListTy]
}
```

There is a lot of constructors here, but that's because the grammar is quite big. Notice that
the shape of the AST roughly follows the shape of the grammar (down to the naming). Subtyping
has been used where the same rule can appear in multiple places (`ConId` or `VarId` for
instance). This helps keep the parser simple whilst still providing a level of type safety. We
know, for instance, the associativity of the operators purely based on their types alone. By
generalising, we can see that left-associative types are shaped like `(B, A) => B`, right
associative ones as `(A, B) => B` and non-associative ones as `(A, A) => B`. This is a helpful
guide for us, and in fact it will also ensure that we can't get the precedence table "wrong". A
consequence, as we'll see later, is we will be forced to use `SOps` instead of `Ops` in the
precedence tables.

The bridges have all also been defined above as well, including those marked
with `ParserBridge0`, which is done on the object itself.

Notably though, there are two AST nodes I'm _not_ going to give bridge constructors to: `StrongApp`, `TyApp`. If you look at
the grammar, you'll see that the two relevant rules are both just `many`-like. Another option for
these datatypes would have been to have taken a `List` of the sub-parses. But, morally, Haskell
function applications are done one at a time so I've not flattened the structure, and as we'll see,
we'll use a `reduce` to handle these cases. In reality, providing a position to either of these two
nodes is quite difficult, because they are actually both delimited by `' '`, so there really isn't a
sensible position to latch on to.

## Parsing
Now it's finally time to tackle the parser itself. Remember, our lexer handles all whitespace
(except newlines) for us, and our bridge constructors would handle position information for us. The
type of the AST is going to help us make sure that the parsers are constructed in the right way. One
concern we need to be aware of is notice areas of the grammar where ambiguity lies, and make sure
we resolve it properly with `atomic` (and only when needed!). Let's start at the bottom and work
our way up: this means tackling, in order, atoms, expressions, patterns, clauses, types,
declarations, and then finally data.

### Atoms of the Expression
Let's make a start with `<term>`. There is nothing particular special about this, but we will need
to be careful to handle the difference between tupled expressions and parenthesised expressions.
There are a couple of ways we can try and tackle this: the first is to write both cases out and be
sure to `atomic` one of them, so we can back out if required. The second is to parse them as one
case together and then disambiguate which of the constructors should be used in a bridge! I'm
going to take the first approach for now, and then we can revisit later. Now for the `<term>`
parser:

```scala mdoc:invisible
val /???/ = parsley.Parsley.empty
```

```scala mdoc:silent
import parsley.Parsley.atomic
import parsley.combinator.{sepBy, sepBy1, countSome}

import lexer._
import implicits.implicitSymbol
import ast._

val `<literal>` = atomic(HsDouble(FLOAT)) | HsInt(INTEGER) | HsString(STRING) | HsChar(CHAR)
val `<var-id>` = VarId(VAR_ID)
val `<con-id>` = ConId(CON_ID)

val `<expr>`: Parsley[Expr] = /???/

val `<term>` = ( `<var-id>` | `<con-id>` | (UnitCon from "()")
               | atomic(TupleCon("(" ~> countSome(",") <~ ")"))
               | atomic(ParensVal("(" ~> `<expr>` <~ ")"))
               | TupleLit("(" ~> sepBy1(`<expr>`, ",") <~ ")")
               | ListLit("[" ~> sepBy(`<expr>`, ",") <~ "]")
               | `<literal>`
               )
```

Here, I've followed the structure of the grammar quite closely. I'm even making sure to follow the
same order that the grammar uses: this means that I use `lazy val` for any parser which forward
references another grammar rule (this is why I didn't need the laziness in the bridges). I'm making
use of the `sepBy` and `sepBy1` combinators to handle the comma separated values and parse them into
a list. Notice that there are three instances of backtracking _alone_ in this parser. Tuple
constructions (like `(,,,)`), parenthesised values, and tuple literals all share the `(` token. That
being said, so does `()`, but there it's been treated as a single atomic token by our
`implicitSymbol`, so no backtracking required at the branching level. As I said, this could get
expensive, so we will re-visit it later. The same crops up with `FLOAT` and `INTEGER`, which may
also overlap with each other: again, we will re-visit this later and use the `FLOAT_OR_INT` token
instead. To deal with the number of `,` representing the arity of a tuple constructor operator, I've
also drafted up a `countSome` combinator, which will parse its given parser one or more times, counting
the number of successes. You'll also notice that, in Scala, anything goes between backticks! For
this parser, I'll go with this notation to make it match a little more closely with the grammar
itself (and for variety): it's up to you whether or not you like this notation. One thing that's
nice about it is that it clearly distinguishes between our combinators and the grammar rules
themselves.

### Expressions
Next we'll tackle up to `<expr>`. By now, we should know that the correct tool to reach for is
`precedence`. While `<expr-10>` can be considered to have operators in it, they do not fit with
any associativity in Haskell so I will split them out for digestibility. Without further ado, let's get going:

```scala mdoc:nest
import parsley.expr.{precedence, SOps, InfixL, InfixR, InfixN, Prefix, Atoms}

lazy val `<expr>`: Parsley[Expr] = precedence {
    SOps(InfixL)(WeakApp from "$") +:
    SOps(InfixR)(Or      from "||") +:
    SOps(InfixR)(And     from "&&") +:
    SOps(InfixN)(Less    from "<",  LessEqual    from "<=",
                 Greater from ">",  GreaterEqual from ">=",
                 Equal   from "==", NotEqual     from "/=") +:
    SOps(InfixR)(Cons    from ":",  Append       from "++") +:
    SOps(InfixL)(Add     from "+",  Sub          from "-") +:
    SOps(Prefix)(Negate  from "-") +:
    SOps(InfixL)(Mul     from "*",  Div          from "/") +:
    SOps(InfixR)(Exp     from "^") +:
    SOps(InfixR)(Comp    from ".") +:
    `<expr-10>`
}
lazy val `<expr-10>` = Atoms(/???/)
```
```scala mdoc:invisible
import scala.annotation.unused
val _ = `<expr>`: @unused
```

Here we can see a whole bunch of interesting things! Firstly, up to this point we've been used to
seeing `Ops` in our precedence, where here we are using `SOps` and the `Levels` list. This is
important, because our AST is far more strongly typed. If we made each layer of the tree the same
`(Expr, Expr) => Expr` shape, then we could use `Ops` as we've been used to in other pages.
However, since I opted to make a more strongly typed tree using subtyping, we have to use the more
complex and general `SOps` precedence architecture. This has some really nice consequences:

1) if, say, I removed `SOps(InfixR)(Exp <# "^")` from the list, it would no longer compile
2) if, say, I accidentally said `SOps(InfixL)(Exp <# "^")`, then it would no longer compile
3) if, say, I reordered the lines in the list, then it would no longer compile
4) by using subtyping, we don't need to provide any explicit wrapper constructors

Next up is the remaining three rules: `<expr-10>`, `<alt>`, and `<func-app>`.

```scala mdoc:nest:silent
import parsley.Parsley.{some, many}

val `<clause>`: Parsley[Clause] = /???/
val `<pat-naked>`: Parsley[PatNaked] = /???/
val `<pat>`: Parsley[Pat] = /???/

val `<alt>` = Alt(`<pat>`, "->" ~> `<expr>`)
lazy val `<func-app>` = `<term>`.reduceLeft(StrongApp)
lazy val `<expr-10>` = Atoms(
    Lam("\\" ~> some(`<pat-naked>`), "->" ~> `<expr>`),
    Let("let" ~> `<clause>`, "in" ~> `<expr>`),
    If("if" ~> `<expr>`, "then" ~> `<expr>`, "else" ~> `<expr>`),
    Case("case" ~> `<expr>`,
         "of" ~> "{" ~> sepBy1(`<alt>`, (";" | NEWLINE) <~ many(NEWLINE)) <~ "}"),
    `<func-app>`)
```
```scala mdoc:invisible
val _ = `<expr-10>`: @unused
```

This section of the parser is much more straightforward: we are using the regular shape of the
grammar in conjunction with our bridge constructors. Notice here we are explicitly making use of
`NEWLINE`, so that we can make multi-line `case` statements. This is ok because we have explicit
curly braces to delimit the start and end of the `case`. Note here that the `"\\"` is just parsing a
`\`, but the backslash much be escaped to fit within the Scala string! The `<func-app>` rule is
interesting, because it is the same as ```some(`<term>`).map(_.reduceLeft(StrongApp))```, but is
more efficient, not having to have to construct an intermediate list. It's always a good idea to
check out `parsley`'s API to see if you can find any hidden gems like this one!

Now we have tackled everything from `<expr>` down, we are now in a position to deal with `<clause>`
and its sub grammars.

### Clauses
These set of parsers are similar to what we saw with `<term>`. There are going to be elements of
ambiguity in the grammar that need to be resolved with `atomic`, specifically those involving
parentheses. But other than that there isn't anything really new here.

```scala mdoc:nest
import parsley.Parsley.many
import parsley.combinator.option
import parsley.expr.infix

lazy val `<clause>` =
    Clause(`<var-id>`, many(`<pat-naked>`), option(`<guard>`), "=" ~> `<expr>`)
lazy val `<pat-naked>`: Parsley[PatNaked] =
    ( `<var-id>` | atomic(`<pat-con>`)
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | atomic(NestedPat("(" ~> `<pat>` <~ ")"))
    | PatTuple("(" ~> sepBy1(`<pat>`, ",") <~ ")")
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )
lazy val `<pat>` = infix.right1(`<pat-paren>`)(PatCons from ":")
lazy val `<pat-paren>` = atomic(`<pat-app>`) | `<pat-naked>`
lazy val `<pat-app>` = PatApp(`<pat-con>`, some(`<pat-naked>`))
lazy val `<pat-con>` = ( atomic("(" ~> (ConsCon from ":") <~ ")")
                       | TupleCon("(" ~> countSome(",") <~ ")")
                       | `<con-id>`
                       )

lazy val `<guard>` = "|" ~> `<expr>`
```
```scala mdoc:invisible
val _ = `<clause>`: @unused
```

Like I said, nothing too interesting here. Notice, however, that for `<pat>` I have used a
`infix.right1`: we've been used to using `precedence`, but in cases like these the `chain`s are just
so much more simple. Don't be afraid to make use of them! There are plenty of  `atomic`s here that we
will come back and eliminate later on: notably, the `atomic` in `<pat-paren>` is used to guard
against a `<pat-con>` being parsed without any `<pat-naked>` - this needs to be parsed again in
`<pat-naked>` itself.

### Types
Again, here we can just see more examples of the same concepts we've already been working with.
There is a sense in which we've really reached the limit of stuff we need for our practical common
cases: there isn't much more to say until we try and deal with much more complex grammatical
features.

```scala mdoc
lazy val `<type>`: Parsley[Type] = infix.right1(`<type-app>`)(FunTy from "->")
lazy val `<type-app>` = `<type-atom>`.reduceLeft(TyApp)
lazy val `<type-atom>` = ( `<type-con>` | `<var-id>` | (UnitTy from "()")
                         | ListTy("[" ~> `<type>` <~ "]")
                         | atomic(ParenTy("(" ~> `<type>` <~ ")"))
                         | TupleTy("(" ~> sepBy1(`<type>`, ",") <~ ")")
                         )
lazy val `<type-con>` = ( `<con-id>`
                        | (ListConTy from "[]")
                        | atomic("(" ~> (FunConTy from "->") <~ ")")
                        | atomic(TupleConTy("(" ~> countSome(",") <~ ")"))
                        )
```

We can see another instance of `infix.right1` out in the wild here, as well as the other `reduceLeft`
used for type application. Yet again, there is some ambiguity evidenced by the `atomic` here, and
just like in the other instances, it's to do with parentheses. The second `atomic` in `<type-con>` is
interesting: it's not clear from the rule itself why it is there. In fact, it's there because we
need to be able to backtrack out of `<type-con>` during `<type-atom>`; however, since the ambiguity
only arises from the tuple constructor (the function constructor doesn't cause ambiguity in this case,
because of the existing `atomic`), we don't need to enable backtracking on the _entire_ rule.
Finally we can move onto the top level parsers and start tackling the performance gotchas: this is
the more challenging aspect of managing a parser of this size and complexity.

### Declarations and Data
Here is a nice easy finish. These last rules are really just book-keeping. I'm also going to
introduce a way of running the parser directly.

```scala mdoc
import parsley.Parsley.some
import parsley.combinator.sepEndBy
import parsley.errors.ErrorBuilder

def parse[Err: ErrorBuilder](input: String) = `<program>`.parse(input)

lazy val `<program>` =
    fully(sepEndBy(`<data>` | atomic(`<declaration>`) | `<clause>`, some(NEWLINE)))

lazy val `<data>` = Data("data" ~> `<con-id>`, many(`<var-id>`), "=" ~> `<constructors>`)
lazy val `<constructors>` = sepBy1(`<constructor>`, "|")
lazy val `<constructor>` = Con(`<con-id>`, many(`<type-atom>`))

lazy val `<declaration>` = Decl(`<var-id>`, "::" ~> `<type>`)
```
```scala mdoc:invisible
val _ = (x: String) => parse(x): @unused
```

We do have to be careful here, there is some overlap between `<declaration>` and `<clause>`. This,
unfortunately, is unavoidable without more involved work, but the overlap is just a single variable
name, so it's fairly minor. That being said, really, the scope on that `atomic` is too big: once we've
seen the `"::"`, we _know_ for sure that everything that follows (good or bad) is a type. At the
moment, if something bad happens in the type, it can backtrack all the way out and try reading a
clause instead. This isn't too much of a problem for us here, since the clause will also fail and
the declaration's error message got further (and so takes precedence), but suppose the backtracking
allowed the parser to succeed somehow (perhaps it was ```many(`<declaration>`)```) then the error
will for sure be in the wrong place!

The `parse` method is our hook to the outside world. I've written it "properly"
here so that it is parameterised by an instance of the `ErrorBuilder` typeclass. We could omit this,
but then we'd always have to run it with the `ErrorBuilder` in scope for whatever concrete `Err` we
choose (in most cases, `String`). It's useful to do this in case your unit tests want to test for
the correctness of error messages!

## Optimising the Parser
### Fixing Parentheses Backtracking
Before we finish up this page, and move onto learning about error messages, let's deal with the last
remaining warts in this version of our parser. There are several instances of backtracking: some are
very avoidable, and others are potentially expensive. We will progress through them, in some cases
making incremental changes. As I've said before, dealing with backtracking in an effective way is in
most cases the trickiest part of actually writing a parser. Make sure you understand how the parser
has been built up so far and convince yourself about why each instance of `atomic` in the previous
parsers has been necessary before continuing.

#### `<pat-con>`, `<type-con>`, and `<term>`
These three grammar rules are very straightforward to factor out and remove the backtracking for,
so we will start with them first. Let's remind ourselves of the three rules in question and identify
the parts we can handle:

```scala mdoc:nest:silent
lazy val `<pat-con>` = ( atomic("(" ~> (ConsCon from ":") <~ ")")
                       | TupleCon("(" ~> countSome(",") <~ ")")
                       | `<con-id>`
                       )

val `<type-con>` = ( `<con-id>`
                   | (ListConTy from "[]")
                   | atomic("(" ~> (FunConTy from "->") <~ ")")
                   | atomic(TupleConTy("(" ~> countSome(",") <~ ")"))
                   )

val `<term>` = ( `<var-id>` | `<con-id>` | (UnitCon from "()")
               | atomic(TupleCon("(" ~> countSome(",") <~ ")"))
               | atomic(ParensVal("(" ~> `<expr>` <~ ")"))
               | TupleLit("(" ~> sepBy1(`<expr>`, ",") <~ ")")
               | ListLit("[" ~> sepBy(`<expr>`, ",") <~ "]")
               | `<literal>`
               )
```
```scala mdoc:invisible
val _ = `<pat-con>`: @unused
```

Now, with `<pat-con>` and `<type-con>`, they both contain backtracking because there are two portions
of the parser which lie within parentheses. You can see the same thing in the `<term>` parser,
however, as we'll see, `<term>` will require a bit more extra work to fix the second instance of
backtracking. Thankfully, these are all relatively easy to fix: we just need to distribute the
parentheses through the rules that contain them on the _inside_. This is a nice warm-up exercise:

```scala mdoc:nest:silent
lazy val `<pat-con>` = ( atomic("(" ~> (ConsCon from ":") <~ ")")
                       | "(" ~> TupleCon(countSome(",")) <~ ")"
                       | `<con-id>`
                       )

val `<type-con>` = ( `<con-id>`
                   | (ListConTy from "[]")
                   | atomic("(" ~> (FunConTy from "->") <~ ")")
                   | atomic("(" ~> TupleConTy(countSome(",")) <~ ")")
                   )

val `<term>` = ( `<var-id>` | `<con-id>` | (UnitCon from "()")
               | atomic("(" ~> TupleCon(countSome(",")) <~ ")")
               | atomic("(" ~> ParensVal(`<expr>`) <~ ")")
               | "(" ~> TupleLit(sepBy1(`<expr>`, ",")) <~ ")"
               | ListLit("[" ~> sepBy(`<expr>`, ",") <~ "]")
               | `<literal>`
               )
```
```scala mdoc:invisible
val _ = `<pat-con>`: @unused
```

With the parentheses distributed, we can see that they are easily factored out (on both the left-
and the right-hand sides):

```scala mdoc:nest:silent
lazy val `<pat-con>` = ( "(" ~> ((ConsCon from ":") | TupleCon(countSome(","))) <~ ")"
                       | `<con-id>`
                       )

val `<type-con>` = ( `<con-id>`
                   | (ListConTy from "[]")
                   | atomic("(" ~> ((FunConTy from "->") | TupleConTy(countSome(","))) <~ ")")
                   )

val `<term>` = ( `<var-id>` | `<con-id>` | (UnitCon from "()")
               | "(" ~> ( TupleCon(countSome(","))
                        | atomic(ParensVal(`<expr>`))
                        | TupleLit(sepBy1(`<expr>`, ","))
                        ) <~ ")"
               | ListLit("[" ~> sepBy(`<expr>`, ",") <~ "]")
               | `<literal>`
               )
```
```scala mdoc:invisible
val _ = `<pat-con>`: @unused
```

This has immediately eliminated three of the `atomic`s, but one persists inside `<term>`: this is
because `ParensVal` and `TupleLit` both share an `<expr>`. This is a bit trickier to eliminate, but
let's move on to tackling these. Note that the other `atomic` in `<type-con>` is due to a conflict
in the wider grammar, we can look at that in part 4.

#### `<pat-naked>`, `<type-atom>`, and `<term>` (again)
The next three grammar rules contain similar patterns to the last three, but solving the backtracking
is less obvious. Let's start by recapping what the three rules are:

```scala mdoc:nest:silent
lazy val `<pat-naked>`: Parsley[PatNaked] =
    ( `<var-id>` | atomic(`<pat-con>`)
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | atomic(NestedPat("(" ~> `<pat>` <~ ")"))
    | PatTuple("(" ~> sepBy1(`<pat>`, ",") <~ ")")
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )

lazy val `<type-atom>` = ( `<type-con>` | `<var-id>` | (UnitTy from "()")
                         | ListTy("[" ~> `<type>` <~ "]")
                         | atomic(ParenTy("(" ~> `<type>` <~ ")"))
                         | TupleTy("(" ~> sepBy1(`<type>`, ",") <~ ")")
                         )

val `<term>` = ( `<var-id>` | `<con-id>` | (UnitCon from "()")
               | "(" ~> ( TupleCon(countSome(","))
                        | atomic(ParensVal(`<expr>`))
                        | TupleLit(sepBy1(`<expr>`, ","))
                        ) <~ ")"
               | ListLit("[" ~> sepBy(`<expr>`, ",") <~ "]")
               | `<literal>`
               )
```
```scala mdoc:invisible
val _ = `<pat-naked>`: @unused
val _ = `<type-atom>`: @unused
```

First, let's do what we did to `<term>` to `<pat-naked>` and `<type-atom>`:

```scala mdoc:nest:silent
lazy val `<pat-naked>`: Parsley[PatNaked] =
    ( `<var-id>` | atomic(`<pat-con>`)
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | "(" ~> ( atomic(NestedPat(`<pat>`))
             | PatTuple(sepBy1(`<pat>`, ","))
             ) <~ ")"
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )

lazy val `<type-atom>` = ( `<type-con>` | `<var-id>` | (UnitTy from "()")
                         | ListTy("[" ~> `<type>` <~ "]")
                         | "(" ~> ( atomic(ParenTy(`<type>`))
                                  | TupleTy(sepBy1(`<type>`, ","))
                                  ) <~ ")"
                         )

val `<term>` = ( `<var-id>` | `<con-id>` | (UnitCon from "()")
               | "(" ~> ( TupleCon(countSome(","))
                        | atomic(ParensVal(`<expr>`))
                        | TupleLit(sepBy1(`<expr>`, ","))
                        ) <~ ")"
               | ListLit("[" ~> sepBy(`<expr>`, ",") <~ "]")
               | `<literal>`
               )
```
```scala mdoc:invisible
val _ = `<pat-naked>`: @unused
val _ = `<type-atom>`: @unused
```

Hopefully you can see that all three rules are similar to each other: they all have an `atomic`
_inside_ the factored parentheses. The problem is that they use different bridge constructors and the
`sepBy1` does not allow for easy factoring. That being said, we could deal with this by creating... a
new ***disambiguator bridge***! Let's take a look at them:

```scala mdoc:invisible
import parsley.generic._
```
```scala mdoc
object NestedPatOrPatTuple extends ParserBridge1[List[Pat], PatNaked] {
    def apply(ps: List[Pat]): PatNaked = ps match {
        case List(p) => NestedPat(p)
        case ps => PatTuple(ps)
    }
}

object ParenTyOrTupleTy extends ParserBridge1[List[Type], TyAtom] {
    def apply(tys: List[Type]): TyAtom = tys match {
        case List(ty) => ParenTy(ty)
        case tys => TupleTy(tys)
    }
}

object TupleLitOrParensVal extends ParserBridge1[List[Expr], Term] {
    def apply(xs: List[Expr]): Term = xs match {
        case List(x) => ParensVal(x)
        case xs => TupleLit(xs)
    }
}
```

Compared with _**bridge constructors**_, _**disambiguator bridges**_ are used to arbitrate between
several possible instantiations dependending on the data that is fed to them. These bridges each
encapsulate both of the overlapping cases. If the list of results only has one element, then it
indicates we should not be creating a tuple. With these new bridges replacing the old ones, we can
adjust our parsers:

```scala mdoc:nest:silent
lazy val `<pat-naked>`: Parsley[PatNaked] =
    ( `<var-id>` | atomic(`<pat-con>`)
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | NestedPatOrPatTuple("(" ~> sepBy1(`<pat>`, ",") <~ ")")
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )

lazy val `<type-atom>` = ( `<type-con>` | `<var-id>` | (UnitTy from "()")
                         | ListTy("[" ~> `<type>` <~ "]")
                         | ParenTyOrTupleTy("(" ~> sepBy1(`<type>`, ",") <~ ")")
                         )

val `<term>` = ( `<var-id>` | `<con-id>` | (UnitCon from "()")
               | "(" ~> ( TupleCon(countSome(","))
                        | TupleLitOrParensVal(sepBy1(`<expr>`, ","))
                        ) <~ ")"
               | ListLit("[" ~> sepBy(`<expr>`, ",") <~ "]")
               | `<literal>`
               )
```
```scala mdoc:invisible
val _ = `<pat-naked>`: @unused
val _ = `<type-atom>`: @unused
```

By making use of our special disambiguators bridge, we've eliminated the pesky `atomic`s. I've put the
parentheses back inside the bridge call for the `<pat-naked>` and `<type-atom>` rules, because, in my opinion, it looks a bit cleaner.

#### Numbers in `<literal>`
With the tools we've already developed so far, this one is easy. Let's remind ourselves of the rule
first:

```scala mdoc:nest:silent
/*
val INTEGER = lexer.lexeme.numeric.natural.number
val FLOAT = lexer.lexeme.numeric.floating.number
val INT_OR_FLOAT = lexer.lexeme.numeric.unsignedCombined.number
*/
val `<literal>` = atomic(HsDouble(FLOAT)) | HsInt(INTEGER) | HsString(STRING) | HsChar(CHAR)
```

The problem here is that floats and ints share a common leading prefix: the whole number part. When
we defined the `lexer`, I mentioned that it supports a `INT_OR_FLOAT` token. Now it's time to make
use of it to remove this `atomic`. Our first thought might be to make a disambiguator bridge that can accommodate either of them, and that would be a fine idea:

```scala mdoc:nest:silent
object HsIntOrDouble extends ParserBridge1[Either[BigInt, BigDecimal], Literal] {
    def apply(x: Either[BigInt, BigDecimal]): Literal = x.fold(HsInt(_), HsDouble(_))
}

val `<literal>` = HsIntOrDouble(INT_OR_FLOAT) | HsString(STRING) | HsChar(CHAR)
```

And this works fine!

#### Loose ends
At this point, there are four `atomic`s left in the entire parser: one in `<type-con>`, one in
`<pat-paren>`, one in `<pat-naked>`, and, finally, one in `<program>`. At this point it gets much
harder to remove them without altering the grammar substantially. Let's see how much more we can do
to remove them and explore some of the more dramatic changes it entails to the parser. We'll start
with the `atomic` in `<type-con>`.

The reason for this `atomic` is more obvious when we compare it with `<type-atom>`:

```scala mdoc:nest:silent
val `<type-con>` = ( `<con-id>`
                   | (ListConTy from "[]")
                   | atomic("(" ~> ((FunConTy from "->") | TupleConTy(countSome(","))) <~ ")")
                   )
lazy val `<type-atom>` = ( `<type-con>` | `<var-id>` | (UnitTy from "()")
                         | ListTy("[" ~> `<type>` <~ "]")
                         | ParenTyOrTupleTy("(" ~> sepBy1(`<type>`, ",") <~ ")")
                         )
```
```scala mdoc:invisible
val _ = `<type-atom>`: @unused
```

The `atomic` in `<type-con>` is used to backtrack out of the parentheses, since `ParenTyOrTupleTy`
will also consume them if we didn't see a `->` or a `,`. Thankfully, `<type-con>` is used in one
place in the parser, and we are looking at it! To fix this `atomic` we just need to be destructive
and stop following the grammar as rigidly as we have been. Let's inline `<type-con>` into `<type-atom>`
to start with:

```scala mdoc:nest:silent
lazy val `<type-atom>` = ( `<con-id>`
                         | (ListConTy from "[]")
                         | atomic("(" ~> ( (FunConTy from "->")
                                         | TupleConTy(countSome(","))
                                         ) <~ ")")
                         | `<var-id>` | (UnitTy from "()")
                         | ListTy("[" ~> `<type>` <~ "]")
                         | ParenTyOrTupleTy("(" ~> sepBy1(`<type>`, ",") <~ ")")
                         )
```
```scala mdoc:invisible
val _ = `<type-atom>`: @unused
```

Right, now that they have been put together, we can see the problem more clearly. Let's now
reorganise the parser so that the problematic parentheses appear next to each other: it's worth
mentioning that, for parsers without backtracking, we can always reorder the branches without
consequence; the restriction is that backtracking parsers cannot move ahead of their paired up branch.

```scala mdoc:nest:silent
lazy val `<type-atom>` = ( `<con-id>`
                         | (ListConTy from "[]")
                         | `<var-id>` | (UnitTy from "()")
                         | ListTy("[" ~> `<type>` <~ "]")
                         | atomic("(" ~> ( (FunConTy from "->")
                                         | TupleConTy(countSome(","))
                                         ) <~ ")")
                         | ParenTyOrTupleTy("(" ~> sepBy1(`<type>`, ",") <~ ")")
                         )
```
```scala mdoc:invisible
val _ = `<type-atom>`: @unused
```

This parser is a bit neater, and now we can apply our favourite tricks from part 1 to resolve this
`atomic`:

```scala mdoc:nest:silent
lazy val `<type-atom>` = ( `<con-id>`
                         | (ListConTy from "[]")
                         | `<var-id>` | (UnitTy from "()")
                         | ListTy("[" ~> `<type>` <~ "]")
                         | "(" ~> ( (FunConTy from "->")
                                  | TupleConTy(countSome(","))
                                  | ParenTyOrTupleTy(sepBy1(`<type>`, ","))
                                  ) <~ ")"
                         )
```
```scala mdoc:invisible
val _ = `<type-atom>`: @unused
```

Nice! One down, three to go. Let's have a look at the two involving patterns together:

```scala mdoc:nest:silent
lazy val `<pat-naked>`: Parsley[PatNaked] =
    ( `<var-id>` | atomic(`<pat-con>`)
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | NestedPatOrPatTuple("(" ~> sepBy1(`<pat>`, ",") <~ ")")
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )
lazy val `<pat-paren>` = atomic(`<pat-app>`) | `<pat-naked>`
lazy val `<pat-app>` = PatApp(`<pat-con>`, some(`<pat-naked>`))
lazy val `<pat-con>` = ( "(" ~> ((ConsCon from ":") | TupleCon(countSome(","))) <~ ")"
                       | `<con-id>`
                       )
```
```scala mdoc:invisible
val _ = `<pat-paren>`: @unused
```

I've omitted the `<pat>` rule here, since it's not relevant. Right, so the interaction of these rules
is quite intricate! The `atomic` in `<pat-paren>` is there because `<pat-app>` reads a `<pat-con>`
and `<pat-naked>` can also read one of those. Additionally, within `<pat-naked>`, the `atomic` is
guarding against yet more parentheses ambiguity caused by `ConsCon`, `TupleCon`, and
`NestedPatOrPatTuple`. Now, our first thought might be that we can eliminate the `atomic` within
`<pat-naked>` with the same strategy we used for `<type-con>`; we certainly could, but `<pat-con>`
is used in _two_ places, so doing so will cause duplication in the parser. This is a trade-off:
inlining `<pat-con>` will eliminate backtracking and make the parser more efficient, but that comes
at the cost of increased code size; in this case, in fact, the backtracking is limited to a single
character, which is relatively cheap, _and_ the size of the rule is small, so inlining it will not
increase the code size significantly. It doesn't really matter either way what we do, so let's
reinforce our factoring skills and duplicate the code to eliminate the `atomic`!

```scala mdoc:nest:silent
lazy val `<pat-naked>`: Parsley[PatNaked] =
    ( `<var-id>` | `<con-id>`
    | atomic("(" ~> ((ConsCon from ":") | TupleCon(countSome(","))) <~ ")")
    | NestedPatOrPatTuple("(" ~> sepBy1(`<pat>`, ",") <~ ")")
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )
lazy val `<pat-paren>` = atomic(`<pat-app>`) | `<pat-naked>`
lazy val `<pat-app>` = PatApp(`<pat-con>`, some(`<pat-naked>`))
lazy val `<pat-con>` = ( "(" ~> ((ConsCon from ":") | TupleCon(countSome(","))) <~ ")"
                       | `<con-id>`
                       )
```
```scala mdoc:invisible
val _ = `<pat-paren>`: @unused
```

In the above parser, I inlined the parser and reorganised it to bring the offending sub-rules together.
We know the drill by this point, let's factor that out:

```scala mdoc:nest:silent
lazy val `<pat-naked>`: Parsley[PatNaked] =
    ( `<var-id>` | `<con-id>`
    | "(" ~> ( (ConsCon from ":")
             | TupleCon(countSome(","))
             | NestedPatOrPatTuple(sepBy1(`<pat>`, ","))
             ) <~ ")"
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )
lazy val `<pat-paren>` = atomic(`<pat-app>`) | `<pat-naked>`
lazy val `<pat-app>` = PatApp(`<pat-con>`, some(`<pat-naked>`))
lazy val `<pat-con>` = ( "(" ~> ((ConsCon from ":") | TupleCon(countSome(","))) <~ ")"
                       | `<con-id>`
                       )
```
```scala mdoc:invisible
val _ = `<pat-paren>`: @unused
```

Nice, another `atomic` down! Now, what about the `atomic` in `<pat-paren>`? Well, it turns out
that, by eliminating the first `atomic`, we've stopped ourselves from being able to deal with this one!
The reason is that we've cannibalised the common `<pat-con>` structure into our factored parentheses
in `<pat-naked>`: oops! This `atomic` is actually worse than the other one, since it can backtrack
out of an entire `<pat-con>` as opposed to just a single `(`. So, could we undo what we've just done
and fix this one instead? We certainly could; but this transformation is very violent since
`<pat-naked>` appears in other places in the parser. That being said, lets do it!

The first step is to return to our old parser:

```scala mdoc:nest:silent
lazy val `<pat-naked>`: Parsley[PatNaked] =
    ( `<var-id>` | atomic(`<pat-con>`)
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | NestedPatOrPatTuple("(" ~> sepBy1(`<pat>`, ",") <~ ")")
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )
lazy val `<pat-paren>` = atomic(`<pat-app>`) | `<pat-naked>`
lazy val `<pat-app>` = PatApp(`<pat-con>`, some(`<pat-naked>`))
lazy val `<pat-con>` = ( "(" ~> ((ConsCon from ":") | TupleCon(countSome(","))) <~ ")"
                       | `<con-id>`
                       )
```
```scala mdoc:invisible
val _ = `<pat-paren>`: @unused
```

Now, we know that the `<pat-con>` is the problematic bit here, so let's break the `<pat-naked>` into
two rules:

```scala mdoc:nest:silent
lazy val `<pat-naked>` = atomic(`<pat-con>`) | `<pat-naked'>`
lazy val `<pat-naked'>`: Parsley[PatNaked] =
    ( `<var-id>`
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | NestedPatOrPatTuple("(" ~> sepBy1(`<pat>`, ",") <~ ")")
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )
lazy val `<pat-paren>` = atomic(`<pat-app>`) | atomic(`<pat-con>`) | `<pat-naked'>`
lazy val `<pat-app>` = PatApp(`<pat-con>`, some(`<pat-naked>`))
lazy val `<pat-con>` = ( "(" ~> ((ConsCon from ":") | TupleCon(countSome(","))) <~ ")"
                       | `<con-id>`
                       )
```
```scala mdoc:invisible
val _ = `<pat-paren>`: @unused
```

Now, notice that I've inlined `<pat-naked>` into `<pat-paren>`. The reason I did this is to make it
clear that the part we are trying to factor is the `<pat-con>`. In fact, let's do a little bit of
shuffling and move it into `<pat-app>`:

```scala mdoc:nest:silent
lazy val `<pat-paren>` = atomic(`<pat-app>`) | `<pat-naked'>`
lazy val `<pat-app>` = atomic(PatApp(`<pat-con>`, some(`<pat-naked>`))) | `<pat-con>`
```
```scala mdoc:invisible
val _ = `<pat-paren>`: @unused
```

Now, the aim here is to smash those `<pat-con>`s together! We can introduce a new disambiguator bridge to
handle this, and switch `some` for `many`:

```scala mdoc
object PatAppIfNonEmpty extends ParserBridge2[PatCon, List[PatNaked], PatParen] {
    def apply(con: PatCon, args: List[PatNaked]): PatParen = args match {
        case Nil => con
        case args => PatApp(con, args)
    }
}
```

Now, compared to the original `PatApp` bridge constructor, this one returns a `PatParen` instead of a
`PatApp`. This is because that is the common supertype of `PatApp` and `PatCon`. Let's see what the
parser looks like now:

```scala mdoc:nest:silent
lazy val `<pat-paren>` = atomic(`<pat-app>`) | `<pat-naked'>`
lazy val `<pat-app>` = PatAppIfNonEmpty(`<pat-con>`, many(`<pat-naked>`))
```
```scala mdoc:invisible
val _ = `<pat-paren>`: @unused
```

Now, since we've switched to a `many`, we can actually push both of our `atomic`s down into the
`<pat-con>` and leave it at that:

```scala mdoc:nest:silent
lazy val `<pat-naked>` = `<pat-con>` | `<pat-naked'>`
lazy val `<pat-naked'>`: Parsley[PatNaked] =
    ( `<var-id>`
    | (UnitCon from "()") | (NilCon from "[]") | `<literal>` | (Wild from "_")
    | NestedPatOrPatTuple("(" ~> sepBy1(`<pat>`, ",") <~ ")")
    | PatList("[" ~> sepBy(`<pat>`, ",") <~ "]")
    )
lazy val `<pat-paren>` = `<pat-app>` | `<pat-naked'>`
lazy val `<pat-app>` = PatAppIfNonEmpty(`<pat-con>`, many(`<pat-naked>`))
lazy val `<pat-con>` = ( atomic("(" ~> ((ConsCon from ":") | TupleCon(countSome(","))) <~ ")")
                       | `<con-id>`
                       )
```
```scala mdoc:invisible
val _ = `<pat-paren>`: @unused
```

So, can we remove that last `atomic`? No. At least not without a collosal amount of very destructive
refactoring of the grammar. What we can do, however, is make its scope ever so slightly smaller:

```scala mdoc:nest:silent
lazy val `<pat-con>` = ( atomic("(" ~> ((ConsCon from ":") | TupleCon(countSome(",")))) <~ ")"
                       | `<con-id>`
                       )
```
```scala mdoc:invisible
val _ = `<pat-con>`: @unused
```

All I've done there is move it one parser to the left, that way, we commit to the branch as soon as
we've seen either a `,` or a `:` and can't backtrack out of the closing bracket. That's as good as
we're going to get. A little unsatisfying, perhaps, but it's really such a minor point.

So, now what? Well, we have one final `atomic` we can look at. And it's trickier than it looks.

```scala mdoc:nest:silent
val `<declaration>` = Decl(`<var-id>`, "::" ~> `<type>`)

val `<clause>` =
    Clause(`<var-id>`, many(`<pat-naked>`), option(`<guard>`), "=" ~> `<expr>`)

val `<program>` =
    fully(sepEndBy(`<data>` | atomic(`<declaration>`) | `<clause>`, some(NEWLINE)))
```
```scala mdoc:invisible
val _ = `<program>`: @unused
```

I've skipped out the irrelevant `<data>` parser here. So, from the outset, this `atomic` doesn't
look so bad: both `<declaration>` and `<clause>` share a `<var-id>`. This, in theory, should be easy
to factor out. The problem is the bridges: when we factor out `<var-id>` we no longer have the right
shape to use their bridges. You might think that the solution is to introduce an `Either`, like we
did with the `INT_OR_FLOAT` lexeme. This would work out ok, but is a little clunky. The new bridge
factory would be dealing with `Either[Type, (List[PatNaked], Option[Expr], Expr)])]`. Let's start with
this approach first, and then see an alternative that keeps the two bridges separate and avoids the
tuple.

```scala mdoc:nest
// These make use of `ast.Clause` because they are defined outside of `ast` (in this .md file)
type PartialClause = (List[PatNaked], Option[Expr], Expr)
object DeclOrClause extends ParserBridge2[VarId, Either[Type, PartialClause], ProgramUnit] {
    def apply(id: VarId, declOrClause: Either[Type, PartialClause]): ProgramUnit =
        declOrClause match {
            case Left(ty) => Decl(id, ty)
            case Right((args, guard, body)) => ast.Clause(id, args, guard, body)
        }
}

object Clause extends ParserBridge2[VarId, PartialClause, Clause] {
    def apply(id: VarId, partialClause: PartialClause): Clause = {
        val (args, guard, body) = partialClause
        ast.Clause(id, args, guard, body)
    }
}
```

Now, to make this work nicely, I'm going to make use of the `<+>` combinator: pronounced "sum", this parser works like `|` except it returns its result into a co-product (`Either`). Using
this, we can define the factored `<program>`:

```scala mdoc:nest:silent
import parsley.syntax.zipped._

val `<declaration>` = "::" ~> `<type>`

val `<partial-clause>` = (many(`<pat-naked>`), option(`<guard>`), "=" ~> `<expr>`).zipped
val `<clause>` = Clause(`<var-id>`, `<partial-clause>`)

val `<decl-or-clause>` = DeclOrClause(`<var-id>`, `<declaration>` <+> `<partial-clause>`)
val `<program>` =
    fully(sepEndBy(`<data>` | `<decl-or-clause>`, some(NEWLINE)))
```
```scala mdoc:invisible
val _ = `<program>`: @unused
val _ = `<clause>`: @unused
```

Why have we got two `<clause>`s? Well, we also need a `<clause>` for the `let`-expressions further
down the parser. Now, there isn't anything wrong with this parser, and its a perfectly reasonable
approach.

Let's also take a look at the other way we could have done this. This time, we'll change
the two original bridge constructors, but won't introduce a third:

```scala mdoc:nest
// These make use of `ast.Clause` because they are defined outside of `ast` (in this .md file)
object Decl extends ParserBridge1[Type, VarId => ast.Decl] {
    def apply(ty: Type): VarId => ast.Decl = ast.Decl(_, ty)
}
object Clause extends ParserBridge3[List[PatNaked], Option[Expr], Expr, VarId => ast.Clause] {
    def apply(pats: List[PatNaked], guard: Option[Expr], rhs: Expr): VarId => ast.Clause =
        ast.Clause(_, pats, guard, rhs)
}
```

This time, the two bridge constructors return functions that take the factored `VarId`. How does this
change the parser?

```scala mdoc:nest:silent
val `<declaration>` = Decl("::" ~> `<type>`)

val `<partial-clause>` = Clause(many(`<pat-naked>`), option(`<guard>`), "=" ~> `<expr>`)
val `<clause>` = `<var-id>` <**> `<partial-clause>`

val `<decl-or-clause>` = `<var-id>` <**> (`<declaration>` | `<partial-clause>`)
val `<program>` =
    fully(sepEndBy(`<data>` | `<decl-or-clause>`, some(NEWLINE)))
```
```scala mdoc:invisible
val _ = `<program>`: @unused
val _ = `<clause>`: @unused
```

This also works completely fine. This time, we use `<**>` to apply the `<var-id>` to the partially
built AST nodes. The advantage of this style is that it's a little more concise, and involves less
unnecessary object construction. _However_, this version has a subtle flaw: suppose we wanted to
add position information to the AST, then where would the `pos` go? The position information would
be in the `var-id>`, and we'd have to extract it out in our bridges, for example:

```scala mdoc:nest:invisible
case class VarId(v: String)(val pos: (Int, Int))
case class Decl(id: VarId, ty: Type)(val pos: (Int, Int)) extends ProgramUnit

case class Clause(id: VarId, pats: List[PatNaked], guard: Option[Expr], rhs: Expr)(val pos: (Int, Int))
    extends ProgramUnit
```

```scala mdoc
object Decl extends ParserBridge1[Type, VarId => Decl] {
    def apply(ty: Type): VarId => Decl = v => Decl(v, ty)(v.pos)
}
object Clause extends ParserBridge3[List[PatNaked], Option[Expr], Expr, VarId => Clause] {
    def apply(pats: List[PatNaked], guard: Option[Expr], rhs: Expr): VarId => Clause =
        v => Clause(v, pats, guard, rhs)(v.pos)
}
```

This is really brittle: it is relying on the `VarId` type having _and exposing_ its position
information. In contrast, here's how our other bridge factory would be transformed:

```scala mdoc:invisible
trait ParserBridgePos2[A, B, C]
```
```scala mdoc
object DeclOrClause
    extends ParserBridgePos2[VarId, Either[Type, PartialClause], ProgramUnit] {
    def apply(id: VarId, declOrClause: Either[Type, PartialClause])
             (pos: (Int, Int)): ProgramUnit = declOrClause match {
        case Left(ty) => Decl(id, ty)(pos)
        case Right((args, guard, body)) => Clause(id, args, guard, body)(pos)
    }
}
```
```scala mdoc:invisible
val _ = DeclOrClause: @unused
```

Much more straightforward, with no effect on any other bridge! This is worth considering if you do
find yourself in this sort of position (no pun intended). That being said, the `<**>`-based version
is slightly cleaner and a _tiny_ bit more efficient. Regardless of which method we pick, however,
that pesky `atomic` is gone! That leaves us in a final state with a _single_ `atomic` which has a
maximum backtrack of one character. In other words, this parser runs in linear-time. Excellent!

## Concluding Thoughts
In this (rather long) page, we've explored the implementation of an entire parser for a subset of
Haskell from _scratch_. We've also seen a few techniques for factoring out common branches of the
parser and applied everything we've learnt so far to a real example. We are going to come back to
this parser later in the series: we've got to add better error messages, and deal with Haskell's
indentation-sensitive off-side rule!
