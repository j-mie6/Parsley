/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.collection.mutable
import scala.reflect.macros.blackbox

/** This annotation can be applied to an object or class to record their
  * names for the debugging/diagnostic combinators.
  *
  * @note It requires that the object/class is type-checkable, which due to Scala 2 macro limitations
  *       involves stripping away the enclosing type itself. This might lead to
  *       weird edge-cases: if parsley reports that type-checking failed, you
  *       should report this to the maintainers.
  * @note If odd things happen, the easiest way to resolve them is to provide an explicit
  *       type signature to members of your class/object: this will allow the annotation
  *       to ignore these during typechecking, side-stepping potential issues.
  *
  * @since 5.0.0
  */
@compileTimeOnly("macros need to be enabled to use this functionality: -Ymacro-annotations in 2.13, or use \"Macro Paradise\" for 2.12")
class debuggable extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro debuggable.impl
}

private object debuggable {
    def impl(c: blackbox.Context)(annottees: c.Tree*): c.Tree = {
        import c.universe._
        // to accurately model the Scala 3 equivalent, we are treated like a black-box
        // macro: only the first annottee is relevant, and this must be a class or an object
        // anything else is returned as is.
        val inputs = annottees.toList
        val outputs = inputs match {
            case ClassDef(mods, clsName, tyParams, Template(parents, self, body)) :: rest =>
                collect(c)(clsName.toString, body, body => ClassDef(mods, clsName, tyParams, Template(parents, self, body))) :: rest
            case ModuleDef(mods, objName, Template(parents, self, body)) :: rest =>
                collect(c)(objName.toString, body, body => ModuleDef(mods, objName, Template(parents, self, body))) :: rest
            case _ =>
                c.error(c.enclosingPosition, "only classes/objects containing parsers can be annotated for debugging")
                inputs
        }
        q"{..$outputs}"
    }

    private def collect(c: blackbox.Context)(treeName: String, defs: List[c.Tree], recon: List[c.Tree] => c.Tree): c.Tree = {
        import c.universe._
        val parsleyTy = c.typeOf[Parsley[_]].typeSymbol
        lazy val noBody = atPos(c.enclosingPosition)(q"??? : @scala.annotation.nowarn")
        // can't typecheck constructors in a stand-alone block
        val seenNames = mutable.Set.empty[TermName]
        val overloadMap = mutable.Map.empty[(TermName, Position), TermName]
        val noConDefs = defs.flatMap {
            case DefDef(_, name, _, _, _, _) if name == TermName("<init>") => None
            // in addition, on 2.12, we need to remove `paramaccessor` modifiers on constructor arguments
            case dfn@ValDef(Modifiers(_, _, annotations), name, tpt, x) =>
                // if the type tree is not empty, then we might as well scratch out the body -- helps remove problem values!
                // must use lazy here to fix problems with nested objects, for some reason...
                Some(atPos(dfn.pos)(ValDef(Modifiers(Flag.LAZY, typeNames.EMPTY, annotations), name, tpt, if (tpt.nonEmpty) noBody else x)))
            // FIXME: probably strip mods from all defdefs as above?
            // if the type tree is not empty, then we might as well scratch out the body -- helps remove problem values!
            case dfn@DefDef(mods, name, tyArgs, args, tpt, body) =>
                val finalName = if (seenNames.contains(name)) {
                    // this is an overload, don't panic
                    val newName = c.freshName(name)
                    overloadMap += ((name, dfn.pos) -> newName)
                    newName
                }
                else {
                    seenNames += name
                    overloadMap += ((name, dfn.pos) -> name)
                    name
                }
                Some(atPos(dfn.pos)(DefDef(mods, finalName, tyArgs, args, tpt, if (tpt.nonEmpty) q"???" else body)))
            case dfn => Some(dfn)
        }
        val classlessBlock = q"..${noConDefs}"
        val typelessDefs = noConDefs.filter {
            case ValDef(_, _, tpt, _) => tpt.isEmpty
            case DefDef(_, _, _, _, tpt, _) => tpt.isEmpty
            case _ => false
        }
        doPreDiagnostics(c)(treeName, typelessDefs)
        val typeMap = c.typecheck(classlessBlock, c.TERMmode, silent = true) match {
            // in this case, we want to use the original tree (it's still untyped, as required)
            // but we can process typedDefs into a map from identifiers to inferred types
            case Block(typedDefs, _) =>
                typedDefs.collect {
                    case ValDef(_, name, tpt, _) => name -> ((Nil, tpt.tpe))
                    case DefDef(_, name, _, args, ret, _) => name -> ((args.flatten.map(_.tpt.tpe), ret.tpe))
                }.toMap
            // in this case, we are stuck
            case _ =>
                if (noConDefs.nonEmpty) {
                    val faultDetermined = doDiagnostics(c)(overloadMap.keys.map(_._1).toSet, typelessDefs)
                    if (!faultDetermined) {
                        c.error(c.enclosingPosition, s"annotating `$treeName` failed because of a macro typechecking failure, with no identifiable diagnostic; please report to parsley maintainers")
                    }
                }
                Map.empty[TermName, (List[Type], Type)]
        }
        // filter the definitions based on their type from the type map:
        val parsers = defs.flatMap {
            case dfn: ValOrDefDef => typeMap.get(dfn.name) match {
                case Some((Nil, ty)) if ty.typeSymbol == parsleyTy => Some(dfn.name)
                case _ => None
            }
            case _ => None
        }
        // the idea is we inject a call to Collector.registerNames with a constructed
        // map from these identifiers to their compile-time names
        val listOfParsers = q"List(..${parsers.map(tr => q"${Ident(tr)} -> ${tr.toString}")})"
        val registration = q"parsley.debug.util.Collector.registerNames($listOfParsers.toMap)"
        // add the registration as the last statement in the object
        // TODO: in future, we want to modify all `def`s with a top level `opaque` combinator
        // that will require a bit more modification of the overall `body`, unfortunately
        recon(defs :+ registration)
    }

