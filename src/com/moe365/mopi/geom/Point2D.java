package com.moe365.mopi.geom;

public class Point2D {
	public int x;
	public int y;

	public Point2D() {

	}

	public Point2D(int x, int y) {
		this.x = x;
		this.y = y;
	}
	
	public Point2D subtract(Point2D other) {
		return new Point2D(this.x - other.x, this.y - other.y);
	}
	
	public Point2D abs() {
		return new Point2D(Math.abs(x), Math.abs(y));
	}
	
	public float getSlopeTo(Point2D other) {
		return ((float)(other.y - this.y)) / ((float)(other.x - this.x));
	}
	
	public Point2D getTaxicabCenter(Point2D other) {
		return new Point2D((this.x + other.x) / 2, (this.y + other.y) / 2);
	}
}
