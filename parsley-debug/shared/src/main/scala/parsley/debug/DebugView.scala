/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug

import parsley.debug.internal.XIllegalStateException

/** A common interface for a rendering view for a debugger to present the debug tree. Inherit from
  * one of the two provided subtraits to use.
  *
  * Any compliant implementation that handles all nodes of a `parsley.debugger.DebugTree` can be
  * used in place of any other implementation (e.g. a serialiser to JSON, a GUI, etc.).
  *
  * If a view is reusable, one can implement it as either an `object` or a `class`, but an `object`
  * is recommended. Either way, it should inherit [[DebugView.Reusable]].
  *
  * If a view is single-use (e.g. it has some non-reusable state), never implement it as an `object`. Always
  * implement single-use views as a `class` of some sort inheriting from [[DebugView.SingleUse]].
  *
  * @since 5.0.0
  * @group debugview
  */
sealed trait DebugView {
    /** Render a debug tree.
      *
      * @param input The full input of the parse.
      * @param tree  Debug tree to render.
      */
    private [debug] def render(input: =>String, tree: =>DebugTree): Unit
}
/** @group debugview */
object DebugView {
    /** Signifies that the debug view inheriting from this can be used multiple times.
      *
      * @see [[DebugView]]
      * @since 5.0.0
      */
    trait Reusable extends DebugView

    /** Signifies that the debug view inheriting from this can only be run once.
      *
      * @see [[DebugView]]
      * @since 4.5.0
      */
    trait SingleUse extends DebugView {
        private var hasBeenRun = false
        final override private [debug] def render(input: =>String, tree: =>DebugTree): Unit = {
            if (hasBeenRun) {
                // XXX: There isn't really another way to enforce not running a stateful frontend more than once that isn't just "do nothing".
                //      Especially since doing nothing turns that action into a silent error, which is generally less preferable to "loud"
                //      errors. Failing fast may be better for some frontends.
                throw new XIllegalStateException("Stateful frontend has already been run.").except // scalastyle:ignore throw
            } else {
                renderImpl(input, tree)
                hasBeenRun = true
            }
        }
        /** The implementation of the render method above */
        private [debug] def renderImpl(input: =>String, tree: =>DebugTree): Unit
    }
}
