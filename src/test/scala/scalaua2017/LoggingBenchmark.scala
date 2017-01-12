package scalaua2017

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps


import org.scalameter.api._

trait LoggingBenchmark extends Bench.OfflineReport {

  override lazy val executor = SeparateJvmsExecutor(
    new Executor.Warmer.Default,
    Aggregator.min,
    new Measurer.Default
  )
  def testLogging(system:ActorSystem)(count: Int) {
    val pingActor = system.actorOf(PingActor.props)
    implicit val timeout = Timeout(60 seconds)
    val future = pingActor ? PingActor.Start(count)
    Await.ready(future, timeout.duration)
  }

  def testLoggings(system: ActorSystem): Unit =
    performance of "Logging" in {
      measure method "interpolation" in {
        val messageCount: Gen[Int] = Gen.range("count")(3, 15, 30).map(_ * 10000)
        using(messageCount) in {
          testLogging(system)
        }
      }
    }

  val system = ActorSystem("akka-antipatterns")
  testLoggings(system)
  system.terminate()
  Await.result(system.whenTerminated, Duration.Inf)
}

object LoggingBenchmark extends LoggingBenchmark
