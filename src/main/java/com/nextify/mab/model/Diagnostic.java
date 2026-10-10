package com.nextify.mab.model;

/** A problem found while linking mapper interfaces and SQL. */
public final class Diagnostic {

    public enum Level { ERROR, WARN, INFO }

    private final Level level;
    private final String location;
    private final String message;

    public Diagnostic(Level level, String location, String message) {
        this.level = level;
        this.location = location;
        this.message = message;
    }

    public static Diagnostic error(String location, String message) {
        return new Diagnostic(Level.ERROR, location, message);
    }

    public static Diagnostic warn(String location, String message) {
        return new Diagnostic(Level.WARN, location, message);
    }

    public static Diagnostic info(String location, String message) {
        return new Diagnostic(Level.INFO, location, message);
    }

    public Level getLevel() {
        return level;
    }

    public String getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

    /** ERROR and WARN are considered actionable; INFO is not. */
    public boolean isActionable() {
        return level != Level.INFO;
    }

    @Override
    public String toString() {
        return "[" + level + "] " + location + ": " + message;
    }
}
