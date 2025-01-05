## Why are the definitions in this package *weird*?
One of key requirements of the lexical descriptions is the ergonomics of creating them.
Compare the ease-of-use for `descriptions` vs `errors` (in `4.x`, at least), which uses an
abstract class that needs anonymous instantiation.

The best way to do this is obviously `case class`. The problem is that case classes cannot be
easily extended in future. If I wanted to add a field to a description, this would break pattern
matching and the binary compatibility would be broken. In the past, new major versions have taken
the opportunity to simplify or adjust existing fields. In future, however, I want the ability to
at least *add* configuration, even if I cannot change or remove without breaking bin compat.

To do this, we need to remove most of the case class machinery and re-implement it by hand. We don't
care about pattern matching, equality, or hashing, I just want a `copy` and an `apply`. This involves
the horrid duplication you see within, and makes documenting a bit of a nightmare, but at least now
I can **add** new bits of configuration.

One downside, much like our usual tricks of evolution, is that it is not *TASTy* compatible, but oh
well, nobody is probably writing any parsley macros that aren't able to step-evolve with us.

The far less ergonomic alternative is the style used for errors in (4.x). The advantage of this is
that it also allows for changes as well as removals, but the ergonomic cost is just too great for
something as core as the lexical desc. Besides, I don't expect to want to remove or change much from
this, and there are horrible ways in which I *could* work around it if I absolutely needed to.
