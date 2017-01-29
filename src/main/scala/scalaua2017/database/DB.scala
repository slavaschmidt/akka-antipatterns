package scalaua2017

import java.sql.{Connection, DriverManager}

import com.jolbox.bonecp.{BoneCP, BoneCPConfig}

import scala.util.Try

case class ConnectionIdentifier(name: String)

case class Config(driver: String, url: String, user: String, pass: String)

trait ConnectionFactory {
  def newConnection(name: ConnectionIdentifier): Option[Connection]
}

trait DriverConnection {
  def cfg: Config = scalaua2017.database.Config
  Class.forName(cfg.driver)
  def createOne(name: ConnectionIdentifier): Option[Connection] =
    Try { DriverManager.getConnection(cfg.url, cfg.user, cfg.pass) }.toOption
}

object NoConnectionPool extends DriverConnection with ConnectionFactory {
  override def newConnection(name: ConnectionIdentifier): Option[Connection] = createOne(name)
}


object ExternalConnectionPool extends DriverConnection with ConnectionFactory {

  private object ConnectionPool {
    val minConnectionsPerPartition  = 10
    val maxConnectionsPerPartition  = 25
    val partitionCount              = 4
    val closeConnectionWatch        = false
    val queryExecuteTimeLimit       = 60000
    val closeConnectionWatchTimeout = 30000
    val disableConnectionTracking   = true

    val config = new BoneCPConfig
    config.setJdbcUrl(cfg.url)
    config.setUsername(cfg.user)
    config.setPassword(cfg.pass)
    config.setMinConnectionsPerPartition(minConnectionsPerPartition)
    config.setMaxConnectionsPerPartition(maxConnectionsPerPartition)
    config.setPartitionCount(partitionCount)
    config.setCloseConnectionWatch(closeConnectionWatch)
    config.setQueryExecuteTimeLimitInMs(queryExecuteTimeLimit)
    config.setCloseConnectionWatchTimeoutInMs(closeConnectionWatchTimeout)
    config.setDisableConnectionTracking(disableConnectionTracking)
    val pool = new BoneCP(config)
    Runtime.getRuntime.addShutdownHook(new Thread() { override def run() { pool.shutdown() } })
  }

  Class.forName(cfg.driver)

  def newConnection(name: ConnectionIdentifier): Option[Connection] = Option(ConnectionPool.pool.getConnection)

}

object LiftConnectionPool extends DriverConnection with ConnectionFactory {
  private var pool: List[Connection] = Nil
  private var poolSize = 0
  private var tempMaxSize = maxPoolSize
  Class.forName(cfg.driver)

  /**
    * Override and set to false if the maximum pool size can temporarilly be expanded to avoid pool starvation
    */
  protected def allowTemporaryPoolExpansion = true

  /**
    *  Override this method if you want something other than
    * 4 connections in the pool
    */
  protected def maxPoolSize = 4

  /**
    * The absolute maximum that this pool can extend to
    * The default is 20.  Override this method to change.
    */
  protected def doNotExpandBeyond = 200

  /**
    * The logic for whether we can expand the pool beyond the current size.  By
    * default, the logic tests allowTemporaryPoolExpansion &amp;&amp; poolSize &lt;= doNotExpandBeyond
    */
  protected def canExpand_? : Boolean = allowTemporaryPoolExpansion && poolSize <= doNotExpandBeyond

  /**
    *   How is a connection created?
    */
  def createOne: Option[Connection] =
    Try { DriverManager.getConnection(cfg.url, cfg.user, cfg.pass) }.toOption

  /**
    * Test the connection.  By default, setAutoCommit(false),
    * but you can do a real query on your RDBMS to see if the connection is alive
    */
  protected def testConnection(conn: Connection) {
    conn.setAutoCommit(false)
  }

  def newConnection(name: ConnectionIdentifier): Option[Connection] =
    synchronized {
      pool match {
        case Nil if poolSize < tempMaxSize =>
          val ret = createOne
          ret.foreach(_.setAutoCommit(false))
          poolSize = poolSize + 1
          ret

        case Nil =>
          val curSize = poolSize
          wait(50L)
          // if we've waited 50 ms and the pool is still empty, temporarily expand it
          if (pool.isEmpty && poolSize == curSize && canExpand_?) {
            tempMaxSize += 1
          }
          newConnection(name)

        case x :: xs =>
          pool = xs
          try {
            this.testConnection(x)
            Option(x)
          } catch {
            case e: Exception => try {
              poolSize = poolSize - 1
              x.close()
              newConnection(name)
            } catch {
              case e: Exception => newConnection(name)
            }
          }
      }
    }

  def releaseConnection(conn: Connection): Unit = synchronized {
    if (tempMaxSize > maxPoolSize) {
      conn.close()
      tempMaxSize -= 1
      poolSize -= 1
    } else {
      pool = conn :: pool
    }
    notifyAll()
  }

  def closeAllConnections_!(): Unit = synchronized {
    if (poolSize == 0) ()
    else {
      pool.foreach {c => c.close(); poolSize -= 1}
      pool = Nil

      if (poolSize > 0) wait(250)

      closeAllConnections_!()
    }
  }
}

