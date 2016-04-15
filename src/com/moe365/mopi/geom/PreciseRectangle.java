package com.moe365.mopi.geom;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.Comparator;

public class PreciseRectangle {
	protected final double x, y, width, height;
	protected int hash;
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
