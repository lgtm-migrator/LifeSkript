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

package ch.njol.skript.effects;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.SkriptConfig;
import ch.njol.skript.agents.SkriptAgent;
import ch.njol.skript.agents.events.end.DelayEndEvent;
import ch.njol.skript.agents.events.start.DelayStartEvent;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.function.FunctionEvent;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Peter Güttinger
 */
@Name("Delay")
@Description("Delays the script's execution by a given timespan. Please note that delays are not persistent, e.g. trying to create a tempban script with <code>ban player → wait 7 days → unban player</code> will not work if you restart your server anytime within these 7 days. You also have to be careful even when using small delays!")
@Examples({"wait 2 minutes", "halt for 5 minecraft hours", "wait a tick"})
@Since("1.4")
public final class Delay extends Effect {
    @SuppressWarnings("null")
    static final Set<Event> delayed = Collections.newSetFromMap(new WeakHashMap<>());

    static {
        Skript.registerEffect(Delay.class, "(wait|halt) [for] %timespan%");
    }

    @SuppressWarnings("null")
    Expression<Timespan> duration;

    public static boolean isDelayed(final Event e) {
        return delayed.contains(e);
    }

    public static final void addDelayedEvent(final Event event) {
        delayed.add(event);
    }

    @SuppressWarnings({"unchecked", "null"})
    @Override
    public boolean init(final Expression<?>[] exprs, final int matchedPattern, final Kleenean isDelayed, final ParseResult parseResult) {
        duration = (Expression<Timespan>) exprs[0];
        if (duration instanceof Literal) {
            final long millis = ((Literal<Timespan>) duration).getSingle().getMilliSeconds();
            if (millis > 86400000L && !SkriptConfig.disableTooLongDelayWarnings.value())
                Skript.warning("Delays greater than one day are not persistent, please use variables to store date and calculate difference instead.");
        }
        if (ScriptLoader.isCurrentEvent(FunctionEvent.class) && !SkriptConfig.disableDelaysInFunctionsWarnings.value())
            Skript.warning("Delays in functions causes function to return instantly, this may cause bugs, so don't use a delay in functions.");
        return true;
    }

    @Override
    @Nullable
    protected TriggerItem walk(final Event e) {
        debug(e, true);
        final Timespan duration = this.duration.getSingle(e);
        if (duration == null)
            return null;
        final TriggerItem next = getNext();
        if (next != null) {
            delayed.add(e);
            final boolean trackingEnabled = SkriptAgent.isTrackingEnabled();
            final long start = Skript.debug() ? System.nanoTime() : 0L;
            if (trackingEnabled)
                SkriptAgent.throwEvent(new DelayStartEvent(duration));
            Bukkit.getScheduler().scheduleSyncDelayedTask(Skript.getInstance(), () -> {
                if (Skript.debug())
                    Skript.info(getIndentation() + " ... continuing after " + (System.nanoTime() - start) / 1000000000. + "s");
                final long startTime = trackingEnabled ? System.nanoTime() : 0L;
                TriggerItem.walk(next, e);
                if (trackingEnabled)
                    SkriptAgent.throwEvent(new DelayEndEvent(duration, startTime, /* endTime: */ System.nanoTime()));
            }, duration.getTicks_i());
        }
        return null;
    }

    @Override
    protected void execute(final Event e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString(final @Nullable Event e, final boolean debug) {
        return "wait for " + duration.toString(e, debug) + (e == null ? "" : "...");
    }

}
