/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.singletons

import parsley.internal.deepembedding.backend.MZero
import parsley.internal.errors.CaretWidth
import parsley.internal.machine.instructions

// This doesn't need to be a CaretWidth, because empty generates errors that are the least dominating kind anyway
private [parsley] final class Empty private (val width: Int) extends Singleton[Nothing] with MZero {
    // $COVERAGE-OFF$
    override val pretty: String = "empty"
    // $COVERAGE-ON$
    override val instr: instructions.Instr = new instructions.Empty(width)
}

private [parsley] final class Fail(width: CaretWidth, msgs: String*) extends Singleton[Nothing] with MZero {
    // $COVERAGE-OFF$
    override def pretty: String = s"fail(${msgs.mkString(", ")})"
    // $COVERAGE-ON$
    override def instr: instructions.Instr = new instructions.Fail(width, msgs: _*)
}

private [parsley] final class Unexpected(msg: String, width: CaretWidth) extends Singleton[Nothing] with MZero {
    // $COVERAGE-OFF$
    override def pretty: String = s"unexpected($msg)"
    // $COVERAGE-ON$
    override def instr: instructions.Instr = new instructions.Unexpected(msg, width)
}

private [parsley] object Empty {
    val Zero = new Empty(0)
    def apply(width: Int): Empty = if (width == 0) Zero else new Empty(width)
}
