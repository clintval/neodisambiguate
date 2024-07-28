package io.cvbio.neodisambiguate

/** Common math utilities. */
object MathUtil {

  /** Implicits for counting the number of maximum and minimum items in a collection. */
  implicit class WithCountMaxMinBy[A](self: Seq[A]) {

    /** Count the number of maximally occurring items in a collection after applying a function. */
    def countMaxBy[B](fn: A => B)(implicit cmp: Ordering[B]): Int = MathUtil.countMax(self.map(fn))

    /** Count the number of minimally occurring items in a collection after applying a function. */
    def countMinBy[B](fn: A => B)(implicit cmp: Ordering[B]): Int = MathUtil.countMin(self.map(fn))
  }

  /** Implicits picking the single maximum or single minimum item in a collection. */
  implicit class WithPickMaxMinBy[A](self: Seq[A]) {

    /** Pick the maximum item in a collection after transforming the item if, and only if, there is one maximum item. */
    def pickMaxBy[B](fn: A => B)(implicit cmp: Ordering[B]): Option[A] = {
      val transformed: Seq[B] = self.map(fn)
      MathUtil.pickMax(transformed).map(transformed.indexOf).map(self)
    }

    /** Pick the minimum item in a collection after transforming the item if, and only if, there is one minimum item. */
    def pickMinBy[B](fn: A => B)(implicit cmp: Ordering[B]): Option[A] = {
      val transformed: Seq[B] = self.map(fn)
      MathUtil.pickMin(transformed).map(transformed.indexOf).map(self)
    }
  }

  /** Count the number of maximally occurring items in a collection. */
  def countMax[T](seq: Seq[T])(implicit cmp: Ordering[T]): Int = {
    seq.count(item => seq.reduceOption(cmp.max[T]).contains(item))
  }

  /** Count the number of minimally occurring items in a collection. */
  def countMin[T](seq: Seq[T])(implicit cmp: Ordering[T]): Int = {
    seq.count(item => seq.reduceOption(cmp.min[T]).contains(item))
  }

  /** Pick the maximum item in a collection if, and only if, there is one maximum item. */
  def pickMax[T](coll: Seq[T])(implicit cmp: Ordering[T]): Option[T] = {
    if (coll.count(cmp.equiv(_, coll.max)) != 1) None else Some(coll.max)
  }

  /** Pick the minimum item in a collection if, and only if, there is one minimum item. */
  def pickMin[T](coll: Seq[T])(implicit cmp: Ordering[T]): Option[T] = {
    if (coll.count(cmp.equiv(_, coll.min)) != 1) None else Some(coll.min)
  }
}
