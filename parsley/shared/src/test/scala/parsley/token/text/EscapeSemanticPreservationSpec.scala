/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.text

import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import parsley.Parsley.eof
import parsley.token.descriptions._
import parsley.token.errors.ErrorConfig
import org.scalacheck.Gen

import parsley.token.descriptions.{DescGen, DescShrink}
import DescGen._
import DescShrink._

class EscapeSemanticPreservationSpec extends AnyPropSpec with ScalaCheckPropertyChecks with should.Matchers {
    implicit val config: PropertyCheckConfiguration = new PropertyCheckConfiguration(minSuccessful = 50)
    val errConfig = new ErrorConfig
    val generic = new parsley.token.numeric.Generic(errConfig)
    def makeOptEscape(escDesc: EscapeDesc) = new Escape(escDesc, errConfig, generic)
    def makeUnoptEscape(escDesc: EscapeDesc) = new OriginalEscape(escDesc, errConfig, generic)

    val escInputGen = Gen.frequency(
        4 -> Gen.alphaNumStr,
        1 -> Gen.numStr,
        1 -> Gen.hexStr.map(s => s"x$s"),
        1 -> Gen.stringOf(Gen.choose('0', '7')).map(s => s"o$s"),
        1 -> Gen.stringOf(Gen.oneOf('0', '1')).map(s => s"b$s"),
    ).map(s => s"\\$s")

    property("reading escape characters should not vary based on optimisations") {
        forAll(escDescGen -> "escDesc", escInputGen -> "input") { (escDesc, input) =>
            val optEscape = makeOptEscape(escDesc)
            val unoptEscape = makeUnoptEscape(escDesc)
            optEscape.escapeChar.parse(input) shouldBe unoptEscape.escapeChar.parse(input)
            (optEscape.escapeChar <* eof).parse(input) shouldBe (unoptEscape.escapeChar <* eof).parse(input)
        }

        forAll(escDescGen -> "escDesc", Gen.asciiPrintableStr -> "input") { (escDesc, input) =>
            val optEscape = makeOptEscape(escDesc)
            val unoptEscape = makeUnoptEscape(escDesc)
            optEscape.escapeChar.parse(input) shouldBe unoptEscape.escapeChar.parse(input)
        }
    }
}
