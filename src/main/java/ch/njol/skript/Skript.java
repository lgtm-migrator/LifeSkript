/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Copyright 2011-2019 Peter Güttinger and contributors
 *
 */

package ch.njol.skript;

import ch.njol.skript.aliases.Aliases;
import ch.njol.skript.bukkitutil.Workarounds;
import ch.njol.skript.classes.data.*;
import ch.njol.skript.command.Commands;
import ch.njol.skript.doc.Documentation;
import ch.njol.skript.events.EvtSkript;
import ch.njol.skript.expressions.ExprEntities;
import ch.njol.skript.hooks.Hook;
import ch.njol.skript.lang.*;
import ch.njol.skript.lang.function.Functions;
import ch.njol.skript.localization.Language;
import ch.njol.skript.localization.Message;
import ch.njol.skript.log.*;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.registrations.Converters;
import ch.njol.skript.util.*;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Closeable;
import ch.njol.util.StringUtils;
import ch.njol.util.WebUtils;
import ch.njol.util.coll.CollectionUtils;
import ch.njol.util.coll.iterator.CheckedIterator;
import ch.njol.util.coll.iterator.EnumerationIterable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jdt.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

// TODO meaningful error if someone uses an %expression with percent signs% outside of text or a variable

/**
 * <b>Skript</b> - A Bukkit plugin to modify how Minecraft behaves without having to write a single line of code (You'll likely be writing some code though if you're reading this
 * =P)
 * <p>
 * Use this class to extend this plugin's functionality by adding more {@link Condition conditions}, {@link Effect effects}, {@link ch.njol.skript.lang.util.SimpleExpression expressions}, etc.
 * <p>
 * If your plugin.yml contains <tt>'depend: [Skript]'</tt> then your plugin will not start at all if Skript is not present. Add <tt>'softdepend: [Skript]'</tt> to your plugin.yml
 * if you want your plugin to work even if Skript isn't present, but want to make sure that Skript gets loaded before your plugin.
 * <p>
 * If you use 'softdepend' you can test whether Skript is loaded with <tt>'Bukkit.getPluginManager().getPlugin(&quot;Skript&quot;) != null'</tt>
 * <p>
 * Once you made sure that Skript is loaded you can use <code>Skript.getInstance()</code> whenever you need a reference to the plugin, but you likely won't need it since all API
 * methods are static.
 *
 * @author Peter Güttinger
 * @see #registerAddon(JavaPlugin)
 * @see #registerCondition(Class, String...)
 * @see #registerEffect(Class, String...)
 * @see #registerExpression(Class, Class, ExpressionType, String...)
 * @see #registerEvent(String, Class, Class, String...)
 * @see ch.njol.skript.registrations.EventValues#registerEventValue(Class, Class, Getter, int)
 * @see Classes#registerClass(ch.njol.skript.classes.ClassInfo)
 * @see ch.njol.skript.registrations.Comparators#registerComparator(Class, Class, ch.njol.skript.classes.Comparator)
 * @see Converters#registerConverter(Class, Class, ch.njol.skript.classes.Converter)
 */
public final class Skript extends JavaPlugin implements Listener {

    // ================ CONSTANTS ================

    public static final String LATEST_VERSION_DOWNLOAD_LINK = "https://github.com/LifeMC/LifeSkript/releases/latest/";

    public static final String ISSUES_LINK = "https://github.com/LifeMC/LifeSkript/issues/";

    // ================ PLUGIN ================
    public final static Message m_invalid_reload = new Message("skript.invalid reload"),
            m_finished_loading = new Message("skript.finished loading");
    public final static String SCRIPTSFOLDER = "scripts";
    /**
     * A small value, useful for comparing doubles or floats.
     * <p>
     * E.g. to test whether two floating-point numbers are equal:
     *
     * <pre>
     * Math.abs(a - b) &lt; Skript.EPSILON
     * </pre>
     * <p>
     * or whether a location is within a specific radius of another location:
     *
     * <pre>
     * location.distanceSquared(center) - radius * radius &lt; Skript.EPSILON
     * </pre>
     *
     * @see #EPSILON_MULT
     */
    public final static double EPSILON = 1e-10;
    /**
     * A value a bit larger than 1
     *
     * @see #EPSILON
     */
    public final static double EPSILON_MULT = 1.00001;
    /**
     * The maximum ID a block can have in Minecraft.
     */
    public final static int MAXBLOCKID = 255;
    /**
     * The maximum data value of Minecraft, i.e. Short.MAX_VALUE - Short.MIN_VALUE.
     */
    public final static int MAXDATAVALUE = Short.MAX_VALUE - Short.MIN_VALUE;
    public static final String SKRIPT_PREFIX = ChatColor.GRAY + "[" + ChatColor.GOLD + "Skript" + ChatColor.GRAY + "]" + ChatColor.RESET + " ";
    @SuppressWarnings("null")
    private final static Collection<Closeable> closeOnDisable = Collections.synchronizedCollection(new ArrayList<>());
    private final static HashMap<String, SkriptAddon> addons = new HashMap<>();
    private final static Collection<SyntaxElementInfo<? extends Condition>> conditions = new ArrayList<>(100);
    private final static Collection<SyntaxElementInfo<? extends Effect>> effects = new ArrayList<>(100);
    private final static Collection<SyntaxElementInfo<? extends Statement>> statements = new ArrayList<>(100);
    private final static List<ExpressionInfo<?, ?>> expressions = new ArrayList<>(300);
    private final static int[] expressionTypesStartIndices = new int[ExpressionType.values().length];
    private final static Collection<SkriptEventInfo<?>> events = new ArrayList<>(100);
    private final static String EXCEPTION_PREFIX = "#!#! ";
    @Nullable
    static Skript instance;
    static boolean disabled;
    static boolean updateAvailable;
    @Nullable
    static String latestVersion;
    static Version minecraftVersion = new Version(666);
    static boolean runningCraftBukkit;
    @Nullable
    private static Version version;
    public final static UncaughtExceptionHandler UEH = (t, e) -> Skript.exception(e, "Exception in thread " + (t == null ? null : t.getName()));
    private static boolean acceptRegistrations = true;
    @Nullable
    private static SkriptAddon addon;