    private def doDiagnostics(c: blackbox.Context)(overloadings: Set[c.TermName], dfns: List[c.Tree]) = {
        var faultDetermined = false
        for (dfn <- dfns) {
            var problemFound = reportAnonClass(c)(dfn)
            problemFound ||= reportOverloading(c)(overloadings, dfn)
            faultDetermined ||= problemFound
            if (problemFound) c.error(dfn.pos, s"this definition needs an explicit type annotation for the debugging annotation to work")
        }
        faultDetermined
    }

    private def doPreDiagnostics(c: blackbox.Context)(enclosingName: String, dfns: List[c.Tree]) = {
        var problemFound = false
        for (dfn <- dfns) {
            val problemFoundDfn = reportUsedEnclosing(c)(enclosingName, dfn)
            problemFound ||= problemFoundDfn
            if (problemFoundDfn) c.error(dfn.pos, s"this definition needs an explicit type annotation for the debugging annotation to work")
        }
        if (problemFound) {
            c.abort(c.enclosingPosition, "annotation macro aborting before bad bad things happen")
        }
    }

    private def reportAnonClass(c: blackbox.Context)(dfn: c.Tree) = {
        import c.universe._
        val anonClasses = dfn.filter {
            case ClassDef(_, TypeName(name), _, _) => name.contains("$anon")
            case _ => false
        }
        for (anonClass <- anonClasses) {
            c.echo(anonClass.pos, "anonymous classes don't work properly with `parsley.debuggable`")
        }
        anonClasses.nonEmpty
    }

    private def reportOverloading(c: blackbox.Context)(overloadings: Set[c.TermName], dfn: c.Tree) = {
        import c.universe._
        val badOverloadings = dfn.filter {
            case Ident(tn: TermName) => overloadings.contains(tn)
            case _ => false
        }
        for (badOverloading <- badOverloadings) {
            c.echo(badOverloading.pos, s"overloaded functions defined within annotated object can't be used reliably")
        }
        badOverloadings.nonEmpty
    }

    private def reportUsedEnclosing(c: blackbox.Context)(enclosingName: String, dfn: c.Tree) = {
        import c.universe._
        val badUse = dfn.find {
            case Ident(TermName(name)) => name == enclosingName
            case _ => false
        }
        for (use <- badUse) {
            c.echo(use.pos, s"shadowing the enclosing class/object's name for one of its members and using it doesn't work properly")
        }
        badUse.nonEmpty
    }
}
