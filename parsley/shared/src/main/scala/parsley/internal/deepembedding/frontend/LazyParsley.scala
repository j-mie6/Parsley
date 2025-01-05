/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.frontend

import scala.annotation.nowarn
import scala.collection.mutable

import parsley.XAssert._
import parsley.state.Ref

import parsley.internal.deepembedding.{Cont, ContOps, Id}, ContOps.{perform, result, ContAdapter}
import parsley.internal.deepembedding.backend, backend.StrictParsley
import parsley.internal.diagnostics.NullParserException
import parsley.internal.machine.instructions, instructions.Instr

/** This is the root type of the parsley "frontend": it represents a combinator tree
  * where the join-points in the tree (recursive or otherwise) have not been identified
  * or factored. As such, it is a potentially cyclic graph (though finite), and must be handled with
  * caution.
  *
  * @note objects of this type may be shared across parsers or threads and, as such,
  *       must remain entirely immutable.
  */
private [parsley] abstract class LazyParsley[+A] private [deepembedding] {
    // Public API
    // $COVERAGE-OFF$
    /** Force the parser, which eagerly computes its instructions immediately */
    private [parsley] final def force(): Unit = instrs: @nowarn
    /** Denote that this parser is large enough that it might stack-overflow during
      * compilation: this allows for the slow path using `Cont` to be used immediately
      * instead of going through the (likely failing) `Id` path.
      */
    private [parsley] final def overflows(): Unit = cps = true
    // $COVERAGE-ON$

    // The instructions used to execute this parser along with the number of registers it uses
    final private [parsley] lazy val (instrs: Array[Instr], numRefs: Int) = computeInstrs

    /** This parser is the result of a `flatMap` operation, and as such may need to expand
      * the refs set. If so, it needs to know what the minimum free slot is according to
      * the context.
      *
      * @param minRef the smallest ref we are allowed to allocate to.
      */
    private [deepembedding] def setMinReferenceAllocation(minRef: Int): Unit = this.minRef = minRef

    // Internals
    // To ensure that stack-overflow cannot occur during the processing of particularly
    // large parsers, the entire internals of the "frontend" and "backend" of parsley is
    // performed via the Monad of Continuations, or `Cont`. This allows for the execution
    // of the methods below to be trampolined, which evaluates them in a loop, trading
    // stack-space for heap-space. Each method is parameterised, however, by an ''abstract''
    // `Cont`, because in the event that a parser doesn't stack overflow under normal
    // execution, it is preferable to evaluate it under the much lighter-weight Identity
    // Monad, or `Id`. The choice of monad is delegated to `computeInstrs`.
    //
    // The frontend is split into two passes: the first identifies all the shared parsers
    // within the combinator tree; and the second factors these parsers out and converts
    // the combinator tree into its strict, finite, form: `StrictParsley`.
    //
    // Pass 1: `findLets` (using `findLetsAux`) populates a `LetFinderState`
    // Pass 2: extracts the `lets` and `recs` from the state, and feeds through `optimised`
    //         (using `preprocess`)

    /** Describes how to recursively traverse this combinators sub-trees using `findLets`.
      *
      * @param seen the set of all nodes that have previously been seen by the let-finding
      * @param state stores all the information of the let-finding process
      */
    protected def findLetsAux[M[_, +_]: ContOps, R](seen: Set[LazyParsley[_]])(implicit state: LetFinderState): M[R, Unit]

    /** Describes how to recursively convert this combinator into a `StrictParsley` by `optimise`ing its sub-trees.
      *
      * @param lets the known shared parsers mapped to their corresponding join-point nodes
      * @return the strict, finite, version of this tree, with all shared parsers factored out into join-points
      */
    protected def preprocess[M[_, +_]: ContOps, R, A_ >: A](implicit lets: LetMap): M[R, StrictParsley[A_]]

    /** should the `Id` instance be skipped? */
    final private var cps = false
    final private [deepembedding] def isCps: Boolean = cps
    /** how many registers are used by the ''parent'' of this combinator (this combinator is part of a `flatMap` when this is not -1) */
    final private var minRef = -1

    /** Computes the instructions associated with this parser as well as the number of
      * registers it requires in a (possibly) stack-safe way.
      */
    final private def computeInstrs: (Array[Instr], Int) = {
        if (cps) computeInstrs(Cont.ops) else computeInstrs(Id.ops)
    }
    /** Computes the instructions associated with this parser as well as the number of
      * registers it requires within the context of a specific (unknown) monad.
      *
      * @param ops the instance for the monad to evaluate with
      */
    final private def computeInstrs[M[_, +_]](ops: ContOps[M]): (Array[Instr], Int) = pipeline(ops)

    /** Performs the full end-to-end pipeline through both the frontend and the backend.
      *
      * First performs let-finding to identify the shared parsers (recursive or otherwise)
      * within the combinator tree. Then performs let-factoring and (optimising) conversion
      * to `StrictParsley`. Then executes the backend pipeline on this strict combinator tree,
      * yielding the final results.
      *
      * @return the instructions associates with this parser as well as the number of
      *         registers it requires
      */
    final private def pipeline[M[_, +_]: ContOps]: (Array[Instr], Int) = {
        implicit val letFinderState: LetFinderState = new LetFinderState
        (perform[M, Array[Instr]] {
            findLets(Set.empty) >> {
                val usedRefs: Set[Ref[_]] = letFinderState.usedRefs
                implicit val letMap: LetMap = LetMap(letFinderState.lets, letFinderState.recs)
                for { sp <- this.optimised } yield {
                    implicit val state: backend.CodeGenState = new backend.CodeGenState(letFinderState.numRefs)
                    sp.generateInstructions(minRef, usedRefs, letMap.bodies)
                }
            }
        }, letFinderState.numRefs)
    }

    // Pass 1
    /** A recursive process that identifies shared parsers within the combinator tree.
      *
      * - first increments the "predecessor" count of this parser: this is the number of
      *   other parsers (or the top-level) that reference. When this count is more than 1
      *   this parser will be considered as shared
      * - checks whether this parser has been encountered before during the analysis: if
      *   so it is recursive, so add it to the recs set: this node '''must''' not be processed,
      *   or it will cause an infinite divergence.
      * - otherwise add any used registers to the register set and recurse
      *
      * @param seen the set of all nodes that have previously been seen by the let-finding
      * @param state stores all the information of the let-finding process
      */
    final protected [frontend] def findLets[M[_, +_]: ContOps, R](seen: Set[LazyParsley[_]])(implicit state: LetFinderState): M[R, Unit] = {
        state.addPred(this)
        if (seen.contains(this)) result(state.addRec(this))
        else if (state.notProcessedBefore(this)) {
            this match {
                case self: UsesRef => state.addRef(self.ref)
                case _             =>
            }

            try findLetsAux(seen + this)
            catch {
                // $COVERAGE-OFF$
                case NullParserException(err) => throw err // scalastyle:ignore throw
                // $COVERAGE-ON$
            }
        }
        else result(())
    }

    // Pass 2
    /** Performs the factoring out of shared parsers and then converts this parser into its strict form performing
      * optimisations on that translated form.
      *
      * @param lets the known shared parsers mapped to their corresponding join-point nodes
      * @return the strict, finite, version of this tree, with all shared parsers factored out into join-points
      */
    final protected [frontend] def optimised[M[_, +_]: ContOps, R, A_ >: A](implicit lets: LetMap): M[R, StrictParsley[A_]] = {
        if (lets.contains(this)) result(lets(this))
        else this.unsafeOptimised
    }
    /** Similar to `optimised` but should be '''only''' used on things known to be let-bindings (to avoid infinite expansion!). */
    final private [frontend] def knownLetTopOptimised[M[_, +_]: ContOps, R, A_ >: A](implicit lets: LetMap): M[R, StrictParsley[A_]] = {
        assume(lets.contains(this), "the let check can only be skipped for known let-bindings")
        this.unsafeOptimised
    }
    /** Similar to `optimised` but does not check for inclusion in the `lets` set. */
    private def unsafeOptimised[M[_, +_]: ContOps, R, A_ >: A](implicit lets: LetMap): M[R, StrictParsley[A_]] = {
        for { p <- this.preprocess } yield p.optimise
    }

    // $COVERAGE-OFF$
    // Processing with visitors.
    /** Use a visitor implementation to process this internal lazy parser. */
    def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A]

    /** Pretty names for parsers, for internal debugging purposes only. */
    private [parsley] var debugName: String
    private [parsley] def transparent(): Unit = debugName = null
    private [parsley] def opaque(name: String): Unit = debugName = name
    private [parsley] def isOpaque: Boolean = debugName != null

    /** Pretty-prints a combinator tree, for internal debugging purposes only. */
    final private [internal] def prettyAST: String = {
        implicit val ops = Id.ops
        implicit val letFinderState: LetFinderState = new LetFinderState
        findLets(Set.empty)
        implicit val letMap: LetMap = LetMap(letFinderState.lets, letFinderState.recs)
        implicit val state: backend.CodeGenState = new backend.CodeGenState(0)
        val mrecs = for {
            (let, p) <- letMap.bodies
        } yield s"${state.getLabel(let, producesResults = true)}: ${p.pretty}"
        s"main body: ${this.optimised.pretty}\n${mrecs.mkString("\n")}"
    }
    // $COVERAGE-ON$
}

