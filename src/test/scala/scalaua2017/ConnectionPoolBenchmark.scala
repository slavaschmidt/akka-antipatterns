package scalaua2017

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import org.scalameter.api._
import org.scalameter.picklers.Implicits._

import scalaua2017.database._

trait ConnectionPoolBenchmark extends TestConfig {

  override def executeTests(): Boolean = {
    val result = super.executeTests()
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
    result
  }

  private def testSingle(pong: Props, ping: Props)(count: Int)(implicit system:ActorSystem) {
    implicit val timeout = Timeout(600 seconds)
    val actor = system.actorOf(ping)
    val future = actor ? Start(pong, count)
    Await.ready(future, timeout.duration)
    actor ! PoisonPill
  }

  private val testNoPool = testSingle(PongActor.nprops, PingActor.props) _
  private val testLiftPool = testSingle(PongActor.lprops, PingActor.props) _
  private val testBoneCpPool = testSingle(PongActor.bprops, PingActor.props) _
  private val testWithRouter = testSingle(PongActor.rprops, PingActor.rprops) _

  def compareLogging(implicit system: ActorSystem): Unit =
    performance of "Database Connection Pool" in {
/*      measure method "without connection pool" in {
        using(messageCount) in { testNoPool }
      }
      measure method "with bad behaving pool" in {
        using(messageCount) in { testLiftPool }
      }
      measure method "with good behaving pool" in {
        using(messageCount) in { testBoneCpPool }
      }
      */
      measure method "with actor based pool and router" in {
        using(messageCount) in { testWithRouter }
      }
    }

  compareLogging
}

object ConnectionPoolBenchmark extends ConnectionPoolBenchmark
