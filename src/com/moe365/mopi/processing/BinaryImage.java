package com.moe365.mopi.processing;

/**
 * A BinaryImage is a image that has a value of true or false at any given point.
 * You can think of it as a lazily-populated boolean array.
 * @author mailmindlin
 * @since April 2016 (v. 0.2.7)
 */
@FunctionalInterface
public interface BinaryImage {
	boolean test(int midpointX, int midpointY);

	default boolean test(double x, double y) {
		return test((int)Math.round(x), (int)Math.round(y));
	}
}
