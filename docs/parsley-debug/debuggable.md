{%
laika.title = "`@parsley.debuggable`"
laika.versioned = true
laika.site.metadata.description = "The debuggable annotation."
%}

# Debugging Annotation `@parsley.debuggable`
Compared with the [basic debugging][Debugging Problematic Parsers (`debug`)] combinators
found in *vanilla* `parsley`, the `parsley-debug` library is designed to work as a more complete
system. The key part of this system from the `parsley`-end is the `@debuggable` annotation, which
is used to collect up the names of your parsers so that they appear in the debugger views in the
form you defined them in.

@:callout(warning)
This annotation relies on Scala's *macro* system. This means that its implementation is dependent
on the version of Scala being used. Scala 3's implementation is far more stable, but requires the
`@experimental` annotation to use (or the `-experimental` compiler flag) until
macro annotations are standard.

For Scala 2, there are some caveats to the annotation dicussed below. For Scala 2.13, you must
enable the `-Ymacro-annotation` flag, and for Scala 2.12 you must use the `org.scalamacros:paradise`
compiler plugin.
@:@

## How to use
Using `@debuggable` is very easy! Assuming your parsers are kept in either one or more `class`es or `object`s,
just simply annotate them with the annotation, and you're good to go.

```scala
import parsley.quick.*
import parsley.debuggable

@debuggable // (or @experimental @debuggable for Scala 3)
object parsers {
    val p = char('a')
    val q = p ~> digit
    val r = p <~> q

    // there can be more in here, including a `def main` method.
}
```

What you will find, as a result, is that if you use the `parsley-debug` debugging functionality,
the names `p`, `q`, and `r` will automatically appear in the outputs.

## Scala 2 Woes
The way this macro works is by demanding the values within the annotated `object` or `class` that
have type `Parsley[?]` and adding all of them to an internal map accessible via the
`parsley.debug.util.Collector` API. In Scala 3, the tree provided to the annotation macro is
already typechecked, so this query is effortless and works very robustly. In Scala 2, the tree
provided is not typechecked; this means that the annotation has to send the AST to the typer within
the compiler to obtain the information it needs.

The problem is that, because of the various transformations that have happened to the AST already,
the typer often crashes! There are a few different things that I've observed can cause this (it's
certainly not exhaustive):

* Anonymous class instances being typechecked will crash.
* The use of overloaded methods with the annotated object doesn't necessarily work correctly.
* You must not shadow the enclosing class/object's name as one of its values.

The annotation can mostly check for these things for you. However, if it complains, it is up to you
to fix it! This is easy: just add explicit type signatures. When you do this, `debuggable` will
behind the scenes remove the body of the definition (replacing it with `???`), removing scope for
screwing with the typer (which is unnecessary, because we already have the type).

Obviously, this is really not ideal, and when things do go wrong it can be quite spectatular. This
is why I **absolutely 100% recommend using *Scala 3***.
