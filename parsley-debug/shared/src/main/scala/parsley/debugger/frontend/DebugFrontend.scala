/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debugger.frontend

import parsley.debugger.DebugTree

/** A common interface for a debug frontend for a debugger to present the debug tree. Inherit from
  * one of the two provided subtraits to use.
  *
  * Any compliant implementation that handles all nodes of a [[parsley.debugger.DebugTree]] can be
  * used in place of any other implementation (e.g. a serialiser to JSON, a GUI, etc.).
  *
  * If a frontend is reusable, one can implement it as either an `object` or a `class`, but an `object`
  * is recommended. Either way, it should inherit [[ReusableFrontend]].
  *
  * If a frontend is single-use (e.g. it has some non-reusable state), never implement it as an `object`. Always
  * implement single-use frontends as a `class` of some sort inheriting from [[SingleUseFrontend]].
  *
  * If the results of some processing of a tree are needed out of a frontend, create a frontend class that accepts
  * some continuation parameter of `ReturnType => Unit` and call it somewhere within the implementation.
  *
  * @since 4.5.0
  */
sealed trait DebugFrontend {
    // Is this frontend stateful (and should only be able to run once)?
    protected [frontend] val reusable: Boolean

    // Tracks if this frontend has run already.
    private var hasRun: Boolean = false

    // FIXME: There is probably a better way of making frontends that return than having to use continuations.
    //        One contender is to just allow the frontend to return its result and discard it for automatic executions,
    //        while making overloads that return the return value in a pair, where the original parser's return result
    //        is converted into an Option[_]. The other contender is just to ignore the result entirely and leave returns
    //        of processing results to manual invocations of a frontend.

    /** Process a debug tree using whatever the frontend is doing to present the tree in some way.
      *
      * @param input The full input of the parse.
      * @param tree  Debug tree to process.
      */
    final def process(input: => String, tree: => DebugTree): Unit =
        if (!(reusable && hasRun)) {
            hasRun = true
            processImpl(input, tree)
        } else {
            // XXX: There isn't really another way to enforce not running a stateful frontend more than once that isn't just "do nothing".
            //      Especially since doing nothing turns that action into a silent error, which is generally less preferable to "loud"
            //      errors. Failing fast may be better for some frontends.
            throw new XIllegalStateException("Stateful frontend has already been run.").except // scalastyle:ignore throw
        }

    /** The actual method that does the processing of the tree.
      * Override this to process a tree in a custom way.
      */
    protected def processImpl(input: => String, tree: => DebugTree): Unit
}

/** Signifies that the frontend inheriting from this can be used multiple times.
  *
  * @see [[DebugFrontend]]
  * @since 4.5.0
  */
trait ReusableFrontend extends DebugFrontend {
    override protected [frontend] final val reusable: Boolean = false
}

/** Signifies that the frontend inheriting from this can only be run once.
  *
  * @see [[DebugFrontend]]
  * @since 4.5.0
  */
trait SingleUseFrontend extends DebugFrontend {
    override protected [frontend] final val reusable: Boolean = true
}
