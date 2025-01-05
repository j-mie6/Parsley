{%
laika.versioned = true
laika.title = "`PrintView`"
laika.site.metadata.description = "The built-in view for plain console debugging"
%}

# `PrintView` (`parsley-debug`)
The core `parsley-debug` library contains one view, namely `PrintView`. This can be
used in place of more advanced functionality present in the other "companion" libraries.
It is perhaps less featureful than the "vanilla" `.debug` combinator, which supports breakpoints,
but does work cross platform and across all Scala versions. The advantage over `.debug`, of course,
is that for large-scale debugging, `@debuggable` is far more ergonomic to apply to a whole parser,
and gives more intermediate information. With no internal state, this view is reusable, however,
concurrent executions within a parser (if used with `attachReusable`) may interleave and be confusing.

@:callout(info)
Please see the [`@parsley.debuggable`](../debuggable.md) page first, as this is used
to provide meaningful names for this functionality.
@:@

## Configuration
The `PrintView` configuration is very simple. By default, the `PrintView` object will print the
debug trace directly to `Console.out`. It is possible to use the `PrintView.apply` methods to
produce new views that print their output elsewhere: for instance to a file.

## What does it look like?
As an example, here is the trace without `@debuggable`:

```scala mdoc:to-string
import parsley.quick.*
import parsley.debug.combinator.*
import parsley.debug.PrintView

val hello = ( atomic(string("hello"))
            | (string("hey")
             | string("hi"))
            )

hello.attach(PrintView).parse("hey")

hello.attach(PrintView).parse("hi")
```

As you can see, without an `@debuggable` annotation, this just prints the names of the combinators
used to construct the parser, which is ok here, but would be undecipherable for more complex parsers.
The rendering style is slightly different to the vanilla combinator as well, rendering in a tree
shape as opposed to using explicit entry/exit markers.
