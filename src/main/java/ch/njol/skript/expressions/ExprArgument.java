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

package ch.njol.skript.expressions;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.command.Argument;
import ch.njol.skript.command.CommandEvent;
import ch.njol.skript.command.Commands;
import ch.njol.skript.command.ScriptCommandEvent;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.log.ErrorQuality;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Utils;
import ch.njol.util.Kleenean;
import ch.njol.util.StringUtils;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.eclipse.jdt.annotation.Nullable;

import java.util.List;
import java.util.regex.MatchResult;

/**
 * @author Peter Güttinger
 */
@Name("Argument")
@Description({"Only usable in command events. Holds the value of the nth argument given to the command, " + "e.g. if the command \"/tell &lt;player&gt; &lt;text&gt;\" is used like \"/tell Njol Hello Njol!\" argument 1 is the player named \"Njol\" and argument 2 is \"Hello Njol!\".", "One can also use the type of the argument instead of its index to address the argument, e.g. in the above example 'player-argument' is the same as 'argument 1'."})
@Examples({"give the item-argument to the player-argument", "damage the player-argument by the number-argument", "give a diamond pickaxe to the argument", "add argument 1 to argument 2", "heal the last argument"})
@Since("1.0")
public final class ExprArgument extends SimpleExpression<Object> {
    static {
        Skript.registerExpression(ExprArgument.class, Object.class, ExpressionType.SIMPLE, "[the] last arg[ument][s]", "[the] arg[ument][s](-| )<(\\d+)>", "[the] <(\\d*1)st|(\\d*2)nd|(\\d*3)rd|(\\d*[4-90])th> arg[ument][s]", "[the] arg[ument][s]", "[the] %*classinfo%( |-)arg[ument][( |-)<\\d+>]", "[the] arg[ument]( |-)%*classinfo%[( |-)<\\d+>]");
    }

    @SuppressWarnings("null")
    private Argument<?> arg;

    private boolean dynamic;
    private int index;

    private int matchedPattern;

    @Nullable
    private String script;

    private int line;

    @Override
    @SuppressWarnings({"null", "unused"})
    public boolean init(final Expression<?>[] exprs, final int matchedPattern, final Kleenean isDelayed, final ParseResult parser) {
        this.matchedPattern = matchedPattern;
        final List<Argument<?>> currentArguments = Commands.currentArguments;
        if (currentArguments == null) {
            if ((ScriptLoader.isCurrentEvent(PlayerCommandPreprocessEvent.class) || ScriptLoader.isCurrentEvent(ServerCommandEvent.class)) && !ScriptLoader.isCurrentEvent(ScriptCommandEvent.class) && !ScriptLoader.isCurrentEvent(CommandEvent.class)) {
                if (Skript.debug()) {
                    Skript.info("Using dynamic argument");

                    script = ScriptLoader.currentScript.getFileName();
                    line = SkriptLogger.getNode().getLine();
                }
                dynamic = true;
            } else {
                Skript.error("The expression 'argument' can only be used within a command", ErrorQuality.SEMANTIC_ERROR);
                return false;
            }
        }
        if (!dynamic && currentArguments.isEmpty()) {
            Skript.error("This command doesn't have any arguments", ErrorQuality.SEMANTIC_ERROR);
            return false;
        }
        Argument<?> arg = null;
        switch (matchedPattern) {
            case 0:
                if (!dynamic)
                    arg = currentArguments.get(currentArguments.size() - 1);
                else
                    index = -1;
                break;
            case 1:
            case 2:
                // Figure out in which format (1st, 2nd, 3rd, Nth) argument was given in
                final MatchResult regex = parser.regexes.get(0);
                String argMatch = null;

                for (int i = 1; i <= 4; i++) {
                    argMatch = regex.group(i);
                    if (argMatch != null)
                        break; // Found format
                }

                assert argMatch != null;
                final int i = Utils.parseInt(argMatch);

                if (!dynamic) {
                    assert currentArguments != null;
                    if (i > currentArguments.size()) {
                        Skript.error("The command doesn't have a " + StringUtils.fancyOrderNumber(i) + " argument", ErrorQuality.SEMANTIC_ERROR);
                        return false;
                    } else if (i < 1) {
                        Skript.error("Command arguments start from one; argument number " + i + " is invalid", ErrorQuality.SEMANTIC_ERROR);
                        return false;
                    }
                    arg = currentArguments.get(i - 1);
                } else
                    index = i - 1;
                break;
            case 3:
                if (!dynamic) {
                    if (currentArguments.size() == 1) {
                        arg = currentArguments.get(0);
                    } else {
                        Skript.error("'argument(s)' cannot be used if the command has multiple arguments. Use 'argument 1', 'argument 2', etc. instead", ErrorQuality.SEMANTIC_ERROR);
                        return false;
                    }
                } else
                    index = 0;
                break;
            case 4:
            case 5:
                if (dynamic) {
                    Skript.error("This form of the argument expression is not supported on command events! Use 'argument 1', 'argument 2', etc. instead", ErrorQuality.SEMANTIC_ERROR);
                    return false;
                }
                @SuppressWarnings("unchecked") final ClassInfo<?> c = ((Literal<ClassInfo<?>>) exprs[0]).getSingle();
                @SuppressWarnings("null") final int num = !parser.regexes.isEmpty() ? Utils.parseInt(parser.regexes.get(0).group()) : -1;
                int j = 1;
                for (final Argument<?> a : currentArguments) {
                    if (!c.getC().isAssignableFrom(a.getType()))
                        continue;
                    if (arg != null) {
                        Skript.error("There are multiple " + c + " arguments in this command", ErrorQuality.SEMANTIC_ERROR);
                        return false;
                    }
                    if (j < num) {
                        j++;
                        continue;
                    }
                    arg = a;
                    if (j == num)
                        break;
                }
                if (arg == null) {
                    j--;
                    if (num == -1 || j == 0)
                        Skript.error("There is no " + c + " argument in this command", ErrorQuality.SEMANTIC_ERROR);
                    else if (j == 1)
                        Skript.error("There is only one " + c + " argument in this command", ErrorQuality.SEMANTIC_ERROR);
                    else
                        Skript.error("There are only " + j + " " + c + " arguments in this command", ErrorQuality.SEMANTIC_ERROR);
                    return false;
                }
                break;
            default:
                assert false : matchedPattern;
                return false;
        }
        this.arg = arg;
        return true;
    }

