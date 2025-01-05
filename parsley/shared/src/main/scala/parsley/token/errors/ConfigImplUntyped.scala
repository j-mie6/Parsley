/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.errors

import parsley.Parsley
import parsley.errors.combinator.ErrorMethods

// This feels wrong? perhaps token is the wrong package
// Because this is now used for Char, Sat, String to encode the label config...
import parsley.internal.errors.{ExpectDesc, ExpectItem, ExpectRaw}

import org.typelevel.scalaccompat.annotation.unused

private [parsley] sealed trait ConfigImplUntyped {
    private [parsley] def apply[A](p: Parsley[A]): Parsley[A]
}

// TODO: move into internal?
// Relaxing Types
private [parsley] sealed trait LabelOps {
    private [parsley] def asExpectDescs: Iterable[ExpectDesc]
    private [parsley] def asExpectDescs(otherwise: String): Iterable[ExpectDesc]
    private [parsley] def asExpectItems(raw: String): Iterable[ExpectItem]
    private [parsley] final def asExpectItems(raw: Char): Iterable[ExpectItem] = asExpectItems(s"$raw")
}

private [parsley] sealed trait ExplainOps {
    private [parsley] def asReason: Option[String]
}

// Constraining Types
/** This type can be used to configure ''both'' errors that make labels and those that make reasons.
  * @since 4.1.0
  * @group labels
  */
sealed trait LabelWithExplainConfig extends ConfigImplUntyped with LabelOps with ExplainOps {
    private [parsley] def orElse(other: LabelWithExplainConfig): LabelWithExplainConfig
}
/** This type can be used to configure errors that make labels.
  * @since 4.1.0
  * @group labels
  */
sealed trait LabelConfig extends LabelWithExplainConfig {
    private [parsley] def orElse(other: LabelConfig): LabelConfig
}
/** This type can be used to configure errors that make reasons.
  * @since 4.1.0
  * @group labels
  */
sealed trait ExplainConfig extends LabelWithExplainConfig

private [parsley] sealed trait Labeller {
    private [parsley] def config(name: String): LabelConfig
}

/** This class represents configurations producing labels: labels may not be empty.
  * @since 4.1.0
  * @group labels
  */
final class Label(val label: String, val labels: String*) extends LabelConfig {
    require(label.nonEmpty && labels.forall(_.nonEmpty), "labels cannot be empty strings")
    private [parsley] final override def apply[A](p: Parsley[A]) = p.label(label, labels: _*)
    private [parsley] final override def asExpectDescs: Iterable[ExpectDesc] = (label +: labels).map(new ExpectDesc(_))
    private [parsley] final override def asExpectDescs(@unused otherwise: String) = asExpectDescs
    private [parsley] final override def asExpectItems(@unused raw: String) = asExpectDescs
    private [parsley] final override def orElse(config: LabelWithExplainConfig) = config match {
        case r: Reason => new LabelAndReason(r.reason, label, labels: _*)
        case lr: LabelAndReason => new LabelAndReason(lr.reason, label, labels: _*)
        case _ => this
    }
    private [parsley] final override def orElse(config: LabelConfig) = this
    private [parsley] final override def asReason: Option[String] = None
}
/** @since 4.1.0
  * @group labels
  */
object Label extends Labeller {
    def apply(label: String, labels: String*): LabelConfig = new Label(label, labels: _*)
    private [parsley] final def config(name: String) = new Label(name)
}

/** This object configures labels by stating that it must be hidden.
  * @since 4.1.0
  * @group labels
  */
object Hidden extends LabelConfig {
    private [parsley] final override def apply[A](p: Parsley[A]) = p.hide
    private [parsley] final override def asExpectDescs = None
    private [parsley] final override def asExpectDescs(@unused otherwise: String) = asExpectDescs
    private [parsley] final override def asExpectItems(@unused raw: String) = asExpectDescs
    private [parsley] final override def orElse(config: LabelWithExplainConfig) = this
    private [parsley] final override def orElse(config: LabelConfig) = this
    private [parsley] final override def asReason: Option[String] = None
}

/** This object has a factory for configurations producing reasons: if the empty string is provided, this equivalent to [[NotConfigured `NotConfigured`]].
  * @since 4.1.0
  * @group labels
  */
final class Reason(val reason: String) extends ExplainConfig {
    require(reason.nonEmpty, "reasons cannot be empty strings")
    private [parsley] final override def apply[A](p: Parsley[A]) = p.explain(reason)
    private [parsley] final override def asExpectDescs = None
    private [parsley] final override def asExpectDescs(otherwise: String) = Some(new ExpectDesc(otherwise))
    private [parsley] final override def asExpectItems(raw: String) = Some(new ExpectRaw(raw))
    private [parsley] final override def orElse(config: LabelWithExplainConfig) = config match {
        case l: Label => new LabelAndReason(reason, l.label, l.labels: _*)
        case lr: LabelAndReason => new LabelAndReason(reason, lr.label, lr.labels: _*)
        case _ => this
    }
    private [parsley] final override def asReason: Option[String] = Some(reason)
}
/** @since 4.1.0
  * @group labels
  */
object Reason {
    def apply(reason: String): ExplainConfig = new Reason(reason)
}

/** This object has a factory for configurations producing labels and reasons: the reason and labels cannot be empty.
  * @since 4.1.0
  * @group labels
  */
final class LabelAndReason(val reason: String, val label: String, val labels: String*) extends LabelWithExplainConfig {
    require(reason.nonEmpty, "reason cannot be empty strings, use `Label` instead")
    require(label.nonEmpty && labels.forall(_.nonEmpty), "labels cannot be empty strings")
    private [parsley] final override def apply[A](p: Parsley[A]) = p.label(label, labels: _*).explain(reason)
    private [parsley] final override def asExpectDescs = (label +: labels).map(new ExpectDesc(_))
    private [parsley] final override def asExpectDescs(@unused otherwise: String) = asExpectDescs
    private [parsley] final override def asExpectItems(@unused raw: String) = asExpectDescs
    private [parsley] final override def orElse(config: LabelWithExplainConfig) = this
    private [parsley] final override def asReason: Option[String] = Some(reason)
}
/** @since 4.1.0
  * @group labels
  */
object LabelAndReason {
    def apply(reason: String, label: String, labels: String*): LabelWithExplainConfig = new LabelAndReason(reason, label, labels: _*)
}

/** This object specifies that no special labels or reasons should be generated, and default errors should be used instead.
  * @since 4.1.0
  * @group labels
  */
object NotConfigured extends LabelConfig with ExplainConfig with LabelWithExplainConfig with Labeller {
    private [parsley] final override def apply[A](p: Parsley[A]) = p
    private [parsley] final override def asExpectDescs = None
    private [parsley] final override def asExpectDescs(otherwise: String) = Some(new ExpectDesc(otherwise))
    private [parsley] final override def asExpectItems(raw: String) = Some(new ExpectRaw(raw))
    private [parsley] final override def orElse(config: LabelWithExplainConfig) = config
    private [parsley] final override def orElse(config: LabelConfig) = config
    private [parsley] final override def asReason: Option[String] = None
    private [parsley] final def config(name: String) = this
}
