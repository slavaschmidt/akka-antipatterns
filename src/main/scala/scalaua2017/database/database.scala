package scalaua2017

import java.sql.Connection

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.routing.RoundRobinPool

import scala.util.Random

object database {

  val Config = new Config("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/pv2020?useSSL=false", "root","")

  trait DbWriter extends ConnectionFactory {
    def write(): Unit = newConnection(ConnectionIdentifier(Random.nextLong().toString)) map { conn =>
      val str = Random.nextString(255)
      val time = Random.nextInt()
      val st = conn.prepareStatement(s"insert into conn_test (str, time) values (?, ?)")
      st.setString(1, str)
      st.setInt(2, time)
      st.execute()
      st.close()
      conn.close()
    }
  }

  trait PongActor extends Actor with ActorLogging  with DbWriter {
    def receive: Receive = {
      case Ping =>
        write()
        sender() ! Pong
    }
  }

  class PingActor extends Actor with ActorLogging {
    var count = 0
    def waiter(initiator: ActorRef, countTo: Int): Receive = {
      case Pong =>
        count += 1
        if (count >= countTo) {
          initiator ! Done(count)
          self ! PoisonPill
        }
    }

    def receive: Receive = initialize

    def initialize: Receive = {
      case Start(props: Props, count: Int) =>
        for (i <- 0 to count) {
          val peer = context.actorOf(props)
          peer ! Ping
          peer ! PoisonPill
        }
        context.become(waiter(sender(), count))

    }
  }
  class RoutingPingActor extends  PingActor {
    override def initialize: Receive = {
      case Start(props: Props, count: Int) =>
        val peer = context.actorOf(props)
        for (i <- 0 to count) peer ! Ping
        context.become(waiter(sender(), count))
    }
  }

  class NoPoolPingActor extends PongActor {
    override def newConnection(name: ConnectionIdentifier): Option[Connection] = NoConnectionPool.newConnection(name)
  }
  class LiftPingActor extends PongActor {
    override def newConnection(name: ConnectionIdentifier): Option[Connection] = LiftConnectionPool .newConnection(name)
  }
  class BoneCpPingActor extends PongActor {
    override def newConnection(name: ConnectionIdentifier): Option[Connection] = ExternalConnectionPool.newConnection(name)
  }

  object PongActor {
    val nprops: Props = Props[NoPoolPingActor].withDispatcher("akka.actor.db-worker-dispatcher")
    val lprops: Props = Props[LiftPingActor].withDispatcher("akka.actor.db-worker-dispatcher")
    val bprops: Props = Props[BoneCpPingActor].withDispatcher("akka.actor.db-worker-dispatcher")
    val rprops: Props = Props[BoneCpPingActor].withDispatcher("akka.actor.db-worker-dispatcher").withRouter(RoundRobinPool(2))
  }
  object PingActor {
    val props: Props = Props[PingActor]
    val rprops: Props = Props[RoutingPingActor]
  }

  case class Start(peer: Props, count: Int)
  case class Done(count: Int)
  case object Ping
  case object Pong

}


