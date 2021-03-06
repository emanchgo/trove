/*
 *  # Trove
 *
 *  This file is part of Trove - A FREE desktop budgeting application that
 *  helps you track your finances, FREES you from complex budgeting, and
 *  enables you to build your TROVE of savings!
 *
 *  Copyright © 2016-2021 Eric John Fredericks.
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

package trove.core.infrastructure.persist.dao

import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.SQLiteProfile.backend._
import trove.exceptional.PersistenceError

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.reflect.runtime.universe.TypeTag
import scala.util.Try
import scala.util.control.NonFatal

private[dao] trait DbRunOp[+S] {
  def run[R >: Seq[S] : TypeTag](act: DBIOAction[R, NoStream, Nothing]): Try[R]
}

private[dao] trait DbExec[+S] { self: DbRunOp[S] =>
  // Call this method
  def exec[R >: Seq[S] : TypeTag](action: DBIOAction[R, NoStream, Nothing]): Try[R] =
    self.run(action).recoverWith {
      case NonFatal(e) =>
        PersistenceError("Database execution error", e)
    }
}

private[dao] trait LiveDbRunOp[+S] extends DbRunOp[S] {
  protected def db: DatabaseDef
  override def run[R >: Seq[S] : TypeTag](action: DBIOAction[R, NoStream, Nothing]): Try[R] = Try(Await.result(db.run(action), Duration.Inf))
}
