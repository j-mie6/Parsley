/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.symbol

import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import parsley.token.LexemeImpl._

import parsley.Parsley
import parsley.token.descriptions._
import parsley.token.errors.ErrorConfig
import parsley.character.spaces
import org.scalacheck.Gen
import org.scalacheck.Arbitrary

import parsley.token.descriptions.DescGen
import DescGen._
//import DescShrink._

class SymbolSemanticPreservationSpec extends AnyPropSpec with ScalaCheckPropertyChecks with should.Matchers {
    implicit val config: PropertyCheckConfiguration = new PropertyCheckConfiguration(minSuccessful = 50)
    val errConfig = new ErrorConfig
    def makeOptSymbol(nameDesc: NameDesc, symDesc: SymbolDesc): Symbol = new LexemeSymbol(new ConcreteSymbol(nameDesc, symDesc, errConfig), spaces)
    def makeUnoptSymbol(nameDesc: NameDesc, symDesc: SymbolDesc): Symbol = new LexemeSymbol(new OriginalSymbol(nameDesc, symDesc, errConfig), spaces)

    def optAndUnOptAreSame(f: (Symbol, String) => Parsley[Unit])(nameDesc: NameDesc, symbolDesc: SymbolDesc, sym: String, input: String) =
        whenever (!input.contains('\u0000') && !sym.contains('\u0000')) {
            val opt = makeOptSymbol(nameDesc, symbolDesc)
            val unopt = makeUnoptSymbol(nameDesc, symbolDesc)
            f(opt, sym).parse(input) shouldBe f(unopt, sym).parse(input)
        }

    property("reading keywords should not vary based on optimisations") {
        forAll(nameDescGen -> "name description", symbolDescGen -> "symbol description",
               keywordGen -> "keyword", Arbitrary.arbitrary[String] -> "input")(optAndUnOptAreSame(_.softKeyword(_)))

        forAll(nameDescGen -> "name description", symbolDescGen -> "symbol description",
               keywordGen -> "keyword", keywordGen -> "input")(optAndUnOptAreSame(_.softKeyword(_)))

        forAll(nameDescGen -> "name description", symbolDescGen -> "symbol description",
               keywordGen -> "keyword", Gen.alphaNumChar -> "letter") { (nameDesc, symbolDesc, key, letter) =>
            optAndUnOptAreSame(_.softKeyword(_))(nameDesc, symbolDesc, key, s"$key$letter")
        }
    }

    property("reading operators should not vary based on optimisations") {
        forAll(nameDescGen -> "name description", symbolDescGen -> "symbol description",
               operatorGen -> "operator", Arbitrary.arbitrary[String] -> "input")(optAndUnOptAreSame(_.softOperator(_)))

        forAll(nameDescGen -> "name description", symbolDescGen -> "symbol description",
               operatorGen -> "operator", operatorGen -> "input")(optAndUnOptAreSame(_.softOperator(_)))
    }
}
