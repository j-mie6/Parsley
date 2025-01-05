/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token

import parsley.ParsleyTest

import descriptions._

class DescriptionRequireTests extends ParsleyTest {
    "SymbolDesc" should "not allow an intersection between operators and keywords" in {
        an [IllegalArgumentException] should be thrownBy SymbolDesc.plain.copy(hardKeywords = Set("a"), hardOperators = Set("a"))
    }

    "ExponentDesc.Supported" should "not allow for empty exponents" in {
        an [IllegalArgumentException] should be thrownBy ExponentDesc.Supported(false, Set.empty, 10, PlusSignPresence.Illegal, true)
        an [IllegalArgumentException] should be thrownBy ExponentDesc.Supported(false, Set.empty, 10, PlusSignPresence.Optional, true)
    }

    "NumericDesc" should "not allow for multiple prefixless descriptions" in {
        an [IllegalArgumentException] should be thrownBy NumericDesc.plain.copy(
            integerNumbersCanBeHexadecimal = true,
            integerNumbersCanBeOctal = true,
            hexadecimalLeads = Set.empty,
            octalLeads = Set.empty
        )

        an [IllegalArgumentException] should be thrownBy NumericDesc.plain.copy(
            integerNumbersCanBeHexadecimal = true,
            integerNumbersCanBeBinary = true,
            hexadecimalLeads = Set.empty,
            binaryLeads = Set.empty
        )

        an [IllegalArgumentException] should be thrownBy NumericDesc.plain.copy(
            realNumbersCanBeHexadecimal = true,
            realNumbersCanBeOctal = true,
            hexadecimalLeads = Set.empty,
            octalLeads = Set.empty
        )

        an [IllegalArgumentException] should be thrownBy NumericDesc.plain.copy(
            realNumbersCanBeHexadecimal = true,
            realNumbersCanBeBinary = true,
            hexadecimalLeads = Set.empty,
            binaryLeads = Set.empty
        )
    }

    it should "not rule out valid configurations" in {
        noException should be thrownBy NumericDesc.plain.copy(
            integerNumbersCanBeHexadecimal = true,
            integerNumbersCanBeOctal = false,
            realNumbersCanBeOctal = true,
            hexadecimalLeads = Set.empty,
            octalLeads = Set.empty,
            leadingZerosAllowed = false,
        )
    }

    it should "not allow for leading zeros if there is a prefixless description" in {
        an [IllegalArgumentException] should be thrownBy NumericDesc.plain.copy(
            integerNumbersCanBeHexadecimal = true,
            hexadecimalLeads = Set.empty,
            leadingZerosAllowed = true,
        )
    }

    "NumberOfDigits.AtMost" should "not allow for 0 or negative numbers of digits" in {
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.AtMost(0)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.AtMost(-1)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.AtMost(-10)
    }

    "NumberOfDigits.Exactly" should "not allow for 0 or negative numebrs of digits" in {
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.Exactly(0)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.Exactly(-1)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.Exactly(-10)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.Exactly(1, 0)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.Exactly(1, -1)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.Exactly(1, -10)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.Exactly(1, 2, 0)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.Exactly(1, 2, -1)
        an [IllegalArgumentException] should be thrownBy NumberOfDigits.Exactly(1, 2, -10)
    }

    "EscapeDesc" should "not allow for empty string escapes" in {
        an [IllegalArgumentException] should be thrownBy EscapeDesc.plain.copy(mapping = Map(("", 0)))
    }

    it should "not permit ambiguity with the different mappings" in {
        an [IllegalArgumentException] should be thrownBy EscapeDesc.plain.copy(
            literals = Set('a'),
            mapping = Map(("a", 0))
        )
    }

    it should "not allow for constant escapes that aren't valid characters" in {
        noException should be thrownBy EscapeDesc.plain.copy(
            mapping = Map(("a", 0x10ffff))
        )
        an [IllegalArgumentException] should be thrownBy EscapeDesc.plain.copy(
            mapping = Map(("a", 0x10ffff+1))
        )
        an [IllegalArgumentException] should be thrownBy EscapeDesc.plain.copy(
            mapping = Map(("a", -10))
        )
    }

    "TextDesc" should "not allow for string literals without ends" in {
        an [IllegalArgumentException] should be thrownBy TextDesc.plain.copy(
            stringEnds = Set(("", "'")),
        )
        an [IllegalArgumentException] should be thrownBy TextDesc.plain.copy(
            multiStringEnds = Set(("", "'")),
        )
    }
}
