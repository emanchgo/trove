/*
 *  # Trove
 *
 *  This file is part of Trove - A FREE desktop budgeting application that
 *  helps you track your finances, FREES you from complex budgeting, and
 *  enables you to build your TROVE of savings!
 *
 *  Copyright © 2016-2018 Eric John Fredericks.
 *
 *  Trove is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Trove is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Trove.  If not, see <http://www.gnu.org/licenses/>.
 */

package trove.core.persist

import java.io.File

import grizzled.slf4j.Logging
import slick.jdbc.SQLiteProfile.api._
import slick.jdbc.{DriverDataSource, SQLiteProfile}
import slick.util.ClassLoaderUtil
import trove.constants.ProjectsHomeDir
import trove.exceptional.PersistenceError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private[core] object PersistenceManager extends Logging {

  import SQLiteProfile.backend._

  val JdbcPrefix = "jdbc:sqlite:"
  val DbFilenameSuffix: String = ".sqlite3"

  // The current active project
  @volatile private[this] var currentProject: Option[Project] = None

  // Persistence resources for a project
  private[persist] case class Project(name: String, lock: ProjectLock, db: DatabaseDef) {

    private[this] val shutdownHook = new Thread() {
      override def run(): Unit = {
        logger.warn(s"Shutdown hook executing for project $name")
        db.close()
        // lock does its own shutdown hook, but just to be sure
        releaseLock(lock)
      }
    }

    def close(): Unit = {
      db.close()
      logger.debug(s"Database for project $name closed")
      releaseLock(lock)
      logger.debug(s"Lock for project $name released")
      Runtime.getRuntime.removeShutdownHook(shutdownHook)
      logger.debug(s"Database shutdown hook removed for project $name")
      logger.info(s"Closed project $name")
    }

    Runtime.getRuntime.addShutdownHook(shutdownHook)
  }

  def listProjectNames: Seq[String] =
    ProjectsHomeDir.listFiles.filter(_.isFile).map(_.getName).filterNot(_.endsWith(ProjectLock.lockfileSuffix)).filterNot(_.startsWith("."))
      .map(_.stripSuffix(DbFilenameSuffix)).toSeq.sorted

  def openProject(projectName: String): Try[Project] = {
    logger.debug(s"Opening project: $projectName")

    val projectLock: ProjectLock = ProjectLock(projectName)
    val lockResult = projectLock.lock()

    val openResult: Try[(DatabaseDef, Boolean)] = lockResult.map { _ =>
      val dbFileName = s"$projectName$DbFilenameSuffix"
      val dbFile = new File(ProjectsHomeDir, dbFileName)
      val create: Boolean = !dbFile.exists()
      val dbURL = s"$JdbcPrefix${dbFile.getAbsolutePath}"
      val db: DatabaseDef = openDatabase(dbURL)
      (db, create)
    }

    val projectResult: Try[Project] = openResult.flatMap {
      case (db, create) =>
        val setupResult: Future[Unit] = if (create) {
          db.run(Tables.setupAction)
        }
        else {
          Future.successful(())
        }

        val versionCheckResult: Future[Try[Project]] = setupResult.flatMap { _ =>
          db.run(Tables.version.result).map {
            case rows if rows.length == 1 && rows.head == Tables.CurrentDbVersion =>
              val prj = new Project(projectName, projectLock, db)
              Success(prj)
            case rows if rows.length == 1 => PersistenceError(s"Invalid database version: ${rows.head.id}")
            case rows => PersistenceError(s"Incorrect number of rows in the VERSION table: found ${rows.size} rows")
          }
        }

        Await.result(versionCheckResult, Duration.Inf)
    }

    projectResult match {
      case Success(prj) =>
        currentProject = projectResult.toOption
        logger.info(s"Project opened: ${prj.name}")
      case Failure(e) =>
        logger.error("Error creating project", e)
        releaseLock(projectLock)
    }

    projectResult
  }

  def closeCurrentProject: Try[Unit] = currentProject.fold[Try[Unit]](Success(())) { project: Project =>
    Try(project.close()).map { _ =>
      currentProject = None
    }.recoverWith {
      case NonFatal(e) =>
        logger.error("Error closing project")
        Failure(e)
    }
  }

  private[this] def openDatabase(dbURL: String): DatabaseDef = {
    val dds = new DriverDataSource(
      url = dbURL,
      user = null,
      password = null,
      properties = null,
      driverClassName = "org.sqlite.JDBC",
      classLoader = ClassLoaderUtil.defaultClassLoader)

    // N.B. We only need a single thread, so we pass 1 here.
    val numWorkers = 1

    val executor = AsyncExecutor(
      name = "AsyncExecutor.trove",
      minThreads = numWorkers,
      maxThreads = numWorkers,
      queueSize = numWorkers,
      maxConnections = numWorkers,
      keepAliveTime = 1.minute,
      registerMbeans = false
    )

    Database.forDataSource(
      ds = dds,
      maxConnections = Some(numWorkers),
      executor = executor,
      keepAliveConnection = false
    )
  }

  private[this] def releaseLock(lock: ProjectLock): Unit =
    lock.release().recover {
      case NonFatal(ex) => logger.error("Error releasing project lock", ex)
    }
}
