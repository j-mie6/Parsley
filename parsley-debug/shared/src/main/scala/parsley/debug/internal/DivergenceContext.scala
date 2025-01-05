/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.internal

import scala.annotation.nowarn
import scala.collection.mutable

import parsley.exceptions.ParsleyException

import parsley.internal.deepembedding.frontend.LazyParsley

// FIXME: in future, this should use better tuned exception names

private [parsley] final class DivergenceContext {
    case class CtxSnap(pc: Int, instrs: Array[_], off: Int, regs: List[AnyRef])
    case class HandlerSnap(pc: Int, instrs: Array[_])
    private case class Snapshot(name: String, internalName: String, ctxSnap: CtxSnap, handlerSnap: Option[HandlerSnap], children: mutable.ListBuffer[Snapshot]) {
        // this is true when the ctxSnaps match
        def matchesParent(that: Snapshot): Boolean = this.ctxSnap == that.ctxSnap

        // this is true when the handlers are equal (they should exist!) and the ctxSnaps match
        // TODO: technically, we should check that the instrs match for the handler and the ctx?
        def matchesSibling(that: Snapshot): Boolean = this.handlerSnap == that.handlerSnap && this.ctxSnap == that.ctxSnap
    }

    // NOTE: Pruning snapshots sounds attractive, but doesn't really work, at least for siblings
    // consider `val p = r.updateDuring(_ => random())(lookAhead(char('a'))) *> p`
    // this I think in an iterative example would be unable to resolve if the offset from the lookahead dominates
    // For a recursive structure, I think the pruning actually would work, and can be done
    private val snaps = mutable.Stack.empty[Snapshot]

    def takeSnapshot(parser: LazyParsley[_], userAssignedName: Option[String], ctxSnap: CtxSnap, handlerSnap: Option[HandlerSnap]): Unit = {
        val name = Renamer.nameOf(userAssignedName, parser)
        val internalName = Renamer.internalName(parser)
        // at this point we may have some old snapshots on the stack
        // we also have a current snapshot and optional handler snapshot ready to go
        val self = Snapshot(name, internalName, ctxSnap, handlerSnap, mutable.ListBuffer.empty)

        // first step is to check for divergence
        // we must have a parent snapshot for it to possible that we have diverged
        if (snaps.nonEmpty) {
            val parent = snaps.top
            // these are the sibling calls, which are direct children of our parent
            val siblings = parent.children

            // there are two routes to divergence: left-recursion and non-productive iteration
            // the former involves searching for an equivalent CtxSnap somewhere along the stack, the path along the way would be the trace
            if (snaps.exists(self.matchesParent(_))) { //TODO: as soon as the offset changes, the search can stop?
                val cycle = snaps.view.takeWhile(!self.matchesParent(_)).collect {
                    // internal names aren't particularly useful here, filter them out
                    case s if s.name != s.internalName => (s.name, s.ctxSnap.regs)
                }.toVector.reverse
                reportLeftRecursion(name, ctxSnap.regs, cycle)
            }
            // the latter involves the same but along our siblings -- in this case, us and our parent are relevant for reporting the issue
            else if (siblings.exists(self.matchesSibling(_))) { //TODO: as soon as the offset changes, the search can stop?
                val states = siblings.view.takeWhile(!self.matchesSibling(_)).collect {
                    // we need them to be proper siblings for the path to match up
                    case snap if snap.handlerSnap == self.handlerSnap => snap.ctxSnap.regs
                }.toVector.reverse
                reportNonProductiveIteration(name, internalName, parent.name, parent.internalName, ctxSnap.regs, states)
            }
            // no divergence, register ourself as a sibling to the parent (put at the front!!!)
            self +=: siblings
        }
        // at this point, we know divergence didn't occur, so we can push ourself onto the stack
        snaps.push(self)
    }
    def dropSnapshot(): Unit = snaps.pop(): @nowarn
    def reset(): Unit = snaps.clear()

    private final val MissingInformation =
        s"""
           |Left-recursion has been detected in the given parser; however, there is not
           |enough information to determine the cycle. To get the full cycle diagnostic,
           |please use the `parsley.debuggable` annotation to populate the name information.
           |
           |For example, if your parsers are exposed (publically) in an object called
           |`foo`, you should write:
           |
           |> @parsley.debuggable object foo { ... }
           |
           |Alternatively, you can give individual parser fragments names by using the
           |`named` combinator, which will cause them to appear along the path.
           |""".stripMargin

    private def LeftRecursion(cycle: Iterable[String]): String =
        s"""
           |Left-recursion has been detected in the given parser. The trace is as follows:
           |
           |${cycle.mkString("\n")}
           |
           |For readability, all non-named combinators have been stripped out -- to see more,
           |use the `named` combinator to tag parts of the parser you want to see appear in
           |the trace.
           |""".stripMargin

    private def reportLeftRecursion(name: String, regs: List[AnyRef], cycle: Vector[(String, List[AnyRef])]): Nothing = {
        // if the cycle is empty, this means there is no name information
        if (cycle.isEmpty) throw new ParsleyException(MissingInformation)
        else {
            // if all the registers are the same, there is no point reporting the state in the cycle
            val stateFree = (regs +: cycle.map(_._2)).distinct.size == 1
            val cycle2 = (name, regs) +: cycle :+ ((name, regs))
            if (stateFree) throw new ParsleyException(LeftRecursion(cycle2.map(_._1)))
            else throw new ParsleyException(LeftRecursion(cycle2.map {
                // this is horrid, but it'll (TODO: some day allow for watched references like in `debug`?)
                case (name, regs) => s"$name (with state ${regs.mkString("(", ", ", ")")})"
            }))
        }
    }

    private def reportNonProductiveIteration(bodyName: String, bodyInternal: String,
                                             loopName: String, loopInternal: String,
                                             curState: List[AnyRef], states: Vector[List[AnyRef]]): Nothing = {
        val cycle = curState +: states :+ curState
        // no point talking about the state cycle if there is no changes
        val stateFree = cycle.distinct.size == 1
        // if the states do form a cycle, we should report the cycle
        lazy val cycleStr = cycle.map(_.mkString("(", ", ", ")")).mkString("\n")
        val stateNote = if (!stateFree) s"\nand adjusts the state in a cyclic way, as follows:\n\n$cycleStr" else "."

        // if the names are only internal, we should direct the user to either use the collector or use the
        // named combinator to get further information
        val couldRefine = (if (bodyName == bodyInternal) Set("the body") else Set.empty) ++
                          (if (loopName == loopInternal) Set("the loop") else Set.empty)
        val refineMsg =
            if (couldRefine.nonEmpty)
            s"""
               |
               |More precise names for ${couldRefine.mkString(" and ")} can be sourced using @parsley.debuggable
               |or the `named` combinator.""".stripMargin
            else ""

        val msg =
            s"\n`$loopName` is looping unproductively as `$bodyName` can succeed having not consumed input$stateNote$refineMsg"
        throw new ParsleyException(msg)
    }
}
