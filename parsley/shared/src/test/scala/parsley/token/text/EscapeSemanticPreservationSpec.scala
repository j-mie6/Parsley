package parsley.token.text

import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import parsley.combinator.eof
import parsley.token.descriptions.text._
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

    val escInputGen = Gen.alphaNumStr.map(s => s"\\$s")

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
