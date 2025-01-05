/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import scala.annotation.{experimental, MacroAnnotation}
import scala.quoted.*

/** This annotation can be applied to an object or class to record their
  * names for the debugging/diagnostic combinators.
  *
  * @note Currently, macro-annotations in Scala 3 are experimental, which
  * means the @experimental annotation will need to be used (or the global
  * -experimental flag on 3.4+) to use this functionality.
  *
  * @since 5.0.0
  */
@experimental class debuggable extends MacroAnnotation {
    def transform(using Quotes)(tree: quotes.reflect.Definition): List[quotes.reflect.Definition] = {
        import quotes.reflect.*
        tree match {
            case cls@ClassDef(clsName, constr, parents, selfOpt, body) =>
                val parsleyTy = TypeRepr.of[Parsley[?]].typeSymbol
                val fields = cls.symbol.fieldMembers.view.map(_.termRef)
                val parsers = fields.filter(_.typeSymbol == parsleyTy)

                // the idea is we inject a call to Collector.registerNames with a constructed
                // map from these identifiers to their compile-time names
                val listOfParsers = Expr.ofList {
                    parsers.map(tr => Expr.ofTuple((Ident(tr).asExprOf[parsley.Parsley[?]], Expr(tr.name)))).toList
                }
                val registration = '{parsley.debug.util.Collector.registerNames($listOfParsers.toMap)}.asTerm

                // add the registration as the last statement in the object
                // TODO: in future, we want to modify all `def`s with a top level `opaque` combinator
                // that will require a bit more modification of the overall `body`, unfortunately
                List(ClassDef.copy(tree)(clsName, constr, parents, selfOpt, body :+ registration))
            case _ =>
                report.error("only classes/objects containing parsers can be annotated for debugging")
                List(tree)
        }
    }
}