/** A mix-in trait that denotes that this parser uses a specific register, which must be allocated. */
private [deepembedding] trait UsesRef {
    /** The register used by this combinator. */
    val ref: Ref[_]
}

/** This is a collection of builders that track the shared parsers and used registers during Pass 1 */
private [deepembedding] class LetFinderState {
    private val _recs = mutable.Set.empty[LazyParsley[_]]
    private val _preds = mutable.Map.empty[LazyParsley[_], Int]
    private val _usedRefs = mutable.Set.empty[Ref[_]]

    /** Adds a "predecessor" to a given parser, which means that it is referenced by another parser.
      *
      * @note assumes that a parser isn't given a predecessor by the same root twice.
      *
      * @param p the parser to add a predecessor to
      */
    private [frontend] def addPred(p: LazyParsley[_]): Unit = _preds(p) = _preds.getOrElse(p, 0) + 1
    /** If a parser is identified as being recursive, keep track of it.
      *
      * @param p a recursive parser
      */
    private [frontend] def addRec(p: LazyParsley[_]): Unit = _recs += p
    /** If a register has been used by a parser, keep track of it.
      *
      * @param reg the register used by the parser.
      */
    private [frontend] def addRef(ref: Ref[_]): Unit = _usedRefs += ref
    /** Has the given parser never been analysed before? */
    private [frontend] def notProcessedBefore(p: LazyParsley[_]): Boolean = _preds(p) == 1

    /** Returns all the parsers which are referenced two or more times across the tree. */
    private [frontend] def lets: Iterable[LazyParsley[_]] = _preds.toSeq.view.collect {
        case (p, refs) if refs >= 2 => p
    }
    /** Returns all the recursive parsers in the tree */
    private [frontend] lazy val recs: Set[LazyParsley[_]] = _recs.toSet
    /** Returns all the registers used by the parser */
    private [frontend] def usedRefs: Set[Ref[_]] = _usedRefs.toSet
    /** Returns the number of registers used by the parser */
    private [frontend] def numRefs: Int = _usedRefs.size
}

