/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.descriptions

/** This class describes the aggregation of a bunch of different
  * sub-configurations for lexing a specific language.
  *
  * @note Documentation for the parameters is found in the `copy` or companion `apply`.
  *
  * @param nameDesc the description of name-like lexemes
  * @param symbolDesc the description of specific symbolic lexemes
  * @param numericDesc the description of numeric literals
  * @param textDesc the description of text literals
  * @param spaceDesc the description of whitespace
  * @since 4.0.0
  */
final class LexicalDesc private (val nameDesc: NameDesc,
                                 val symbolDesc: SymbolDesc,
                                 val numericDesc: NumericDesc,
                                 val textDesc: TextDesc,
                                 val spaceDesc: SpaceDesc) { // TODO: we could switch to the horrid _ form to allow docs
    /** @param nameDesc the description of name-like lexemes
      * @param symbolDesc the description of specific symbolic lexemes
      * @param numericDesc the description of numeric literals
      * @param textDesc the description of text literals
      * @param spaceDesc the description of whitespace
      */
    def copy(nameDesc: NameDesc = this.nameDesc,
             symbolDesc: SymbolDesc = this.symbolDesc,
             numericDesc: NumericDesc = this.numericDesc,
             textDesc: TextDesc = this.textDesc,
             spaceDesc: SpaceDesc = this.spaceDesc): LexicalDesc =
        new LexicalDesc(nameDesc, symbolDesc, numericDesc, textDesc, spaceDesc)
}

/** This object contains any preconfigured lexical definitions and
  * a way of constructing a complete description.
  * 
  * @since 4.0.0
  */
object LexicalDesc {
    /** @param nameDesc the description of name-like lexemes
      * @param symbolDesc the description of specific symbolic lexemes
      * @param numericDesc the description of numeric literals
      * @param textDesc the description of text literals
      * @param spaceDesc the description of whitespace
      */
    def apply(nameDesc: NameDesc, symbolDesc: SymbolDesc, numericDesc: NumericDesc, textDesc: TextDesc, spaceDesc: SpaceDesc): LexicalDesc =
        new LexicalDesc(nameDesc, symbolDesc, numericDesc, textDesc, spaceDesc)

    /** Defaults to the plain definitions of each sub-description.
      * @since 4.0.0
      */
    val plain: LexicalDesc = LexicalDesc(NameDesc.plain, SymbolDesc.plain, NumericDesc.plain, TextDesc.plain, SpaceDesc.plain)
}
