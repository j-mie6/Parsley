/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.internal

import scala.collection.mutable

import parsley.debug.util.XMutMap

// Abstracts away the logic of XWeakMap so that only minimal interfaces are needed between Scala
// versions. This reduces code duplication, and makes further changes much easier to implement.
private [parsley] final class XWeakMap[K, V] extends XMutMap[K, V] with Iterable[(K, V)] {
    // If we ever need to change this type, it can be done here instead of in each individual
    // implementation of XWeakMap.
    private val backing = mutable.WeakHashMap.empty[K, V]

    // There's not a lot of key dropping happening in this codebase, but this is here to conform with the
    // mutable.Map[K, V] trait more closely.
    // $COVERAGE-OFF$
    override def subtractOne(k: K): XWeakMap.this.type = {
        backing -= k
        this
    }
    // $COVERAGE-ON$

    override def addOne(kv: (K, V)): XWeakMap.this.type = {
        backing.update(kv._1, kv._2)
        this
    }

    override def get(key: K): Option[V] = backing.get(key)

    // $COVERAGE-OFF$
    override def iterator: Iterator[(K, V)] = backing.iterator
    // $COVERAGE-ON$
}
