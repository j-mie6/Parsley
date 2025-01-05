{%
laika.versioned = true
laika.title = "`parsley.debug`"
parsley.tabname = "Basic Debug Combinators (parsley.debug)"
laika.site.metadata.description = "This page describes the built-in basic debugging combinators."
%}
# Basic Debug Combinators (`parsley.debug`)
Parsley has a collection of basic debugging utilities found within `parsley.debug`. These can help
debug errant parsers, understand how error messages have been generated, and provide a rough sense
of what parsers take the most time to execute.

The combinators themselves are all contained within `parsley.debug.DebugCombinators`.

@:callout(info)
*The Scaladoc for this page can be found at [`parsley.debug`](@:api(parsley.debug)) and [`parsley.debug.DebugCombinators`](@:api(parsley.debug.DebugCombinators))*
@:@

@:callout(info)
For a more comprehensive debugging system, check out the `parsley-debug` library, which has its own
section on the navigation bar.
@:@

## Debugging Problematic Parsers (`debug`)
The most common quick debugging combinator is `debug`, which at its simplest prints some information
on entering and exiting a combinator:

```scala mdoc:to-string
import parsley.Parsley.atomic
import parsley.character.string
import parsley.debug, debug._

val hello = ( atomic(string("hello").debug("hello")).debug("atomic1")
            | string("hey").debug("hey")
            | string("hi").debug("hi")
            )

debug.disableColorRendering()

hello.parse("hey")

hello.parse("hi")
```

In the above example, an unexpected failure to parse the input `"hi"` is being debugged using
`debug`. Each of the `string` combinators are annotated, as well as the `atomic`. This allows
us to see the control flow of the parser as it executes, as well as where the input was read up to.
In this case, we can see that `atomic` has undone input consumption, but that doesn't apply to `hey`.
In other words, there is another `atomic` missing! We could have added `debug` to the `|` combinator
as well to make it even clearer, of course. Though not visible above, the output is usually coloured.
If this causes problems, `debug.disableColorRendering()` will disable it, or the `colored` parameter
can be set to `false` on the combinator.

### Breakpoints
The `Breakpoint` type has values `EntryBreak`, `ExitBreak`, `FullBreak`, and `NoBreak`, which is the
default. `FullBreak` has the effect of both `EntryBreak` and `ExitBreak` combined: `EntryBreak` will
pause execution on the entry to the combinator, requiring input on the console to proceed, and `ExitBreak`
will do the same during the exit.

### Watching References
The `debug` combinator takes a variadic number of reference/name pairs as its last argument. These
allow you to watch the values stored in references as well during the debugging process. For instance:

```scala mdoc:to-string
import parsley.Parsley.atomic
import parsley.state._
import parsley.character.string
import parsley.debug._

val p = 0.makeRef { r1 =>
    false.makeRef { r2 =>
        val p = (  string("hello")
                ~> r1.update(_ + 5)
                ~> ( string("!")
                   | r2.set(true) ~> string("?")
                   ).debug("punctuation", r2 -> "r2")
                )
        r1.rollback(atomic(p).debug("hello!", r1 -> "r1", r2 -> "r2"))
          .debug("rollback", r1 -> "r1")
    }
}

p.parse("hello world")
```

## Debugging Error Messages (`debugError`)
The `debugError` is a slightly more experimental combinator that aims to provide some (lower-level)
insight into how an error message came to be. For instance:

```scala mdoc:to-string
import parsley.character.{letter, digit, char}
import parsley.Parsley.many
import parsley.debug._

val q = (many( ( digit.debugError("digit")
               | letter.debugError("letter")
               ).debugError("letter or digit")
             ).debugError("many letterOrDigit")
      ~> many(char('@').debugError("@")).debugError("many @")
      ~> char('#').debugError("#")) | char('!').debugError("!")

q.parse("$")
```

In the above example, you can see how each individual error is raised, as well as evidence of merging,
and how errors can be turned into "hints" if the error is successfully recovered from: this means
that the label may be re-incorporated into the error again later if they are at the valid offset,
as seen in the errors for `char('#')`.

## Profiling Parser (`Profiler`)
The `Profiler` class, and the accompanying `profile` combinator, provide a *rough* guideline of how
much of the runtime a parser might be taking up. The execution of each combinator is measured with
a resolution of 100ns. First, a `Profiler` object must be set up and implicitly available in scope:
its role is to collect the profiling samples. Then, a parser annotated with `profile` combinators
is ran, and the results can be displayed with `profiler.summary()`.

@:callout(warning)
There is a disclaimer that the profiler "just provides data", no guarantee about its statistical
significance is given. Multiple runs can be performed and these will be aggregated, the `profiler`
can be cleared using `clear()`.
@:@

```scala mdoc:height=0
import parsley.Parsley, Parsley.pure
import parsley.character.{string, char}
import parsley.combinator.traverse
import parsley.debug._

def classicString(s: String): Parsley[String] = s.toList match {
    case Nil => pure("")
    case c :: cs => traverse(c, cs: _*)(char).map(_.mkString)
}

implicit val profiler: Profiler = new Profiler
val strings = many(classicString("...").profile("classic string")
               <~> string("!!!").profile("optimised string"))
val stringsVoid = many(classicString("...").profile("voided classic string")
                   <~> string("!!!").profile("voided optimised string")).void

strings.parse("...!!!" * 10000)
stringsVoid.parse("...!!!" * 10000)

profiler.summary()
```

The above example shows that the `string` combinator is much faster than the "classic" definition
in terms of `traverse` and `char` (not even accounting for its improved error messages!). However,
it also shows that when the results are not required (as indicated by the `void` combinator, which
_aggressively_ suppresses result generation underneath it), the combinators perform much more similarly.
You will, however, notice variance depending on when you visit this page: these results are generated
each publish, and sometimes the non-voided `string` can outperform the voided one by pure chance!

The `profile` combinator will account for other profiled combinators underneath it, accounting for
their "self time" only. This helps to measure the impact of a specific sub-parser more accurately.
