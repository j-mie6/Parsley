/* SPDX-FileCopyrightText: © 2021 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.machine.stacks

private [machine] final class HandlerStack(val depth: Int, val pc: Int, val stacksz: Int, val tail: HandlerStack)
private [machine] object HandlerStack extends Stack[HandlerStack] {
    implicit val inst: Stack[HandlerStack] = this
    type ElemTy = (Int, Int, Int)
    // $COVERAGE-OFF$
    override protected def show(x: ElemTy): String = {
        val (depth, pc, stacksz) = x
        s"Handler@$depth:$pc(-${stacksz + 1})"
    }
    override protected def head(xs: HandlerStack): ElemTy = (xs.depth, xs.pc, xs.stacksz)
    override protected def tail(xs: HandlerStack): HandlerStack = xs.tail
    // $COVERAGE-ON$
}
