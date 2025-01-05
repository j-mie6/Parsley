/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug

/** Base trait for breakpoints.
     *
     * @group break
     */
sealed trait Breakpoint
/** Indicates that no breaking should occur.
     *
     * @group break
     */
case object NoBreak extends Breakpoint
/** Break on entry to the combinator, require user input to advance.
     *
     * @group break
     */
case object EntryBreak extends Breakpoint
/** Break on exit to the combinator, require user input to advance.
     *
     * @group break
     */
case object ExitBreak extends Breakpoint
/** Break on both entry and exit to the combinator, require user input to advance in both cases.
     *
     * @group break
     */
case object FullBreak extends Breakpoint
