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

package trove.core

import akka.actor.ActorSystem
import grizzled.slf4j.Logging
import trove.core.infrastructure.event.EventService
import trove.core.infrastructure.persist.ProjectPersistenceService
import trove.services.ProjectService

import scala.util.{Success, Try}

object Trove extends Logging {

  private[this] lazy val actorSystem: ActorSystem = ActorSystem("Trove_Actor_System")

  lazy val eventService: EventService = EventService(actorSystem)
  lazy val projectService: ProjectService = ProjectPersistenceService()

  def startup(): Try[Unit] = {
    actorSystem
    eventService
    projectService
    Success(Unit)
  }

  def shutdown(): Try[Unit] = projectService.closeCurrentProject().flatMap { _ =>
    Try(eventService.shutdown()).map(_ =>
        Unit)
  }
}
