/* SPDX-FileCopyrightText: © 2022 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.collection.mutable

import scala.reflect.ClassTag

// This is designed to be a lighter-weight wrapper around Array to make it resizeable
private [internal] final class ResizableArray[A: ClassTag](initialSize: Int) {
    private [this] var array: Array[A] = new Array(initialSize)
    private [this] var size = 0

    def this() = this(initialSize = ResizableArray.InitialSize)

    def +=(x: A): Unit = {
        val arrayLength: Long = array.length
        if (arrayLength == size) {
            val newSize: Long = Math.min(arrayLength * 2, Int.MaxValue)
            val newArray: Array[A] = new Array(newSize.toInt)
            java.lang.System.arraycopy(array, 0, newArray, 0, size)
            array = newArray
        }
        array(size) = x
        size += 1
    }
    def length: Int = size
    def toArray: Array[A] = {
        val res = array
        array = null
        res
    }
}
private [internal] object ResizableArray {
    final val InitialSize = 16
}
