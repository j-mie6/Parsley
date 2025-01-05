/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.exceptions

// $COVERAGE-OFF$
private [parsley] class CorruptedReferenceException
    extends ParsleyException("A reference has been used across two different parsers in separate calls to parse, causing it to clash with another reference")
// $COVERAGE-ON$
