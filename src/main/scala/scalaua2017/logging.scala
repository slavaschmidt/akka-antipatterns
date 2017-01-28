package scalaua2017

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

object logging {

  trait TestLogger {
    var times: List[Long]
    def writeLog(): Unit
  }
  trait LongInterpolatingTestLogger extends TestLogger with ActorLogging with Actor {
    override def writeLog(): Unit =
      log.debug(s"Current message times: ${times.mkString(",")}")
  }
  trait ShortInterpolatingTestLogger extends TestLogger with ActorLogging with Actor {
    override def writeLog(): Unit =
      log.debug(s"Current message size: ${times.size}")
  }
  trait LongParametrizedTestLogger extends TestLogger with ActorLogging with Actor {
    override def writeLog(): Unit =
      log.debug(s"Current message times: {}", times.mkString(","))
  }
  trait ShortParametrizedTestLogger extends TestLogger with ActorLogging with Actor {
    override def writeLog(): Unit = {
      log.debug(s"Current message size: {}", times.size)
    }
  }
  trait LongCheckingTestLogger extends TestLogger with ActorLogging with Actor {
    override def writeLog(): Unit =
      if (log.isDebugEnabled) log.debug(s"Current message times: {}", times.mkString(","))
  }

  class PongActor extends Actor with ActorLogging {
    def receive: Receive = {
      case Ping =>
        sender() ! Pong
    }
  }

  trait PingActor extends Actor with ActorLogging with TestLogger {
    override var times: List[Long] = Nil
    def player(initiator: ActorRef, peer: ActorRef, countTo: Int): Receive = {
      case Pong =>
        writeLog()
        times = System.currentTimeMillis() :: times
        if (times.size >= countTo) initiator ! Done(times.size)
        else peer ! Ping
    }

    def receive: Receive = initialize

    def initialize: Receive = {
      case Start(peer: ActorRef, count: Int) =>
        context.become(player(sender(), peer, count))
        peer ! Ping
    }
  }

  class LIPingActor extends PingActor with LongInterpolatingTestLogger
  class LPPingActor extends PingActor with LongParametrizedTestLogger
  class LCPingActor extends PingActor with LongCheckingTestLogger
  class SIPingActor extends PingActor with ShortInterpolatingTestLogger
  class SPPingActor extends PingActor with ShortParametrizedTestLogger

  object PingActor {
    val liprops: Props = Props[LIPingActor]
    val lpprops: Props = Props[LPPingActor]
    val lcprops: Props = Props[LCPingActor]
    val siprops: Props = Props[SIPingActor]
    val spprops: Props = Props[SPPingActor]
  }
  object PongActor {
    val props: Props = Props[PongActor]
  }

  case class Start(peer: ActorRef, count: Int)
  case class Done(count: Int)
  case object Ping
  case object Pong

}
