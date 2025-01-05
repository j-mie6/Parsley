/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.diagnostics

import parsley.exceptions.{BadLazinessException, ParsleyException}

private [parsley] object UserException {
    def unapply(e: Throwable): Option[Throwable] = e match {
        case _: ParsleyException => None
        case e if userStackTrace(e.getStackTrace) =>
            e.setStackTrace(pruneParsley(e.getStackTrace))
            Some(e)
        case _ => None
    }

    def userStackTrace(e: Array[StackTraceElement]) = e.view.takeWhile(!_.getClassName.startsWith("parsley.internal")).exists { ste =>
        !ste.getClassName.startsWith("scala") || !ste.getClassName.startsWith("java")
    }
    def pruneParsley(e: Array[StackTraceElement]): Array[StackTraceElement] = {
        val (userBits, parsleyTrace) = e.span(!_.getClassName.startsWith("parsley.internal"))
        userBits ++ parsleyTrace.dropWhile(_.getClassName.startsWith("parsley.internal"))
    }
}

private [parsley] object NullParserException {
    def unapply(e: Throwable): Option[Throwable] = e match {
        // this should only be true when the null was tripped from within the parsley namespace,
        // not the user one
        case e: NullPointerException if !UserException.userStackTrace(e.getStackTrace) => Some(new BadLazinessException)
        case _ => None
    }
}
