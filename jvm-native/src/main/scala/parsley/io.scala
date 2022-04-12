package parsley

import scala.io.{Source, Codec}
import scala.util.Try
import java.io.File

import parsley.internal.machine.Context

import scala.language.{higherKinds, implicitConversions}
import parsley.errors.ErrorBuilder

/** This module contains utilities to have parsers interact with IO, including the very useful `parseFromFile` method (exposed by `ParseFromIO`)
  * @since 3.0.0
  */
object io {
    /**
      * This class exposes a method of running parsers from a file.
      *
      * @param p The parser which serves as the method receiver
      * @param con A conversion (if required) to turn `p` into a parser
      * @version 3.0.0
      */
    implicit final class ParseFromIO[P, +A](p: P)(implicit con: P => Parsley[A]) {
        /** This method executes a parser, but collects the input to the parser from the given file.
         * The file name is used to annotate any error messages. The result of this method handles
         * exceptions and ensures the file has been properly closed.
         * @param file The file to load and run against
         * @param codec The encoding of the file
         * @return a `Try` containing a result of either a success with a value of type `A` or a failure with error message on success,
         *         and a failure if an IOException occured
         * @since 3.0.0
         */
        def parseFromFile[Err: ErrorBuilder](file: File)(implicit codec: Codec): Try[Result[Err, A]] = {
            for {
                src <- Try(Source.fromFile(file))
                input <- Try(src.mkString).recoverWith {
                    case err: Throwable =>
                        src.close()
                        scala.util.Failure(err)
                }
            } yield {
                src.close()
                new Context(con(p).internal.threadSafeInstrs, input, Some(file.getName)).runParser()
            }
        }
    }
}
