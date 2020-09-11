package media.state.cassandra

import java.io.File

import akka.actor.typed.ActorSystem
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object CassandraDB {
  def startCassandraDatabase(): Unit = {
    CassandraLauncher.start(
      new File("media-manage-state/target/cassandra-db"), 
      CassandraLauncher.DefaultTestConfigResource, clean = false, port = 9042)
  }

  def createTables(system: ActorSystem[_]): Unit = {
    val session =
      CassandraSessionRegistry(system).sessionFor("alpakka.cassandra")

    // TODO use real replication strategy in real application
    val keyspaceStmt =
      """
      CREATE KEYSPACE IF NOT EXISTS media_manager_state
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
      """

    val offsetTableStmt =
      """
      CREATE TABLE IF NOT EXISTS media_manager_state.offset_store (
        projection_name text,
        partition int,
        projection_key text,
        offset text,
        manifest text,
        last_updated timestamp,
        PRIMARY KEY ((projection_name, partition), projection_key)
      )
        """
    // ok to block here, main thread
    Await.ready(session.executeDDL(keyspaceStmt), 30.seconds)
    system.log.info("Created media_manager_state keyspace")
    Await.ready(session.executeDDL(offsetTableStmt), 30.seconds)
    system.log.info("Created media_manager_state.offset_store table")

  }
}
