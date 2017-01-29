package scalaua2017

import akka.actor.ActorSystem
import org.scalameter.api._
import org.scalameter.picklers.Implicits._

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

object TestSuite extends Bench.Group {
  override def persistor = Persistor.None

  include(new ConnectionPoolBenchmark {})
  include(new LoggingBenchmark {})

}
