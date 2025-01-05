/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.debug.internal

import parsley.ParsleyTest

// scalastyle:off magic.number
class XWeakMapSpec extends ParsleyTest {
    behavior of "XWeakMap"

    it should "allow lookup and removal of (strong) keys as a Map does" in {
        val xwm: XWeakMap[Object, Int] = new XWeakMap

        // Keys are hardcoded here because otherwise they'd get GC-ed.
        val (k1, v1) = (new Object(), 0)
        val (k2, v2) = (new Object(), 1)

        xwm.put(k1, v1)
        xwm.put(k2, v2)

        xwm(k1) shouldBe 0
        xwm(k2) shouldBe 1

        info("it should also allow removal of keys")

        xwm.remove(k1)
        xwm.get(k1) shouldBe None
    }

    it should "allow replacement of values" in {
        val xwm: XWeakMap[Object, String] = new XWeakMap
        val key = new Object()

        xwm.put(key, "foo")
        xwm.put(key, "bar")

        xwm(key) shouldBe "bar"
    }

    it should "not have an iterator at all" in {
        try {
            new XWeakMap[Object, Object].iterator
            fail(".iterator somehow returned.")
        } catch {
            case _: Throwable => info(".iterator call has thrown, as expected")
        }
    }
}
// scalastyle:on magic.number
