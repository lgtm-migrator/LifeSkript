/*
 *
 *     This file is part of Skript.
 *
 *    Skript is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    Skript is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with Skript. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 *   Copyright 2011-2019 Peter Güttinger and contributors
 *
 */

@file:JvmName("ResolverTrackerAgent")
package ch.njol.skript.agents.defaults

import ch.njol.skript.agents.TrackerAgent

class ResolverTrackerAgent : TrackerAgent {
    /**
     * Registers this tracker.
     *
     * @return This tracker, useful
     * for chaining.
     */
    override fun registerTracker(): ResolverTrackerAgent {
        return this
    }

    /**
     * Unregisters this tracker.
     *
     * @return This tracker, useful
     * for chaining.
     */
    override fun unregisterTracker(): ResolverTrackerAgent {
        return this
    }
}
