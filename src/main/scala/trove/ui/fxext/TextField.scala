/*
 *  # Trove
 *
 *  This file is part of Trove - A FREE desktop budgeting application that
 *  helps you track your finances, FREES you from complex budgeting, and
 *  enables you to build your TROVE of savings!
 *
 *  Copyright © 2016-2019 Eric John Fredericks.
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

package trove.ui.fxext

import scalafx.scene.control.Label

object TextField {
  def apply(metadata: FieldMetadata): TextField = new TextField(metadata)
}

class TextField(metadata: FieldMetadata) extends scalafx.scene.control.TextField {
  val maxLength: Int = metadata.maxLength.getOrElse(Int.MaxValue)
  require(maxLength > 0)

  val label: Label = Label(metadata.name)

  text.onChange {
    (_,oldValue,newValue) => {
      if(newValue.length > oldValue.length && newValue.length > maxLength) {
        text = newValue.substring(0, maxLength)
      }
    }
  }
}