    @SuppressWarnings("null")
    @Override
    @Nullable
    protected Object[] get(final Event e) {
        if (e instanceof ScriptCommandEvent)
            return arg.getCurrent(e);
        else if (dynamic) {
            CommandEvent event = null;

            if (e instanceof PlayerCommandPreprocessEvent) {
                final PlayerCommandPreprocessEvent ev = (PlayerCommandPreprocessEvent) e;
                event = new CommandEvent(ev.getPlayer(), ev.getMessage(), ev.getMessage().split(" "));
            } else if (e instanceof ServerCommandEvent) {
                final ServerCommandEvent ev = (ServerCommandEvent) e;
                event = new CommandEvent(ev.getSender(), ev.getCommand(), ev.getCommand().split(" "));
            } else
                assert false : e.getClass().getCanonicalName();

            final String[] args = event.getArgs();

            final int finalIndex = matchedPattern == 0 ? index > 0 ? index - 1 : index : index + 1;

            assert matchedPattern != 0 || index == -1;
            assert finalIndex >= 0;

            if (finalIndex >= args.length)
                return null;

            if (Skript.debug())
                Skript.info("Getting dynamic argument at the index " + finalIndex + (script != null ? " (" + script + ", line " + line + ")" : ""));

            return new String[]{args[finalIndex]};
        }
        assert false : e.getClass().getCanonicalName();
        return null;
    }

    @Override
    public Class<?> getReturnType() {
        if (dynamic)
            return String.class;
        return arg.getType();
    }

    @Override
    public String toString(final @Nullable Event e, final boolean debug) {
        if (dynamic)
            return "the " + (matchedPattern == 0 ? "last" : StringUtils.fancyOrderNumber(index + 1)) + " argument";
        if (e == null)
            return "the " + StringUtils.fancyOrderNumber(arg.getIndex() + 1) + " argument";
        return Classes.getDebugMessage(getArray(e));
    }

    @Override
    public boolean isSingle() {
        if (dynamic)
            return true;
        return arg.isSingle();
    }

    @Override
    public boolean isLoopOf(final String s) {
        return "argument".equalsIgnoreCase(s);
    }

}
