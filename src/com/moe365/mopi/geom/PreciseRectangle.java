package com.moe365.mopi.geom;

import java.awt.Rectangle;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Comparator;

/**
 * Like a Rectangle, but immutable, and double precision
 * @since April 2016
 * @author mailmindlin
 */
public class PreciseRectangle implements Serializable {
	private static final long serialVersionUID = -4055498917888653239L;
	protected final double x, y, width, height;
	protected transient int hash;
	/**
	 * For deserializing
	 */
	protected PreciseRectangle() {
		this(0,0,0,0);
	}
	public PreciseRectangle(Rectangle rect) {
		this(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
	}
	public PreciseRectangle(double x, double y, double width, double height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
	
	public double getX() {
		return this.x;
	}
	
	public double getY() {
		return this.y;
	}
	
	public double getWidth() {
		return this.width;
	}
	
	public double getHeight() {
		return this.height;
	}
	
	public double getArea() {
		return width * height;
	}
	
	public PreciseRectangle scale(double factor) {
		return new PreciseRectangle(x, y, width * factor, height * factor);
	}
	
	public PreciseRectangle scale(double xf, double yf, double wf, double hf) {
		return new PreciseRectangle(x * xf, y * yf, width * wf, height * hf);
	}
	
	public static class PreciseRectangleAreaComparator implements Comparator<PreciseRectangle> {
		@Override
		public int compare(PreciseRectangle a, PreciseRectangle b) {
			return Double.compare(b.getArea(), a.getArea());
		}
	}
	
	@Override
	public int hashCode() {
		if (hash == 0) {
			ByteBuffer buf = ByteBuffer.allocate(32);
			buf.putDouble(getX());
			buf.putDouble(getY());
			buf.putDouble(getWidth());
			buf.putDouble(getHeight());
			hash = buf.hashCode();
		}
		return hash;
	}
}
