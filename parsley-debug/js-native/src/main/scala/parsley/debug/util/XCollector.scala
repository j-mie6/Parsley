/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.util

// Sadly, no reflective capabilities exist in Scala.JS or Scala Native, so these
// methods don't do anything yet.
// TODO: Find a substitute for reflection for JS and Native.
private [parsley] object XCollector extends XDummyCollector
