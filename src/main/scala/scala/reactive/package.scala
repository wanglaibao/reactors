package scala



import scala.annotation.implicitNotFound
import scala.reflect.ClassTag



package object reactive {

  type spec = specialized

  type XY = Long

  implicit class XYExtensions(val v: XY) extends AnyVal {
    final def x = XY.xOf(v)
    final def y = XY.yOf(v)

    override def toString = "XY(%d, %d)".format(x, y)
  }

  object XY {
    final def xOf(v: Long) = (v & 0xffffffff).toInt
    final def yOf(v: Long) = (v >>> 32).toInt
    final def apply(x: Int, y: Int): XY = value(x, y)
    final def value(x: Int, y: Int): Long = (y.toLong << 32) | (x.toLong & 0xffffffffL)
    final def invalid = (Int.MinValue.toLong << 32) | ((Int.MinValue >>> 1).toLong << 1)
    final def add(thiz: XY, that: XY) = XY(thiz.x + that.x, thiz.y + that.y)
    final def diff(thiz: XY, that: XY) = XY(thiz.x - that.x, thiz.y - that.y)
    final def mult(thiz: XY, v: Int) = XY(thiz.x * v, thiz.y * v)
  }

  type SubscriptionSet = container.SubscriptionSet

  val SubscriptionSet = container.SubscriptionSet

  type RCell[T] = container.RCell[T]

  val RCell = container.RCell

  type ReactRecord = container.ReactRecord

  val ReactRecord = container.ReactRecord

  type RContainer[T] = container.RContainer[T]

  val RContainer = container.RContainer

  type RBuilder[T, Repr] = container.RBuilder[T, Repr]

  val RBuilder = container.RBuilder

  type RArray[T] = container.RArray[T]

  val RArray = container.RArray

  type RQueue[T] = container.RQueue[T]

  val RQueue = container.RQueue

  type RUnrolledQueue[T] = container.RUnrolledQueue[T]

  val RUnrolledQueue = container.RUnrolledQueue

  type RPriorityQueue[T] = container.RPriorityQueue[T]

  val RPriorityQueue = container.RPriorityQueue

  type RBinaryHeap[T] = container.RBinaryHeap[T]

  val RBinaryHeap = container.RBinaryHeap

  type RHashSet[T] = container.RHashSet[T]

  val RHashSet = container.RHashSet

  type RHashValMap[K, V] = container.RHashValMap[K, V]

  val RHashValMap = container.RHashValMap

  type RHashMap[K, V >: Null <: AnyRef] = container.RHashMap[K, V]

  val RHashMap = container.RHashMap

  type RCatamorph[T, S] = container.RCatamorph[T, S]

  val RCatamorph = container.RCatamorph

  type MonoidCatamorph[T, S] = container.MonoidCatamorph[T, S]

  val MonoidCatamorph = container.MonoidCatamorph

  type CommuteCatamorph[T, S] = container.CommuteCatamorph[T, S]

  val CommuteCatamorph = container.CommuteCatamorph

  type AbelianCatamorph[T, S] = container.AbelianCatamorph[T, S]

  val AbelianCatamorph = container.AbelianCatamorph

  type RTileMap[T] = container.RTileMap[T]

  val RTileMap = container.RTileMap

  type SignalCatamorph[T] = container.SignalCatamorph[T]

  val SignalCatamorph = container.SignalCatamorph

  /* random */

  type ReactRandom = calc.ReactRandom

  /* calculations */

  type Monoid[T] = calc.Monoid[T]

  val Monoid = calc.Monoid

  type Commutoid[T] = calc.Commutoid[T]

  val Commutoid = calc.Commutoid

  type Abelian[T] = calc.Abelian[T]

  val Abelian = calc.Abelian

  trait Foreach[@spec(Int, Long, Double) T] {
    def foreach[@spec(Int, Long, Double) U](f: T => U): Unit
  }

  private[reactive] def nextPow2(num: Int): Int = {
    var v = num - 1
    v |= v >> 1
    v |= v >> 2
    v |= v >> 4
    v |= v >> 8
    v |= v >> 16
    v + 1
  }

  /** An implicit value that permits invoking operations that
   *  can buffer events unboundedly.
   * 
   *  Users that are sure their events will not be buffered forever
   *  should import `Permission.canBuffer`.
   */
  @implicitNotFound("This operation can buffer events, and may consume an arbitrary amount of memory. " +
    "Import the value `Permission.canBuffer` to allow buffering operations.")
  sealed trait CanBeBuffered

  /** Explicitly importing this object permits calling various methods.
   */
  object Permission {
    /** Importing this value permits calling reactive combinators
     *  that can potentially unboundedly buffer events.
     */
    implicit val canBuffer = new CanBeBuffered {}
  }

  /* extensions on tuples */

  class Tuple2Extensions[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](val tuple: (Signal[T], Signal[S])) {
    def mutate[M <: ReactMutable](mutable: M)(f: (T, S) => Unit) = {
      val s = new Tuple2Extensions.Mutate(tuple, mutable, f)
      s.subscription = Reactive.CompositeSubscription(
        tuple._1.observe(s.m1),
        tuple._2.observe(s.m2)
      )
      s
    }
  }

  implicit def tuple2ext[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](tuple: (Signal[T], Signal[S])) = new Tuple2Extensions[T, S](tuple)

  object Tuple2Extensions {
    class Mutate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, M <: ReactMutable](val tuple: (Signal[T], Signal[S]), val mutable: M, val f: (T, S) => Unit)
    extends Reactive.ProxySubscription {
      val m1 = new Reactor[T] {
        def react(value: T) {
          f(tuple._1(), tuple._2())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      val m2 = new Reactor[S] {
        def react(value: S) {
          f(tuple._1(), tuple._2())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      var subscription = Reactive.Subscription.empty
    }
  }

  class Tuple3Extensions[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S,  @spec(Int, Long, Double) U](val tuple: (Signal[T], Signal[S], Signal[U])) {
    def mutate[M <: ReactMutable](mutable: M)(f: (T, S, U) => Unit) = {
      val s = new Tuple3Extensions.Mutate(tuple, mutable, f)
      s.subscription = Reactive.CompositeSubscription(
        tuple._1.observe(s.m1),
        tuple._2.observe(s.m2),
        tuple._3.observe(s.m3)
      )
      s
    }
  }

  implicit def tuple3ext[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S,  @spec(Int, Long, Double) U](tuple: (Signal[T], Signal[S], Signal[U])) = new Tuple3Extensions[T, S, U](tuple)

  object Tuple3Extensions {
    class Mutate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, @spec(Int, Long, Double) U, M <: ReactMutable](val tuple: (Signal[T], Signal[S], Signal[U]), val mutable: M, val f: (T, S, U) => Unit)
    extends Reactive.ProxySubscription {
      val m1 = new Reactor[T] {
        def react(value: T) {
          f(tuple._1(), tuple._2(), tuple._3())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      val m2 = new Reactor[S] {
        def react(value: S) {
          f(tuple._1(), tuple._2(), tuple._3())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      val m3 = new Reactor[U] {
        def react(value: U) {
          f(tuple._1(), tuple._2(), tuple._3())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      var subscription = Reactive.Subscription.empty
    }
  }

  /* system events */

  /** Internal events are used by the isolate system to communicate with different isolates.
   */
  sealed trait InternalEvent

  /** Denotes that a channel corresponding to the specified request identifier
   *  has been found.
   */
  case class ChannelRetrieved(reqId: Long, channel: Channel[_]) extends InternalEvent

  /** System events are a special kind of internal events that can be observed
   *  by isolates.
   */
  sealed trait SysEvent extends InternalEvent

  /** Denotes start of an isolate.
   *
   *  Produced before any other event.
   */
  case object IsoStarted extends SysEvent

  /** Denotes the termination of an isolate.
   *
   *  Called after all other events.
   */
  case object IsoTerminated extends SysEvent

  /** Denotes that all the events were processed
   *  and the queues became empty.
   */
  case object IsoEmptyQueue extends SysEvent

  /* exceptions */

  object exception {
    def apply(obj: Any) = throw new RuntimeException(obj.toString)
    def illegalArg(msg: String) = throw new IllegalArgumentException(msg)
    def illegalState(obj: Any) = throw new IllegalStateException(obj.toString)
  }

}


