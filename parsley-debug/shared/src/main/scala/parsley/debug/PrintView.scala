/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug

import java.io.{OutputStream, PrintStream}

import scala.annotation.tailrec

/** A (reusable) pretty-printer for the debugger which outputs to the console.
  *
  * It is recommended that all memory-heavy types (e.g. closures) are not stored explicitly. Consult the documentation
  * on attaching debuggers to find out how to prevent that.
  *
  * @since 5.0.0
  * @group debugview
  */
object PrintView extends DebugView.Reusable with PrintView {
    override protected def out: PrintStream = Console.out
    /** Create a string pretty-printer that outputs to an arbitrary place
      *
      * @since 5.0.0
      */
    def apply(out: OutputStream): DebugView.Reusable = apply(new PrintStream(out))
    /** Create a string pretty-printer that outputs to an arbitrary place
      *
      * @since 5.0.0
      */
    def apply(print: PrintStream): DebugView.Reusable = new PrintView {
        // this could be a file-handle, so we override as a val
        override protected val out: PrintStream = print
    }
}

private [debug] sealed trait PrintView extends DebugView.Reusable {
    // this could be a stream that updates, like Console.out, so eval on demand by default
    protected def out: PrintStream
    override private [debug] def render(input: =>String, tree: =>DebugTree): Unit = {
        out.println(s"${tree.parserName}'s parse tree for input:\n\n$input\n\n")
        pretty(tree)
    }

    private def bury(str: String, withMark: Boolean, indents: Vector[String]): Unit = out.println {
        indents match {
            case is :+ _ if withMark => s"${is.mkString}+-$str"
            case is => s"${is.mkString}$str"
        }
    }

    private def pretty(dt: DebugTree, indents: Vector[String] = Vector.empty): Unit = {
        val childName = dt.childNumber.fold("")(n => s"($n)")
        val uname = if (dt.parserName != dt.internalName) s"${dt.parserName} (${dt.internalName}$childName)" else s"${dt.internalName}$childName"
        val results = dt.parseResults.map(printParseAttempt).mkString

        bury(s"[ $uname ]: $results", withMark = true, indents)
        printChildren(dt, indents, dt.nodeChildren)
    }

    // Print a parse attempt in a human-readable way.
    private def printParseAttempt(attempt: ParseAttempt): String = {
        val status = attempt.result.fold("Failure")(x => s"Success - [ $x ]")
        s"""(\"${attempt.rawInput}\" [${attempt.fromPos} -> ${attempt.toPos}], $status)"""
    }

    // Print all the children, remembering to add a blank indent for the last child.
    @tailrec private def printChildren(dt: DebugTree, indents: Vector[String], children: List[DebugTree]): Unit = children match {
        case List(t) =>
            bury("|", withMark = false, indents)
            pretty(t, indents :+ "  ")
        case t :: xs =>
            bury("|", withMark = false, indents)
            pretty(t, indents :+ "| ")
            printChildren(dt, indents, xs)
        case Nil =>
    }
}