/** Represents a map of let-bound lazy parsers to their strict equivalents. */
private [deepembedding] final class LetMap private (letGen: Map[LazyParsley[_], LetMap => StrictParsley[_]], recs: Set[LazyParsley[_]]) {
    // This might not necessarily contain Let nodes: if they were inlined then they will not be present here
    private val letMap = mutable.Map.empty[LazyParsley[_], StrictParsley[_]]
    private val bodyMap = mutable.Map.empty[backend.Let[_], StrictParsley[_]]

    /** Is the given parser a let-binding? */
    def contains(p: LazyParsley[_]): Boolean = letGen.contains(p)

    /** Returns the strict parser that represents a given let-bound parser.
      *
      * @note this does not necessary return a `Let` node, as the underlying parser may be inlined.
      */
    def apply[A](p: LazyParsley[A]): StrictParsley[A] = letMap.get(p) match {
        case Some(let) => let.asInstanceOf[StrictParsley[A]]
        case None =>
            assume(contains(p), "only let-bound parsers can be mapped to a strict version in the let-map")
            val ourLet = new backend.Let[A]
            // MUST be added before processing the body
            letMap(p) = ourLet
            val sp = letGen(p)(this)
            if (sp.inlinable && !recs.contains(p)) {
                letMap(p) = sp
                sp.asInstanceOf[StrictParsley[A]]
            }
            else {
                bodyMap(ourLet) = sp
                ourLet
            }
    }

    def bodies: Map[backend.Let[_], StrictParsley[_]] = bodyMap.toMap

    // $COVERAGE-OFF$
    override def toString: String = letMap.toString
    // $COVERAGE-ON$
}
private [frontend] object LetMap {
    /** Builds a `LetMap` given the sequence of let-bound parsers as well as the map of recursive parsers
      *
      * @param lets the identified shared non-recursive parsers to include
      * @param recs the identified recursive parsers that may be required in the translation
      */
    def apply[M[_, +_]: ContOps](lets: Iterable[LazyParsley[_]], recs: Set[LazyParsley[_]]): LetMap = {
        new LetMap(lets.map(p => p -> ((_self: LetMap) => {
            implicit val self: LetMap = _self
            perform[M, StrictParsley[_]](p.knownLetTopOptimised)
        })).toMap, recs)
    }
}
