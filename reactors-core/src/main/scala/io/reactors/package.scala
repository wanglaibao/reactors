package io



import scala.annotation.implicitNotFound



package object reactors {

  /* specialization */

  type spec = specialized

  /** Evidence value for specialized type parameters.
   *
   *  Used to artificially insert the type into a type signature.
   */
  class Spec[@spec(Int, Long, Double) T]

  implicit val intSpec = new Spec[Int]

  implicit val longSpec = new Spec[Long]

  implicit val doubleSpec = new Spec[Double]

  implicit def anySpec[T] = new Spec[T]

  /* basic abstractions */

  trait Hash[@spec(Int, Long, Double) T] {
    def apply(x: T): Int
  }

  private class IntHash extends Hash[Int] {
    def apply(x: Int) = x
  }

  implicit val intHash: Hash[Int] = new IntHash

  private class LongHash extends Hash[Long] {
    def apply(x: Long) = (x ^ (x >>> 32)).toInt
  }

  implicit val longHash: Hash[Long] = new LongHash

  private class DoubleHash extends Hash[Double] {
    def apply(x: Double) = {
      val b = java.lang.Double.doubleToLongBits(x)
      (b ^ (b >>> 32)).toInt
    }
  }

  implicit val doubleHash: Hash[Double] = new DoubleHash

  private class RefHash[T <: AnyRef] extends Hash[T] {
    def apply(x: T) = x.##
  }

  implicit def refHash[T <: AnyRef]: Hash[T] = new RefHash[T]

  /* system events */

  /** System events are a special kind of internal events that can be observed
   *  by isolates.
   */
  sealed trait SysEvent

  /** Denotes start of an isolate.
   *
   *  Produced before any other event.
   */
  case object ReactorStarted extends SysEvent

  /** Denotes the termination of an isolate.
   *
   *  Called after all other events.
   */
  case object ReactorTerminated extends SysEvent

  /** Denotes that the isolate was scheduled for execution by the scheduler.
   *
   *  Always sent after `ReactorStarted` and before `ReactorTerminated`.
   *
   *  This event usually occurs when isolate is woken up to process incoming events,
   *  but may be invoked even if there are no pending events.
   *  This event is typically used in conjunction with a scheduler that periodically
   *  wakes up the isolate.
   */
  case object ReactorScheduled extends SysEvent

  /** Denotes that the isolate was preempted by the scheduler.
   *
   *  Always sent after `ReactorStarted` and before `ReactorTerminated`.
   *
   *  When the isolate is preempted, it loses control of the execution thread, until the
   *  scheduler schedules it again on some (possibly the same) thread.
   *  This event is typically used to send another message back to the isolate,
   *  indicating that he should be scheduled again later.
   */
  case object ReactorPreempted extends SysEvent

  /** Denotes that the isolate died due to an exception.
   *
   *  This event is sent after `ReactorStarted`.
   *  This event is sent before `ReactorTerminated`, *unless* the exception is thrown
   *  while `ReactorTerminated` is being processed, in which case the `ReactorDied` is
   *  not sent.
   *
   *  Note that, if the exception is thrown during the isolate constructor invocation
   *  and before the appropriate event handler is created, this event cannot be sent
   *  to that event handler.
   *
   *  @param t              the exception that the isolate threw
   */
  case class ReactorDied(t: Throwable) extends SysEvent

  /* exceptions */

  object exception {
    def apply(obj: Any) = throw new RuntimeException(obj.toString)
    def illegalArg(msg: String) = throw new IllegalArgumentException(msg)
    def illegalState(obj: Any) = throw new IllegalStateException(obj.toString)
  }

  /** Thrown when an error in reactor execution occurs.
   */
  case class ReactorError(msg: String, cause: Throwable) extends Error(msg, cause)

  /** Thrown when an exception is propagated to an event handler, such as `onEvent`.
   */
  case class UnhandledException(cause: Throwable) extends Exception(cause)

  /** Determines if the throwable is lethal, i.e. should the program immediately stop.
   */
  def isLethal(t: Throwable): Boolean = t match {
    case e: VirtualMachineError => true
    case e: LinkageError => true
    case e: ReactorError => true
    case _ => false
  }

  /** Matches lethal throwables.
   */
  object Lethal {
    def unapply(t: Throwable): Option[Throwable] = t match {
      case e: VirtualMachineError => Some(e)
      case e: LinkageError => Some(e)
      case e: ReactorError => Some(e)
      case _ => None
    }
  }

  /** Determines if the throwable is not lethal.
   */
  def isNonLethal(t: Throwable): Boolean = !isLethal(t)

  /** Matches non-lethal throwables.
   */
  object NonLethal {
    def unapply(t: Throwable): Option[Throwable] = t match {
      case e: VirtualMachineError => None
      case e: LinkageError => None
      case e: ReactorError => None
      case _ => Some(t)
    }
  }

  /** Partial function ignores non-lethal throwables.
   *
   *  This is useful in composition with other exception handlers.
   */
  val ignoreNonLethal: PartialFunction[Throwable, Unit] = {
    case t if isNonLethal(t) => // ignore
  }

}