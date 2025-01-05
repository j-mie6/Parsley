/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token

/** This package contains the descriptions of various lexical structures to be fed to `Lexer`.
  * @since 4.0.0
  */
package object descriptions

/** This package contains the abstract parsers for parsing numeric literals, like integers and reals.
  * @since 4.0.0
  */
package object numeric

/** This package contains the abstract parsers for parsing identifiers and operators.
  * @since 4.0.0
  */
package object names

/** This package contains the abstract parsers for parsing string and character literals.
  * @since 4.0.0
  */
package object text

/** This package contains the abstract parsers for parsing symbolic tokens like keywords.
  * @since 4.0.0
  */
package object symbol

/** This module contains functionality to describe character predicates, which can
  * be used to determine what characters are valid for different tokens.
  *
  * @since 4.0.0
  */
package object predicate
