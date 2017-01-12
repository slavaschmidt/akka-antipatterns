package scalaua2017

import org.scalameter.api._

object TestSuite extends Bench.Group {
  override def persistor = Persistor.None

  include(new LoggingBenchmark() {})

}
