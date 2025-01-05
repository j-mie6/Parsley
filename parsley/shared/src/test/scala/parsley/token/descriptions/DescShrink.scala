/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.descriptions

import org.scalacheck.Shrink

import org.typelevel.scalaccompat.annotation._

object DescShrink {
    // TODO: shrinking logic for new esc desc stuff
    @nowarn3("cat=deprecation")
    implicit val escDescShrink: Shrink[EscapeDesc] = Shrink {
        case desc@EscapeDesc(_, literals, mapping, _, _, _, _, _, _) =>
            //@nowarn213("cat=deprecation")
            @nowarn3("cat=deprecation")
            val shrinkLiterals = for (ls <- Shrink.shrink(literals)) yield desc.copy(literals = ls)
            //@nowarn213("cat=deprecation")
            @nowarn3("cat=deprecation")
            val shrinkMapping = Shrink.shrink(mapping).collect {
                case ms if ms.forall(kv => Character.isValidCodePoint(kv._2)) &&
                           !literals.exists(c => ms.contains(s"$c")) => desc.copy(mapping = ms.filter(_._1.nonEmpty))
            }
            shrinkMapping ++ shrinkLiterals
    }
}
