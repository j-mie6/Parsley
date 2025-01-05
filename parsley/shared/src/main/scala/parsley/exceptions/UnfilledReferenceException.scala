/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.exceptions

// $COVERAGE-OFF$
private [parsley] class UnfilledReferenceException
    extends ParsleyException("A parser uses a reference that has not been initialised by a `set`")
// $COVERAGE-ON$
