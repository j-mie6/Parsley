/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.internal

// Cross platform wrapper around UnsupportedOperationException.
private [parsley] class XUnsupportedOperationException(msg: String) {
    def except: Throwable = new UnsupportedOperationException(msg)
}

// Cross platform wrapper around IllegalStateException.
private [parsley] class XIllegalStateException(msg: String) {
    def except: Throwable = new IllegalStateException(msg)
}
