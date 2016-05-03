package com.moe365.mopi.processing;

/**
 * A BinaryImage is a image that has a value of true or false at any given point.
 * You can think of it as a lazily-populated boolean array.
 * @author mailmindlin
 * @since April 2016 (v. 0.2.7)
 */
@FunctionalInterface
public interface BinaryImage {
	/**
	 * Get the value of the image at the given coordinate
	 * @param x x coordinate of pixel to test
	 * @param y y coordinate of pixel to test
	 * @return the value of the image at the point
	 */
	boolean test(int x, int y);

	/**
	 * Test the coordinate (x, y) by rounding the doubles.
	 * @param x x coordinate of pixel to test
	 * @param y y coordinate of pixel to test
	 * @return the value of the image at the point
	 * @see #test(int, int)
	 */
	default boolean test(double x, double y) {
		return test((int)Math.round(x), (int)Math.round(y));
	}
}
