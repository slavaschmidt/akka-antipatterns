package scalaua2017

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

import scalaua2017.logging.{PingActor, PongActor, Start}

trait LoggingBenchmark extends TestConfig {

  override def executeTests(): Boolean = {
    val result = super.executeTests()
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
    result
  }

  private def testSingle(ping: Props, pong: Props)(count: Int)(implicit system:ActorSystem) {
    implicit val timeout = Timeout(60 seconds)
    val future = system.actorOf(ping) ? Start(system.actorOf(pong), count)
    Await.ready(future, timeout.duration)
  }

  private val testLongInterpolated = testSingle(PingActor.liprops, PongActor.props) _
  private val testLongParametrized = testSingle(PingActor.lpprops, PongActor.props) _
  private val testShortInterpolated = testSingle(PingActor.siprops, PongActor.props) _
  private val testShortParametrized = testSingle(PingActor.spprops, PongActor.props) _
  private val testLongChecking = testSingle(PingActor.lcprops, PongActor.props) _
  private val testScalaLogging = testSingle(PingActor.slprops, PongActor.props) _

  def compareLogging(implicit system: ActorSystem): Unit =
    performance of "Logging" in {
      measure method "complex interpolation" in {
        using(messageCount) in { testLongInterpolated }
      }
      measure method "complex parametrization" in {
        using(messageCount) in { testLongParametrized }
      }
      measure method "complex checking" in {
        using(messageCount) in { testLongChecking }
      }
      measure method "simple interpolation" in {
        using(messageCount) in { testShortInterpolated }
      }
      measure method "simple parametrization" in {
        using(messageCount) in { testShortParametrized }
      }
      measure method "scala logging" in {
        using(messageCount) in { testScalaLogging }
      }
    }

  compareLogging
}

object LoggingBenchmark extends ConnectionPoolBenchmark
