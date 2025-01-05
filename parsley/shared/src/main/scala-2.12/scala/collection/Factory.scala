/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package scala.collection

import scala.collection.generic.{SeqFactory, SetFactory, MapFactory, GenericTraversableTemplate}

trait Factory[-A, +C] {
    def fromSpecific(it: Traversable[A]): C
    def newBuilder: mutable.Builder[A, C]
}

object Factory {
    // these are the implicits that make it work for Scala 2.12
    implicit def fromSeqFactory[A, CC[X] <: Seq[X] with GenericTraversableTemplate[X, CC]](factory: SeqFactory[CC]): Factory[A, CC[A]] = {
        new Factory[A, CC[A]] {
            def fromSpecific(it: Traversable[A]): CC[A] = {
                val b = newBuilder
                it.foreach(b += _)
                b.result()
            }
            def newBuilder: mutable.Builder[A, CC[A]] = factory.newBuilder[A]
        }
    }

    implicit def fromSetFactory[A, CC[X] <: Set[X] with SetLike[X, CC[X]]](factory: SetFactory[CC]): Factory[A, CC[A]] = {
        new Factory[A, CC[A]] {
            def fromSpecific(it: Traversable[A]): CC[A] = {
                val b = newBuilder
                it.foreach(b += _)
                b.result()
            }
            def newBuilder: mutable.Builder[A, CC[A]] = factory.newBuilder[A]
        }
    }

    implicit def fromMapFactory[K, V, CC[K1, V1] <: Map[K1, V1] with MapLike[K1, V1, CC[K1, V1]]](factory: MapFactory[CC]): Factory[(K, V), CC[K, V]] = {
        new Factory[(K, V), CC[K, V]] {
            def fromSpecific(it: Traversable[(K, V)]): CC[K, V] = {
                val b = newBuilder
                it.foreach(b += _)
                b.result()
            }
            def newBuilder: mutable.Builder[(K, V), CC[K, V]] = factory.newBuilder[K, V]
        }
    }
}
