/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import java.io.File

import scala.io.{Codec, Source}
import scala.util.Try

import parsley.errors.ErrorBuilder

import parsley.internal.machine.Context

trait PlatformSpecific {
    /** This class exposes a method of running parsers from a file.
      *
      * This extension class operates on values that are convertible to parsers. It enables the use of
      * an alternative `parseFile` method, which can be used to run a parser on the contents of a file.
      *
      * @constructor This constructor should not be called manually, it is designed to be used via Scala's implicit resolution.
      * @param p the value that this class is enabling methods on.
      * @version 4.5.0
      */
    implicit final class ParseFromIO[+A](p: Parsley[A]) {
        /** This method executes a parser, but collects the input to the parser from the given file.
          *
          * The file name is used to annotate any error messages. The result of this method handles
          * exceptions and ensures the file has been properly closed.
          *
          * @param file the file to load and run against.
          * @param codec the encoding of the file.
          * @return a `Try` containing a result of either a success with a value of type `A` or a failure with error message on success,
          *         and a failure if an IOException occured.
          * @group run
          * @since 4.5.0
          */
        def parseFile[Err: ErrorBuilder](file: File)(implicit codec: Codec): Try[Result[Err, A]] = {
            for {
                src <- Try(Source.fromFile(file))
                input <- Try(src.mkString).recoverWith {
                    case err: Throwable =>
                        src.close()
                        scala.util.Failure(err)
                }
            } yield {
                src.close()
                val internal = p.internal
                new Context(internal.instrs, input, internal.numRefs, Some(file.getName)).run()
            }
        }
    }
}