    public Skript() throws IllegalStateException {
        if (instance != null)
            throw new IllegalStateException("Cannot create multiple instances of Skript!");
        instance = this;
    }

    public static Skript getInstance() {
        final Skript i = instance;
        if (i == null)
            throw new IllegalStateException("Can't get Skript instance because it was null!");
        return i;
    }

    public static Version getVersion() {
        final Version v = version;
        if (v == null)
            throw new IllegalStateException("Can't get Skript version because it was null!");
        return v;
    }

    public static void printDownloadLink() {
        Bukkit.getScheduler().runTask(getInstance(), () -> Bukkit.getLogger().info("[Skript] You can download the latest Skript version here: " + LATEST_VERSION_DOWNLOAD_LINK));
    }

    public static void printIssuesLink() {
        Bukkit.getScheduler().runTask(getInstance(), () -> Bukkit.getLogger().info("[Skript] Please report all issues you encounter to the issues page: " + ISSUES_LINK));
    }

    @Nullable
    public static String getLatestVersion() {
        try {
            return WebUtils.getResponse("https://www.lifemcserver.com/skript-latest.php");
        } catch (final Throwable tw) {
            return null;
        }
    }

    public static Version getMinecraftVersion() {
        return minecraftVersion;
    }

    /**
     * @return Whether this server is running CraftBukkit
     */
    public static boolean isRunningCraftBukkit() {
        return runningCraftBukkit;
    }

    // ================ CONSTANTS, OPTIONS & OTHER ================

    /**
     * @return Whether this server is running Minecraft <tt>major.minor</tt> <b>or higher</b>
     */
    public static boolean isRunningMinecraft(final int major, final int minor) {
        return minecraftVersion.compareTo(major, minor) >= 0;
    }

    public static boolean isRunningMinecraft(final int major, final int minor, final int revision) {
        return minecraftVersion.compareTo(major, minor, revision) >= 0;
    }

    public static boolean isRunningMinecraft(final Version v) {
        return minecraftVersion.compareTo(v) >= 0;
    }

    /**
     * Used to test whether certain Bukkit features are supported.
     *
     * @param className
     * @return Whether the given class exists.
     * @deprecated use {@link #classExists(String)}
     */
    @Deprecated
    public static boolean supports(final String className) {
        return classExists(className);
    }

