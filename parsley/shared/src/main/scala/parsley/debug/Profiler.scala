/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug

import scala.annotation.tailrec
import scala.collection.mutable

/** This class is used to store the profile data for a specific group of sub-parsers.
  *
  * It records the start and end timestamps of the parsers that interact with it. It is possible
  * to use multiple different profilers if you want to establish the cumulative time for a sub-parser
  * instead of the self-time.
  *
  * This class is mutable, so care must be taken to call `reset()` between runs, unless you want to
  * accumulate the data.
  *
  * @since 4.4.0
  */
class Profiler {
    private val entries = mutable.Map.empty[String, mutable.Buffer[Long]]
    private val exits = mutable.Map.empty[String, mutable.Buffer[Long]]
    private var lastTime: Long = 0
    private var lastTimeCount: Long = 0

    // $COVERAGE-OFF$
    /** Prints a summary of the data sampled by this profiler.
         *
         * After the run(s) of the parser are complete, this method can be used to
         * generate the summary of the sampled data. It will print a table where the
         * total "self-time", number of invocations and average "self-time" are displayed
         * for each profiled sub-parser.
         *
         * * '''self-time''': this is the amount of time spend in a specific parser, removing
         *                    the times from within the child parsers.
         *
         * @note to measure cumulative time of a parser, consider using a separate `Profiler`
         *       object for it instead.
         * @since 4.4.0
         */
    def summary(): Unit = {
        val (selfTotals, invocations) = process
        render(selfTotals, invocations)
    }
    // $COVERAGE-ON$

    /** Clears the data within this profiler.
         * @since 4.4.0
         */
    def reset(): Unit = {
        // can't clear the maps, because the instructions may have already captured the lists
        for ((_, timings) <- entries) timings.clear()
        for ((_, timings) <- exits) timings.clear()
        lastTime = 0
        lastTimeCount = 0
    }

    private [parsley] def entriesFor(name: String): mutable.Buffer[Long] = entries.getOrElseUpdate(name, mutable.ListBuffer.empty)
    private [parsley] def exitsFor(name: String): mutable.Buffer[Long] = exits.getOrElseUpdate(name, mutable.ListBuffer.empty)
    private [parsley] def monotone(n: Long) = {
        if (n == lastTime) {
            lastTimeCount += 1
            n + lastTimeCount
        }
        else {
            lastTime = n
            lastTimeCount = 0
            n
        }
    }

    private [parsley] def process: (Map[String, Long], Map[String, Int]) = {
        val allEntries = collapse(entries).sortBy(_._2)
        val allExits = collapse(exits).sortBy(_._2)

        require((allEntries ::: allExits).toSet.size == (allExits.length + allExits.length),
                "recorded times must all be monotonically increasing")

        val selfTotals = mutable.Map.empty[String, Long]
        val invocations =  mutable.Map.empty[String, Int]

        @tailrec
        def go(entries: List[(String, Long)], exits: List[(String, Long)], stack: List[((String, Long), Long)], cum: Long): Unit = {
            (entries, exits, stack) match {
                case (Nil, Nil, Nil) =>
                // final unwinding or stuff to clear on the stack (cum here is for the children)
                case (ens, (n2, t2)::exs, ((n1, t1), oldCum)::stack) if ens.headOption.forall(t2 < _._2) =>
                    assert(n1 == n2, "unwinding should result in matching values")
                    add(invocations, n1)(1)
                    add(selfTotals, n1)(t2 - t1 - cum)
                    go(ens, exs, stack, oldCum + t2 - t1)
                // in this case, the scope closes quickly (cum here is for your siblings)
                case ((n1, t1)::ens, (n2, t2)::exs, stack) if ens.headOption.forall(t2 < _._2) && n1 == n2 =>
                    assert(ens.nonEmpty || n1 == n2, "unwinding should result in matching values")
                    add(invocations, n1)(1)
                    add(selfTotals, n1)(t2 - t1)
                    go(ens, exs, stack, cum + t2 - t1)
                // the next one opens first, or the entry and exit don't match
                // in either case, this isn't our exit, push ourselves onto the stack (cum here is for your siblings)
                case (nt::ens, exs@(_ :: _), stack) => go(ens, exs, (nt, cum)::stack, 0)
                // $COVERAGE-OFF$
                case (Nil, Nil, _::_)
                    | (Nil, _::_, Nil)
                    | (_ ::_, Nil, _) => assert(false, "something has gone very wrong")
                case (Nil, _::_, _::_) => ??? // deadcode from case 2
                // $COVERAGE-ON$
            }
        }

        //println(allEntries.map { case (name, t) => (name, t - allEntries.head._2) })
        //println(allExits.map { case (name, t) => (name, t - allEntries.head._2) })
        go(allEntries, allExits, Nil, 0)
        (selfTotals.toMap, invocations.toMap)
    }

    private def collapse(timings: Iterable[(String, Iterable[Long])]): List[(String, Long)] = timings.flatMap {
        case (name, times) => times.map(t => (name, t))
    }.toList

    private def add[A: Numeric](m: mutable.Map[String, A], name: String)(n: A): Unit = m.get(name) match {
        case Some(x) => m(name) = implicitly[Numeric[A]].plus(x, n)
        case None => m(name) = n
    }

    // $COVERAGE-OFF$
    private def render(selfTimes: Map[String, Long], invocations: Map[String, Int]): Unit = {
        val combined = selfTimes.map {
            case (name, selfTime) =>
                val invokes = invocations(name)
                (name, (f"${selfTime/1000.0}%.1fμs", invocations(name), f"${selfTime/invokes/1000.0}%.3fμs"))
        }
        val head1 = "name"
        val head2 = "self time"
        val head3 = "num calls"
        val head4 = "average self time"

        val (names, data) = combined.unzip
        val (selfs, invokes, avs) = data.unzip3

        val col1Width = (head1.length :: names.map(_.length).toList).max
        val col2Width = (head2.length :: selfs.map(_.length).toList).max
        val col3Width = (head3.length :: invokes.map(digits(_)).toList).max
        val col4Width = (head4.length :: avs.map(_.length).toList).max

        val header = List(pad(head1, col1Width), tab(col1Width),
                            pad(head2, col2Width), tab(col2Width),
                            pad(head3, col3Width), tab(col3Width),
                            pad(head4, col4Width)).mkString
        val hline = header.map(_ => '-')

        println(header)
        println(hline)
        for ((name, (selfTime, invokes, avSelfTime)) <- combined) {
            println(List(pad(name, col1Width), tab(col1Width),
                            prePad(selfTime, col2Width), tab(col2Width),
                            prePad(invokes.toString, col3Width), tab(col3Width),
                            prePad(avSelfTime, col4Width)).mkString)
        }
        println(hline)
    }

    private def pad(str: String, n: Int) = str + " " * (n - str.length)
    private def prePad(str: String, n: Int) = " " * (n - str.length) + str
    private def digits[A: Numeric](n: A): Int = Math.log10(implicitly[Numeric[A]].toDouble(n)).toInt + 1
    private def tab(n: Int) = " " * (4 - n % 4)
    // $COVERAGE-ON$
}
