package scalaua2017

import java.sql.{Connection, PreparedStatement}

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.routing.RoundRobinPool

import scala.util.Random

object database {

  val Config = new Config("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/pv2020?useSSL=false", "root","")

  trait DbWriter extends ConnectionFactory {
    def statement: Option[PreparedStatement] = None
    def close(connection: Connection): Unit = connection.close()
    def connection: Option[Connection] = newConnection(ConnectionIdentifier(Random.nextLong().toString))
    def write(): Unit = connection map { conn =>
      val st = statement.getOrElse(conn.prepareStatement(s"insert into conn_test (str, time) values (?, ?)"))
      st.setString(1, Random.nextString(255))
      st.setInt(2, Random.nextInt())
      st.execute()
      if (statement.isEmpty) st.close()
      close(conn)
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

  class RoutingPingActor extends PingActor {
    override def initialize: Receive = {
      case Start(props: Props, count: Int) =>
        val peer = context.actorOf(props)
        for (i <- 0 to count) peer ! Ping
        context.become(waiter(sender(), count))
    }
  }

  trait PreparedPongActor extends PongActor {
    override val connection: Option[Connection] = newConnection(ConnectionIdentifier(Random.nextLong().toString))
    override def close(connection: Connection) = Unit
    override val statement: Option[PreparedStatement] =
       connection map { conn =>
        conn.prepareStatement(s"insert into conn_test (str, time) values (?, ?)")
      }
  }

  class NoPoolPongActor extends PongActor {
    override def newConnection(name: ConnectionIdentifier): Option[Connection] =
      NoConnectionPool.newConnection(name)
  }
  class LiftPongActor extends PongActor {
    override def newConnection(name: ConnectionIdentifier): Option[Connection] = LiftConnectionPool .newConnection(name)
  }
  class BoneCpPongActor extends PongActor {
    override def newConnection(name: ConnectionIdentifier): Option[Connection] = ExternalConnectionPool.newConnection(name)
  }
  class RoutingPongActor extends PreparedPongActor {
    override def newConnection(name: ConnectionIdentifier): Option[Connection] = ExternalConnectionPool.newConnection(name)
  }

  object PongActor {
    val nprops: Props = Props[NoPoolPongActor].withDispatcher("akka.actor.db-worker-dispatcher")
    val lprops: Props = Props[LiftPongActor].withDispatcher("akka.actor.db-worker-dispatcher")
    val bprops: Props = Props[BoneCpPongActor].withDispatcher("akka.actor.db-worker-dispatcher")
    val rprops: Props = Props[RoutingPongActor].withDispatcher("akka.actor.db-small-dispatcher")
      .withRouter(RoundRobinPool(10))
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