    /**
     * Tests whether a given class exists in the classpath.
     *
     * @param className The {@link Class#getCanonicalName() canonical name} of the class
     * @return Whether the given class exists.
     */
    @SuppressWarnings({"null", "unused"})
    public static boolean classExists(final String className) {
        if (className == null)
            return false;
        try {
            Class.forName(className);
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Gets a specific class if it exists, otherwise returns null.
     * Note this simply catches the exception and returns null on exception.
     * Yo should use {@link Skript#classExists(String)} if you want to actually check if it exists.
     *
     * @param className The {@link Class#getCanonicalName() canonical name} of the class
     * @return The class representing the given class name
     */
    @Nullable
    @SuppressWarnings({"null", "unused"})
    public static Class<?> classForName(final String className) {
        if (className == null)
            return null;
        try {
            return Class.forName(className);
        } catch (final ClassNotFoundException ex) {
            return null;
        }
    }

    /**
     * Tests whether a method exists in the given class.
     *
     * @param c              The class
     * @param methodName     The name of the method
     * @param parameterTypes The parameter types of the method
     * @return Whether the given method exists.
     */
    @SuppressWarnings("null")
    public static boolean methodExists(final Class<?> c, final String methodName, final Class<?>... parameterTypes) {
        if (c == null || methodName == null)
            return false;
        try {
            c.getDeclaredMethod(methodName, parameterTypes);
            return true;
        } catch (final NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    /**
     * Tests whether a method exists in the given class, and whether the return type matches the expected one.
     * <p>
     * Note that this method doesn't work properly if multiple methods with the same name and parameters exist but have different return types.
     *
     * @param c              The class
     * @param methodName     The name of the method
     * @param parameterTypes The parameter types of the method
     * @param returnType     The expected return type
     * @return Whether the given method exists.
     */
    @SuppressWarnings("null")
    public static boolean methodExists(final Class<?> c, final String methodName, final Class<?>[] parameterTypes, final Class<?> returnType) {
        if (c == null || methodName == null)
            return false;
        try {
            final Method m = c.getDeclaredMethod(methodName, parameterTypes);
            return m.getReturnType() == returnType;
        } catch (final NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    /**
     * Tests whether a field exists in the given class.
     *
     * @param c         The class
     * @param fieldName The name of the field
     * @return Whether the given field exists.
     */
    @SuppressWarnings("null")
    public static boolean fieldExists(final Class<?> c, final String fieldName) {
        if (c == null || fieldName == null)
            return false;
        try {
            c.getDeclaredField(fieldName);
            return true;
        } catch (final NoSuchFieldException | SecurityException e) {
            return false;
        }
    }

    /**
     * Clears triggers, commands, functions and variable names
     */
    static void disableScripts() {
        VariableString.variableNames.clear();
        SkriptEventHandler.removeAllTriggers();
        Commands.clearCommands();
        Functions.clearFunctions();
    }

    // ================ REGISTRATIONS ================

    /**
     * Prints errors from reloading the config & scripts
     */
    static void reload() {
        disableScripts();
        reloadMainConfig();
        reloadAliases();
        ScriptLoader.loadScripts();
    }

    /**
     * Prints errors
     */
    static void reloadScripts() {
        disableScripts();
        ScriptLoader.loadScripts();
    }

    /**
     * Prints errors
     */
    static void reloadMainConfig() {
        SkriptConfig.load();
    }

    /**
     * Prints errors
     */
    static void reloadAliases() {
        Aliases.clear();
        Aliases.load();
    }

    // ================ ADDONS ================

    /**
     * Registers a Closeable that should be closed when this plugin is disabled.
     * <p>
     * All registered Closeables will be closed after all scripts have been stopped.
     *
     * @param closeable
     */
    public static void closeOnDisable(final Closeable closeable) {
        closeOnDisable.add(closeable);
    }

    public static void outdatedError() {
        error("Skript v" + getInstance().getDescription().getVersion() + " is not fully compatible with Bukkit " + Bukkit.getVersion() + ". Some feature(s) will be broken until you update Skript.");
    }

    public static void outdatedError(final Exception e) {
        outdatedError();
        if (testing())
            e.printStackTrace();
    }

    // TODO localise Infinity, -Infinity, NaN (and decimal point?)
    public static String toString(final double n) {
        return StringUtils.toString(n, SkriptConfig.numberAccuracy.value());
    }

    /**
     * Creates a new Thread and sets its UncaughtExceptionHandler. The Thread is not started automatically.
     */
    public static Thread newThread(final Runnable r, final String name) {
        final Thread t = new Thread(r, name);
        t.setUncaughtExceptionHandler(UEH);
        return t;
    }

    public static boolean isAcceptRegistrations() {
        return acceptRegistrations;
    }

    public static void checkAcceptRegistrations() {
        if (!acceptRegistrations)
            throw new SkriptAPIException("Registering is disabled after initialisation!");
    }

    // ================ CONDITIONS & EFFECTS ================

    static void stopAcceptingRegistrations() {
        acceptRegistrations = false;

        Converters.createMissingConverters();

        Classes.onRegistrationsStop();
    }

    /**
     * Registers an addon to Skript. This is currently not required for addons to work, but the returned {@link SkriptAddon} provides useful methods for registering syntax elements
     * and adding new strings to Skript's localization system (e.g. the required "types.[type]" strings for registered classes).
     *
     * @param p The plugin
     */
    public static SkriptAddon registerAddon(final JavaPlugin p) {
        checkAcceptRegistrations();
        if (addons.containsKey(p.getName()))
            throw new IllegalArgumentException("The addon " + p.getName() + " is already registered!");
        final SkriptAddon addon = new SkriptAddon(p);
        addons.put(p.getName(), addon);
        if (Skript.logVeryHigh())
            Skript.info("The addon " + p.getDescription().getFullName() + " was registered to Skript successfully.");
        return addon;
    }

    @Nullable
    public static SkriptAddon getAddon(final JavaPlugin p) {
        return addons.get(p.getName());
    }

    @Nullable
    public static SkriptAddon getAddon(final String name) {
        return addons.get(name);
    }

    @SuppressWarnings("null")
    public static Collection<SkriptAddon> getAddons() {
        return Collections.unmodifiableCollection(addons.values());
    }

    /**
     * @return A {@link SkriptAddon} representing Skript.
     */
    public static SkriptAddon getAddonInstance() {
        final SkriptAddon a = addon;
        if (a == null)
            return addon = new SkriptAddon(Skript.getInstance()).setLanguageFileDirectory("lang");
        else
            return a;
    }

    /**
     * registers a {@link Condition}.
     *
     * @param condition The condition's class
     * @param patterns  Skript patterns to match this condition
     */
    public static <E extends Condition> void registerCondition(final Class<E> condition, final String... patterns) throws IllegalArgumentException {
        checkAcceptRegistrations();
        final SyntaxElementInfo<E> info = new SyntaxElementInfo<>(patterns, condition);
        conditions.add(info);
        statements.add(info);
    }

    /**
     * Registers an {@link Effect}.
     *
     * @param effect   The effect's class
     * @param patterns Skript patterns to match this effect
     */
    public static <E extends Effect> void registerEffect(final Class<E> effect, final String... patterns) throws IllegalArgumentException {
        checkAcceptRegistrations();
        final SyntaxElementInfo<E> info = new SyntaxElementInfo<>(patterns, effect);
        effects.add(info);
        statements.add(info);
    }

    // ================ EXPRESSIONS ================

    public static Collection<SyntaxElementInfo<? extends Statement>> getStatements() {
        return statements;
    }

    public static Collection<SyntaxElementInfo<? extends Condition>> getConditions() {
        return conditions;
    }

    public static Collection<SyntaxElementInfo<? extends Effect>> getEffects() {
        return effects;
    }

    /**
     * Registers an expression.
     *
     * @param c          The expression's class
     * @param returnType The superclass of all values returned by the expression
     * @param type       The expression's {@link ExpressionType type}. This is used to determine in which order to try to parse expressions.
     * @param patterns   Skript patterns that match this expression
     * @throws IllegalArgumentException if returnType is not a normal class
     */
    public static <E extends Expression<T>, T> void registerExpression(final Class<E> c, final Class<T> returnType, final ExpressionType type, final String... patterns) throws IllegalArgumentException {
        checkAcceptRegistrations();
        if (returnType.isAnnotation() || returnType.isArray() || returnType.isPrimitive())
            throw new IllegalArgumentException("returnType must be a normal type");
        final ExpressionInfo<E, T> info = new ExpressionInfo<>(patterns, returnType, c);
        for (int i = type.ordinal() + 1; i < ExpressionType.values().length; i++) {
            expressionTypesStartIndices[i]++;
        }
        expressions.add(expressionTypesStartIndices[type.ordinal()], info);
    }

    @SuppressWarnings("null")
    public static Iterator<ExpressionInfo<?, ?>> getExpressions() {
        return expressions.iterator();
    }

    // ================ EVENTS ================

    public static Iterator<ExpressionInfo<?, ?>> getExpressions(final Class<?>... returnTypes) {
        return new CheckedIterator<>(getExpressions(), i -> {
            if (i == null || i.returnType == Object.class)
                return true;
            for (final Class<?> returnType : returnTypes) {
                assert returnType != null;
                if (Converters.converterExists(i.returnType, returnType))
                    return true;
            }
            return false;
        });
    }

    /**
     * Registers an event.
     *
     * @param name     Capitalised name of the event without leading "On" which is added automatically (Start the name with an asterisk to prevent this). Used for error messages and
     *                 the documentation.
     * @param c        The event's class
     * @param event    The Bukkit event this event applies to
     * @param patterns Skript patterns to match this event
     * @return A SkriptEventInfo representing the registered event. Used to generate Skript's documentation.
     */
    @SuppressWarnings({"unchecked"})
    public static <E extends SkriptEvent> SkriptEventInfo<E> registerEvent(final String name, final Class<E> c, final Class<? extends Event> event, final String... patterns) {
        checkAcceptRegistrations();
        final SkriptEventInfo<E> r = new SkriptEventInfo<>(name, patterns, c, CollectionUtils.array(event));
        events.add(r);
        return r;
    }

    /**
     * Registers an event.
     *
     * @param name     The name of the event, used for error messages
     * @param c        The event's class
     * @param events   The Bukkit events this event applies to
     * @param patterns Skript patterns to match this event
     * @return A SkriptEventInfo representing the registered event. Used to generate Skript's documentation.
     */
    public static <E extends SkriptEvent> SkriptEventInfo<E> registerEvent(final String name, final Class<E> c, final Class<? extends Event>[] events, final String... patterns) {
        checkAcceptRegistrations();
        final SkriptEventInfo<E> r = new SkriptEventInfo<>(name, patterns, c, events);
        Skript.events.add(r);
        return r;
    }

    public static Collection<SkriptEventInfo<?>> getEvents() {
        return events;
    }

    // ================ COMMANDS ================

    /**
     * Dispatches a command with calling command events
     *
     * @param sender
     * @param command
     * @return Whether the command was run
     */
    public static boolean dispatchCommand(final CommandSender sender, final String command) {
        try {
            if (sender instanceof Player) {
                final PlayerCommandPreprocessEvent e = new PlayerCommandPreprocessEvent((Player) sender, "/" + command);
                Bukkit.getPluginManager().callEvent(e);
                if (e.isCancelled() || e.getMessage() == null || !e.getMessage().startsWith("/"))
                    return false;
                return Bukkit.dispatchCommand(e.getPlayer(), e.getMessage().substring(1));
            } else {
                final ServerCommandEvent e = new ServerCommandEvent(sender, command);
                Bukkit.getPluginManager().callEvent(e);
                if (e.getCommand() == null || e.getCommand().isEmpty())
                    return false;
                return Bukkit.dispatchCommand(e.getSender(), e.getCommand());
            }
        } catch (final Throwable tw) {
            Skript.exception(tw, "Error occurred when executing command " + command);
            return false;
        }
    }

    // ================ LOGGING ================

    public static boolean logNormal() {
        return SkriptLogger.log(Verbosity.NORMAL);
    }

    public static boolean logHigh() {
        return SkriptLogger.log(Verbosity.HIGH);
    }

    public static boolean logVeryHigh() {
        return SkriptLogger.log(Verbosity.VERY_HIGH);
    }

    public static boolean debug() {
        return SkriptLogger.debug();
    }

    public static boolean testing() {
        return debug() || Skript.class.desiredAssertionStatus();
    }

    public static boolean log(final Verbosity minVerb) {
        return SkriptLogger.log(minVerb);
    }

    public static void debug(final String info) {
        if (!debug())
            return;
        SkriptLogger.log(SkriptLogger.DEBUG, info);
    }

    /**
     * @see SkriptLogger#log(Level, String)
     */
    @SuppressWarnings("null")
    public static void info(final String info) {
        SkriptLogger.log(Level.INFO, info);
    }

    /**
     * @see SkriptLogger#log(Level, String)
     */
    @SuppressWarnings("null")
    public static void warning(final String warning) {
        SkriptLogger.log(Level.WARNING, warning);
    }

    /**
     * @see SkriptLogger#log(Level, String)
     */
    @SuppressWarnings("null")
    public static void error(final @Nullable String error) {
        if (error != null)
            SkriptLogger.log(Level.SEVERE, error);
    }

    /**
     * Use this in {@link Expression#init(Expression[], int, ch.njol.util.Kleenean, ch.njol.skript.lang.SkriptParser.ParseResult)} (and other methods that are called during the
     * parsing) to log
     * errors with a specific {@link ErrorQuality}.
     *
     * @param error
     * @param quality
     */
    public static void error(final String error, final ErrorQuality quality) {
        SkriptLogger.log(new LogEntry(SkriptLogger.SEVERE, quality, error));
    }

    /**
     * Used if something happens that shouldn't happen
     *
     * @param info Description of the error and additional information
     * @return an EmptyStacktraceException to throw if code execution should terminate.
     */
    public static RuntimeException exception(final String... info) {
        return exception(null, info);
    }

    public static RuntimeException exception(final @Nullable Throwable cause, final String... info) {
        return exception(cause, null, null, info);
    }

    public static RuntimeException exception(final @Nullable Throwable cause, final @Nullable Thread thread, final String... info) {
        return exception(cause, thread, null, info);
    }

    public static RuntimeException exception(final @Nullable Throwable cause, final @Nullable TriggerItem item, final String... info) {
        return exception(cause, null, item, info);
    }

    /**
     * Used if something happens that shouldn't happen
     *
     * @param cause exception that shouldn't occur
     * @param info  Description of the error and additional information
     * @return an EmptyStacktraceException to throw if code execution should terminate.
     */
    public static RuntimeException exception(@Nullable Throwable cause, final @Nullable Thread thread, final @Nullable TriggerItem item, final String... info) {

        logEx();
        logEx("[Skript] Severe Error:");
        logEx(info);
        logEx();
        logEx("If you're developing a java add-on for Skript this likely means that you have done something wrong.");
        logEx("If you're a server admin however please go to " + ISSUES_LINK);
        logEx("and check whether this issue has already been reported.");
        logEx("If not please create a new issue with a meaningful title, copy & paste this whole error into it,");
        logEx("and describe what you did before it happened and/or what you think caused the error.");
        logEx("If you think that it's a trigger that's causing the error please post the trigger as well.");
        logEx("By following this guide fixing the error should be easy and done fast.");

        logEx();
        logEx("Stack trace:");
        if (cause == null || cause.getStackTrace().length == 0) {
            logEx("  warning: no/empty exception given, dumping current stack trace instead");
            cause = new Exception(cause);
        }
        boolean first = true;
        while (cause != null) {
            logEx((first ? "" : "Caused by: ") + cause.toString());
            for (final StackTraceElement e : cause.getStackTrace())
                logEx("    at " + e.toString());
            cause = cause.getCause();
            first = false;
        }

        logEx();
        logEx("Version Information:");
        logEx("  Skript: " + getVersion() + (updateAvailable ? " (update available)" : " (latest)"));
        logEx("  Bukkit: " + Bukkit.getBukkitVersion() + " (" + Bukkit.getVersion() + ")");
        logEx("  Minecraft: " + getMinecraftVersion());
        logEx("  Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version") + ")");
        logEx("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + " " + System.getProperty("os.version"));
        logEx();
        logEx("Running CraftBukkit: " + runningCraftBukkit);
        logEx();
        logEx("Current node: " + SkriptLogger.getNode());
        logEx("Current item: " + (item == null ? "not available" : item.toString(null, true)));
        if (item != null && item.getTrigger() != null) {
            final Trigger trigger = item.getTrigger();
            if (trigger != null) {
                final File script = trigger.getScript();
                logEx("Current trigger: " + trigger.toString(null, true) + " (" + (script == null ? "null" : script.getName()) + ", line " + trigger.getLineNumber() + ")");
            } else {
                logEx("Current trigger: not available");
            }
        } else {
            logEx("Current trigger: no trigger");
        }
        logEx();
        logEx("Thread: " + (thread == null ? Thread.currentThread() : thread).getName());
        logEx();
        logEx("Language: " + Language.getName().substring(0, 1).toUpperCase(Locale.ENGLISH) + Language.getName().substring(1));
        logEx();
        logEx("End of Error.");
        logEx();

        return new EmptyStacktraceException();
    }

    static void logEx() {
        SkriptLogger.LOGGER.severe(EXCEPTION_PREFIX);
    }

    static void logEx(final String... lines) {
        for (final String line : lines)
            SkriptLogger.LOGGER.severe(EXCEPTION_PREFIX + line);
    }

    public static void info(final CommandSender sender, final String info) {
        sender.sendMessage(SKRIPT_PREFIX + Utils.replaceEnglishChatStyles(info));
    }

    /**
     * @param message
     * @param permission
     * @see #adminBroadcast(String)
     */
    public static void broadcast(final String message, final String permission) {
        Bukkit.broadcast(SKRIPT_PREFIX + Utils.replaceEnglishChatStyles(message), permission);
    }

//	static {
//		Language.addListener(new LanguageChangeListener() {
//			@Override
//			public void onLanguageChange() {
//				final String s = Language.get_("skript.prefix");
//				if (s != null)
//					SKRIPT_PREFIX = Utils.replaceEnglishChatStyles(s) + ChatColor.RESET + " ";
//			}
//		});
//	}

    public static void adminBroadcast(final String message) {
        Bukkit.broadcast(SKRIPT_PREFIX + Utils.replaceEnglishChatStyles(message), "skript.admin");
    }

    /**
     * Similar to {@link #info(CommandSender, String)} but no [Skript] prefix is added.
     *
     * @param sender
     * @param info
     */
    public static void message(final CommandSender sender, final String info) {
        sender.sendMessage(Utils.replaceEnglishChatStyles(info));
    }

    public static void error(final CommandSender sender, final String error) {
        sender.sendMessage(SKRIPT_PREFIX + ChatColor.DARK_RED + Utils.replaceEnglishChatStyles(error));
    }

    @SuppressWarnings("null")
    public static File getPluginFile() {
        return getInstance().getFile();
    }

    @SuppressWarnings("null")
    public static ClassLoader getBukkitClassLoader() {
        return getInstance().getClassLoader();
    }

    @Override
    public void onEnable() {
        try {

            if (disabled) {
                Skript.error(m_invalid_reload.toString());
                setEnabled(false);
                return;
            }

            //System.setOut(new FilterPrintStream(System.out));

            Language.loadDefault(getAddonInstance());

            Workarounds.init();

            version = new Version("" + getDescription().getVersion());
            runningCraftBukkit = Bukkit.getServer().getClass().getName().equals("org.bukkit.craftbukkit.CraftServer"); //NOSONAR
            final String bukkitV = Bukkit.getBukkitVersion();
            final Matcher m = Pattern.compile("\\d+\\.\\d+(\\.\\d+)?").matcher(bukkitV);
            if (!m.find()) {
                Skript.error("The Bukkit version '" + bukkitV + "' does not contain a version number which is required for Skript to enable or disable certain features. " + "Skript will still work, but you might get random errors if you use features that are not available in your version of Bukkit.");
                minecraftVersion = new Version(666, 0, 0);
            } else {
                minecraftVersion = new Version("" + m.group());
            }

            if (!getDataFolder().isDirectory())
                getDataFolder().mkdirs();

            final File scripts = new File(getDataFolder(), SCRIPTSFOLDER);
            if (!scripts.isDirectory()) {
                ZipFile f = null;
                try {
                    if (!scripts.mkdirs())
                        throw new IOException("Could not create the directory " + scripts);
                    f = new ZipFile(getFile());
                    for (final ZipEntry e : new EnumerationIterable<ZipEntry>(f.entries())) {
                        if (e.isDirectory())
                            continue;
                        File saveTo = null;
                        if (e.getName().startsWith(SCRIPTSFOLDER + "/")) {
                            final String fileName = e.getName().substring(e.getName().lastIndexOf('/') + 1);
                            saveTo = new File(scripts, (fileName.startsWith("-") ? "" : "-") + fileName);
                        } else if ("config.sk".equals(e.getName())) {
                            final File cf = new File(getDataFolder(), e.getName());
                            if (!cf.exists())
                                saveTo = cf;
                        } else if (e.getName().startsWith("aliases-") && e.getName().endsWith(".sk") && !e.getName().contains("/")) {
                            final File af = new File(getDataFolder(), e.getName());
                            if (!af.exists())
                                saveTo = af;
                        }
                        if (saveTo != null) {
                            try (InputStream in = f.getInputStream(e)) {
                                assert in != null;
                                FileUtils.save(in, saveTo);
                            }
                        }
                    }
                    info("Successfully generated the config, the example scripts and the aliases files.");
                } catch (final ZipException ignored) {
                } catch (final IOException e) {
                    error("Error generating the default files: " + ExceptionUtils.toString(e));
                } finally {
                    if (f != null) {
                        try {
                            f.close();
                        } catch (final IOException ignored) {
                        }
                    }
                }
            }

            getCommand("skript").setExecutor(new SkriptCommand());

            new JavaClasses(); //NOSONAR
            new BukkitClasses(); //NOSONAR
            new BukkitEventValues(); //NOSONAR
            new SkriptClasses(); //NOSONAR

            new DefaultComparators(); //NOSONAR
            new DefaultConverters(); //NOSONAR
            new DefaultFunctions(); //NOSONAR

            try {
                getAddonInstance().loadClasses("ch.njol.skript", "conditions", "effects", "events", "expressions", "entity");
                if (logHigh()) {
                    if (getAddonInstance().getUnloadableClassCount() > 0) {
                        if (Skript.logVeryHigh()) {
                            info("Total of " + getAddonInstance().getUnloadableClassCount() + " classes are excluded from Skript. This maybe because of your version. Try enabling debug logging for more info.");
                        } else if (!Skript.isRunningMinecraft(1, 7, 10) && !Skript.isRunningMinecraft(1, 8, 8)) {
                            info("Total of " + getAddonInstance().getUnloadableClassCount() + " classes are excluded from Skript. This maybe because of your version. Try enabling debug logging for more info.");
                        }
                    } else {
                        info("Total of " + getAddonInstance().getLoadedClassCount() + " classes loaded from Skript.");
                    }
                }
            } catch (final Throwable tw) {
                exception(tw, "Could not load required .class files: " + tw.getLocalizedMessage());
                setEnabled(false);
                return;
            }

            SkriptConfig.load();
            Language.setUseLocal(true);

            //Updater.start();

            Aliases.load();

            Commands.registerListeners();

            if (logNormal())
                info(" " + Language.get("skript.copyright"));

            final long tick = testing() ? Bukkit.getWorlds().get(0).getFullTime() : 0;
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                @SuppressWarnings("synthetic-access")
                public void run() {
                    assert Bukkit.getWorlds().get(0).getFullTime() == tick;

                    // load hooks
                    try {
                        try (JarFile jar = new JarFile(getPluginFile())) {
                            for (final JarEntry e : new EnumerationIterable<>(jar.entries())) {
                                if (e.getName().startsWith("ch/njol/skript/hooks/") && e.getName().endsWith("Hook.class") && StringUtils.count("" + e.getName(), '/') <= 5) {
                                    final String c = e.getName().replace('/', '.').substring(0, e.getName().length() - ".class".length());
                                    try {
                                        final Class<?> hook = Class.forName(c, true, getBukkitClassLoader());
                                        if (hook != null && Hook.class.isAssignableFrom(hook) && !hook.isInterface() && Hook.class != hook) {
                                            hook.getDeclaredConstructor().setAccessible(true);
                                            hook.getDeclaredConstructor().newInstance();
                                        }
                                    } catch (final NoClassDefFoundError ncdffe) {
                                        Skript.exception(ncdffe, "Cannot load class " + c + " because it missing some dependencies");
                                    } catch (final ClassNotFoundException ex) {
                                        Skript.exception(ex, "Cannot load class " + c);
                                    } catch (final ExceptionInInitializerError err) {
                                        Skript.exception(err.getCause(), "Class " + c + " generated an exception while loading");
                                    }
                                }
                            }
                        }
                    } catch (final Throwable tw) {
                        error("Error while loading plugin hooks" + (tw.getLocalizedMessage() == null ? "" : ": " + tw.getLocalizedMessage()));
                        if (testing())
                            tw.printStackTrace();
                    }

                    Language.setUseLocal(false);

                    stopAcceptingRegistrations();

                    if (!SkriptConfig.disableDocumentationGeneration.value())
                        Documentation.generate();

                    if (logNormal())
                        info("Loading variables...");

                    final long vls = System.currentTimeMillis();

                    final LogHandler h = SkriptLogger.startLogHandler(new ErrorDescLogHandler() {
//						private final List<LogEntry> log = new ArrayList<LogEntry>();

                        @Override
                        public LogResult log(final LogEntry entry) {
                            super.log(entry);
                            if (entry.level.intValue() >= Level.SEVERE.intValue()) {
                                logEx(entry.message); // no [Skript] prefix
                                return LogResult.DO_NOT_LOG;
                            } else {
//								log.add(entry);
//								return LogResult.CACHED;
                                return LogResult.LOG;
                            }
                        }

                        @Override
                        protected void beforeErrors() {
                            logEx();
                            logEx("===!!!=== Skript variable load error ===!!!===");
                            logEx("Unable to load (all) variables:");
                        }

                        @Override
                        protected void afterErrors() {
                            logEx();
                            logEx("Skript will work properly, but old variables might not be available at all and new ones may or may not be saved until Skript is able to create a backup of the old file and/or is able to connect to the database (which requires a restart of Skript)!");
                            logEx();
                        }

                        @Override
                        protected void onStop() {
                            super.onStop();
//							SkriptLogger.logAll(log);
                        }
                    });
                    final CountingLogHandler c = SkriptLogger.startLogHandler(new CountingLogHandler(SkriptLogger.SEVERE));
                    try {
                        if (!Variables.load())
                            if (c.getCount() == 0)
                                error("(no information available)");
                    } finally {
                        c.stop();
                        h.stop();
                    }

                    final long vld = System.currentTimeMillis() - vls;
                    if (logNormal())
                        info("Loaded " + Variables.numVariables() + " variables in " + vld / 100 / 10. + " seconds");

                    ScriptLoader.loadScripts();

                    Skript.info(m_finished_loading.toString());

                    EvtSkript.onSkriptStart();

                    // suppresses the "can't keep up" warning after loading all scripts
                    final Filter f = record -> record != null && record.getMessage() != null && !record.getMessage().toLowerCase().startsWith("can't keep up!".toLowerCase());
                    BukkitLoggerFilter.addFilter(f);
                    Bukkit.getScheduler().scheduleSyncDelayedTask(Skript.this, () -> BukkitLoggerFilter.removeFilter(f), 1);
                }
            });

            if (Skript.testing() && Skript.logHigh() || Skript.logVeryHigh()) {

                Bukkit.getScheduler().runTask(getInstance(), () -> {

                    Skript.info(Color.getWoolData ? "Using new method for color data." : "Using old method for color data.");
                    Skript.info(ExprEntities.getNearbyEntities ? "Using new method for entities expression." : "Using old method for entities expression.");

                });

            }

            if (!isEnabled())
                return;

            Bukkit.getScheduler().runTask(getInstance(), () -> {

                if (minecraftVersion.compareTo(1, 7, 10) == 0) { // If running on Minecraft 1.7.10

                    if (!classExists("com.lifespigot.Main") || !ExprEntities.getNearbyEntities) { // If not using LifeSpigot or not supports getNearbyEntities

                        Skript.warning("You are running on 1.7.10 and not using LifeSpigot, Some features will not be available. Switch to LifeSpigot or update to newer versions. Report this if it is a bug.");

                    }

                }

            });

			/*
			Bukkit.getPluginManager().registerEvents(new Listener() {
				@EventHandler
				public void onJoin(final PlayerJoinEvent e) {
					if (e.getPlayer().hasPermission("skript.admin")) {
						new Task(Skript.this, 0) {
							@Override
							public void run() {
								Updater.stateLock.readLock().lock();
								try {
									final Player p = e.getPlayer();
									assert p != null;
									if ((Updater.state == UpdateState.CHECKED_FOR_UPDATE || Updater.state == UpdateState.DOWNLOAD_ERROR) && Updater.latest.get() != null)
										info(p, "" + Updater.m_update_available);
								} finally {
									Updater.stateLock.readLock().unlock();
								}
							}
						};
					}
				}
			}, this);
			*/

            latestVersion = getInstance().getDescription().getVersion();

            if (!isEnabled())
                return;

            Bukkit.getScheduler().runTaskAsynchronously(getInstance(), () -> {
                try {
                    final String current = getInstance().getDescription().getVersion();

                    if (current == null || current.length() < 1)
                        return;

                    final String latest = getLatestVersion();

                    if (latest == null)
                        return;

                    final String latestTrimmed = latest.trim().toLowerCase(Locale.ENGLISH).replaceAll("\\s+", "".trim()).trim();
                    final String currentTrimmed = current.trim().toLowerCase(Locale.ENGLISH).replaceAll("\\s+", "".trim()).trim();

                    if (!latestTrimmed.equals(currentTrimmed)) {
                        if (!isEnabled())
                            return;
                        Bukkit.getScheduler().runTask(getInstance(), () -> Bukkit.getLogger().warning("[Skript] A new version of Skript has been found. Skript " + latest + " has been released. It's highly recommended to upgrade to the latest skript version. (you are using Skript " + current + ")"));
                        printDownloadLink();
                        updateAvailable = true;
                        latestVersion = latest;
                    } else {
                        if (!isEnabled())
                            return;
                        Bukkit.getScheduler().runTask(getInstance(), () -> Bukkit.getLogger().info("[Skript] You are using the latest version (" + latest + ") of the Skript. No new updates available. Thanks for using Skript!"));
                        printIssuesLink();
                    }
                } catch (final Throwable tw) {
                    if (!isEnabled())
                        return;
                    Bukkit.getScheduler().runTask(getInstance(), () -> Bukkit.getLogger().severe("[Skript] Unable to check updates, make sure you are using the latest version of Skript! (" + tw.getClass().getName() + ": " + tw.getLocalizedMessage() + ")"));
                    if (Skript.logHigh()) {
                        if (!isEnabled())
                            return;
                        Bukkit.getScheduler().runTask(getInstance(), () -> Bukkit.getLogger().log(Level.SEVERE, "[Skript] Unable to check updates", tw));
                    }
                    printDownloadLink();
                }
            });

        } catch (final Throwable tw) {

            exception(tw, Thread.currentThread(), (TriggerItem) null, "An error occured when enabling Skript");

        }

    }

    @Override
    public void onDisable() {
        if (disabled)
            return;
        disabled = true;

        if (Skript.logHigh())
            getLogger().info("Triggering on server stop events - if server freezes here, consider removing such events from skript code.");
        EvtSkript.onSkriptStop();

        if (Skript.logHigh())
            getLogger().info("Disabling scripts..");
        disableScripts();

        if (Skript.logHigh())
            getLogger().info("Unregistering tasks and event listeners..");
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((JavaPlugin) this);

        if (Skript.logHigh())
            getLogger().info("Freeing up the memory - if server freezes here, open a bug report issue at the github repository.");
        for (final Closeable c : closeOnDisable) {
            try {
                c.close();
            } catch (final Throwable tw) {
                Skript.exception(tw, "An error occurred while shutting down.", "This might or might not cause any issues.");
            }
        }

        // unset static fields to prevent memory leaks as Bukkit reloads the classes with a different classloader on reload
        // async to not slow down server reload, delayed to not slow down server shutdown
        final Thread t = newThread(() -> {
            try {
                Thread.sleep(10000);
            } catch (final InterruptedException ignored) {
            }
            try {
                final Field modifiers = Field.class.getDeclaredField("modifiers");
                modifiers.setAccessible(true);
                try (JarFile jar = new JarFile(getPluginFile())) {
                    for (final JarEntry e : new EnumerationIterable<>(jar.entries())) {
                        if (e.getName().endsWith(".class")) {
                            try {
                                final Class<?> c = Class.forName(e.getName().replace('/', '.').substring(0, e.getName().length() - ".class".length()), false, getBukkitClassLoader());
                                for (final Field f : c.getDeclaredFields()) {
                                    if (Modifier.isStatic(f.getModifiers()) && !f.getType().isPrimitive()) {
                                        if (Modifier.isFinal(f.getModifiers())) {
                                            modifiers.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                                        }
                                        f.setAccessible(true);
                                        f.set(null, null);
                                    }
                                }
                            } catch (final Throwable ex) {
                                if (testing() || Skript.logHigh())
                                    ex.printStackTrace();
                            }
                        }
                    }
                }
            } catch (final Throwable ex) {
                if (testing() || Skript.logHigh())
                    ex.printStackTrace();
            }
        }, "Skript cleanup thread");
        t.setPriority(Thread.MIN_PRIORITY);
        t.setDaemon(true);
        t.start();
    }

}
