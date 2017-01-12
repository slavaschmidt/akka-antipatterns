package scalaua2017

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

object logging {

  trait TestLogger {
    def writeLog(msg: String, text: String): Unit
  }
  trait InterpolatingTestLogger extends TestLogger with ActorLogging with Actor {
    override def writeLog(msg: String, text: String): Unit = log.debug(s"$msg $text")
  }

  trait TemplatingTestLogger extends TestLogger with ActorLogging with Actor {
    override def writeLog(msg: String, text: String): Unit = log.debug(msg, text)
  }

  trait PingActor extends Actor with ActorLogging with TestLogger {

    import PingActor._

    private val pongActor = context.actorOf(PongActor.props, "pongActor")
    var initiator: Option[ActorRef] = None
    var counter = 0
    var countTo = 0

    def receive: Receive = {
      case Start(pongActor: ActorRef, count: Int) =>
        countTo = count
        initiator = Some(sender())
        pongActor ! Ping("ping")
      case PongActor.PongMessage(text) if initiator.isDefined =>
        writeLog("In PingActor - received message: {}", text)
        counter += 1
        if (counter >= countTo) initiator.get ! Done(counter)
        else sender() ! Ping("ping")
    }
  }

  object PingActor {
    val props: Props = Props[PingActor]
    case class Start(peer: ActorRef, count: Int)
    case class Done(count: Int)
    case class Ping(text: String)
  }

  trait PongActor extends Actor with ActorLogging with TestLogger {
    import PongActor._

    def receive: Receive = {
      case PingActor.Ping(text) =>
        writeLog("In PongActor - received message: {}", text)
        sender() ! PongMessage("pong")
    }
  }

  object PongActor {
    val props: Props = Props[PongActor]
    case class PongMessage(text: String)
  }

}
