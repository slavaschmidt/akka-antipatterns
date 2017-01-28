package scalaua2017

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import org.scalameter.api._
import org.scalameter.picklers.Implicits._

import scalaua2017.logging.{PingActor, PongActor, Start}

class TestConfig extends Bench.OfflineReport {
  override def persistor = Persistor.None

  override lazy val executor = LocalExecutor(
    new Executor.Warmer.Default,
    Aggregator.min,
    new Measurer.Default
  )
  val messageCount: Gen[Int] = Gen.single("count")(1000)
  implicit val system = ActorSystem("akka-anti-patterns")
}

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
    }

  compareLogging
}

object LoggingBenchmark extends LoggingBenchmark
