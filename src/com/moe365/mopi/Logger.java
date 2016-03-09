package com.moe365.mopi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

public interface Logger {
	public static final int LOG_CRITICAL = 1;
	public static final int LOG_ERROR = 2;
	public static final int LOG_WARNING = 4;
	public static final int LOG_INFO = 8;
	public static final int LOG_VERBOSE = 16;
	LoggerStream getStreamFor(int logtype);
	LoggerStream getStreamFor(int logtype, int indent);
	
	void log(int level, String message, Object...args);
	void log(int level, String message);
	
	default void critical(String message) {
		log(LOG_CRITICAL, message);
	}
	default void error(String message) {
		log(LOG_ERROR, message);
	}
	default void warn(String message) {
		log(LOG_WARNING, message);
	}
	default void info(String message) {
		log(LOG_INFO, message);
	}
	default void verbose(String message) {
		log(LOG_VERBOSE, message);
	}
	
	public abstract class LoggerStream extends PrintStream {
		public LoggerStream(File file) throws FileNotFoundException {
			super(file);
		}
		public abstract LoggerStream indent();
		public abstract LoggerStream unindent();
	}
}
