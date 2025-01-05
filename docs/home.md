{%
helium.site.pageNavigation.enabled = false
laika.site.metadata.description = "The parsley wiki."
%}
# Home

Welcome to the `parsley` wiki!

## Structure
The Wiki is structured into a few broad sections:

@:navigationTree {
  entries = [ { target = "/", excludeRoot = true, depth = 1 } ]
}

The two most important sections here are [Understanding the API](api-guide/README.md)
and [Parser Combinator Tutorial](tutorial/README.md). The former is a structured look at how
to use various parts of the API, with examples and patterns of use; and the latter is a more
ground-up guide on how to use parser combinators, and the patterns that describe best-practice.
The [Cheatsheet](cheatsheet.md) may help get a quick overview on the key combinators to be
aware of when using `parsley`. The [FAQ](faq.md) outlines some common problems you might
encounter, how to interpret them, and how to resolve them.

It is possible to download this Wiki in PDF form, though this is not guaranteed to be formatted
nicely!

## Contributing
Users are welcome to contribute to this wiki themselves. Unlike GitHub's wiki, the source
can be found at <https://github.com/j-mie6/parsley/tree/staging/5.0/docs>, in Markdown files
augmented using the [Laika](https://typelevel.org/Laika/) framework. This allows for some
extra non-standard directives to be used. Have a look at other wiki pages to get a sense for
how things should work. Feel free to then make a PR with your changes 🙂.
