/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.internal

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

import org.typelevel.scalaccompat.annotation.unused
import parsley.debug.util.XMutMap

private [parsley] final class XWeakMap[K <: Object, V] extends XMutMap[K, V] { //
    import XWeakMap.WeakMap // scalastyle:ignore import.grouping

    private val backing: WeakMap[K, V] = new WeakMap

    override def get(key: K): Option[V] = backing.get(key).toOption

    override def addOne(kv: (K, V)): XWeakMap.this.type = {
        val _ = backing.set(kv._1, kv._2): @unused
        this
    }

    override def subtractOne(key: K): XWeakMap.this.type = {
        val _ = backing.delete(key): @unused
        this
    }

    // No iterator here. We don't need this internally.
    override def iterator: Iterator[(K, V)] = ??? // scalastyle:ignore not.implemented.error.usage
}

private [internal] object XWeakMap {
    // Simple facade type for ECMA 6's WeakMap.
    // Source : https://github.com/typelevel/cats-effect/blob/31ad01e798133f9bc62e043aac2e7709d4ba017d/core/js/src/main/scala/cats/effect/unsafe/WeakMap.scala
    // Author : Arman Bilge (@armanbilge)
    // License: Apache License, Version 2.0 (https://github.com/typelevel/cats-effect/blob/31ad01e798133f9bc62e043aac2e7709d4ba017d/LICENSE.txt)
    // State  : Modified to silence compiler warnings with @unused.
    @js.native
    @JSGlobal
    private [XWeakMap] sealed class WeakMap[K <: Object, V] extends js.Object {
        def delete(@unused key: K): Boolean = js.native
        def get(@unused key: K): js.UndefOr[V] = js.native
        def has(@unused key: K): Boolean = js.native
        def set(@unused key: K, @unused value: V): this.type = js.native
    }
}
