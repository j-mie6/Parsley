{%
laika.versioned = true
laika.site.metadata.description = "How attachment works for debug views."
%}

# Attaching Debugger Views
The new functionality in `parsley-debug` involves attaching a debugger to a parser
that will be ran. There are two combinators to do this found in `parsley.debug.combinator`:
`attach`, and `attachReusable`. Before we see these, it will be useful to understand how
`DebugView`s work.

@:callout(info)
By importing `parsley.debug.combinator.DebuggerOps`, you will have access to the
method-style implementation of these combinators.
@:@

## `DebugView`
The `DebugView.SingleUse` and `DebugView.Reusable` traits represent renderers for debug information.
By default, `parsley-debug` includes `PrintView`, which can be used to "render" debug information
out onto the console, or perhaps to a file (or any `PrintStream`/`OutputStream`). The reusability
aspect refers to whether or not multiple such views can work simultaneously: see `attachReusable`
below.

The views themselves do not necessarily have to directly render the debugging content themselves,
they could be remote connections, for instance; but the point is that they are what is attached
to a specific parser. When the parser is ran, the debug combinator collects the information, and
the view processes that information in some way. This process is done via *attachment*.

## `attach` and `attachReusable`
The `attach` combinator takes a `DebugView` (reusable or otherwise) and optionally a "string rules"
function, which determines whether or not a value is eagerly turned into a string or preserved in
its original form within the internal "`DebugTree`". In return, you get a new parser, which will
feed debugging information to the provided `DebugView`.

@:callout(warning)
You should only use the parser returned by `attach` in one place! If you want to debug a parser
that appears in more than one place, you must use `attachReusable`.
@:@

In contrast, `attachReusable` is similar, but takes a *by-name* `DebugView`, which may be recreated
each use, and returns a function that produces new `Parsley` values. The idea is that if you need
to use the parser in more than one place, you call the function more than once to produce independently
bound parsers.
