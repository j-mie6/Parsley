/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.singletons

import parsley.registers.Reg
import parsley.token.errors.LabelConfig

import parsley.internal.machine.instructions

private [parsley] final class Satisfy(private [Satisfy] val f: Char => Boolean, val expected: LabelConfig) extends Singleton[Char] {
    // $COVERAGE-OFF$
    override val pretty: String = "satisfy(f)"
    // $COVERAGE-ON$
    override def instr: instructions.Instr = new instructions.Satisfies(f, expected)
}

private [parsley] object Line extends Singleton[Int] {
    // $COVERAGE-OFF$
    override val pretty: String = "line"
    // $COVERAGE-ON$
    override val instr: instructions.Instr = instructions.Line
}
private [parsley] object Col extends Singleton[Int] {
    // $COVERAGE-OFF$
    override val pretty: String = "col"
    // $COVERAGE-ON$
    override val instr: instructions.Instr = instructions.Col
}
private [parsley] object Offset extends Singleton[Int] {
    // $COVERAGE-OFF$
    override val pretty: String = "offset"
    // $COVERAGE-ON$
    override val instr: instructions.Instr = instructions.Offset
}

// This should really have UsesRegister, however, if it doesn't, this has the nice effect of catching
// registers that have never been filled in some way!
private [parsley] final class Get[S](reg: Reg[S]) extends Singleton[S] {
    // $COVERAGE-OFF$
    override def pretty: String = s"get($reg)"
    // $COVERAGE-ON$
    override def instr: instructions.Instr = new instructions.Get(reg.addr)
}

private [deepembedding] object Satisfy {
    def unapply(self: Satisfy): Some[Char => Boolean] = Some(self.f)
}
