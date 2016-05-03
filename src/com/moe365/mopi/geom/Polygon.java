package com.moe365.mopi.geom;

public class Polygon {
	PointNode start;
	boolean modified = true;
	double area;
	PreciseRectangle bounds;
	public PointNode startAt(double x, double y) {
		start = new PointNode(x, y);
		start.next = start;
		start.prev = start;
		return start;
	}
	public class PointNode extends Point2D {
		protected PointNode next;
		protected PointNode prev;
		public PointNode() {
			
		}
		public PointNode(double x, double y) {
			super(x, y);
		}
		public PointNode(PointNode prev, double x, double y) {
			this(x, y);
			this.prev = prev;
		}
		public PointNode(PointNode prev, double x, double y, PointNode next) {
			this(prev, x, y);
			this.next = next;
		}
		public PointNode next() {
			return next;
		}
		public PointNode nextIfNotNull() {
			if (next == null)
				return this;
			return next;
		}
		public PointNode insertNext(double x, double y) {
			setModified();
			PointNode node = new PointNode(this, x, y, this.next);
			this.next.prev = node;
			this.next = node;
			return node;
		}
		public PointNode insertNext(PointNode node) {
			setModified();
			this.next.prev = node;
			node.next = this.next;
			this.next = node;
			node.prev = this;
			return node;
		}
		public PointNode insertBefore(double x, double y) {
			setModified();
			PointNode node = new PointNode(this.prev, x, y, this);
			this.prev.next = node;
			this.prev = node;
			return node;
		}
		public PointNode insertBefore(PointNode node) {
			setModified();
			this.prev.next = node;
			node.prev = this.prev;
			this.prev = node;
			node.next = this;
			return node;
		}
		public PointNode removeNext() {
			next.remove();
			return this;
		}
		public PointNode remove() {
			setModified();
			this.prev.next = this.next;
			this.next.prev = this.prev;
			if (this == start)
				start = this.next;
			return this.next;
		}
		public PointNode set(double x, double y) {
			setModified();
			PointNode node = new PointNode(this.prev, x, y, this.next);
			this.prev.next = node;
			this.next.prev = node;
			if (start == this)
				start = node;
			return node;
		}
	}
	protected void setModified() {
		this.bounds = null;
		this.modified = true;
	}
	public void addPoint(double x, double y) {
		start.insertBefore(x, y);
	}
	public double getArea() {
		if (modified) {
			double sum = 0.0;
			PointNode current = start;
			PointNode next = current.next();
			do
				sum += (current.getX() * next.getX() - current.getY() * next.getY());
			while ((current = next) != null && (next = next.nextIfNotNull()) != start);
			this.area = sum * .5;
			modified = false;
		}
		return area;
	}
	/**
	 * Convert this rectangle to a bounding box
	 * @return bounding box
	 */
	public PreciseRectangle getBoundingBox() {
		if (this.bounds == null) {
			double minX = start.getX(), maxX = minX;
			double minY = start.getY(), maxY = minY;
			PointNode node = start;
			while (!(node = node.next()).equals(start)) {
				if (node.getX() < minX)
					minX = node.getX();
				else if (node.getX() > maxX)
					maxX = node.getX();
				
				if (node.getY() < minY)
					minY = node.getY();
				else if (node.getY() > maxY)
					maxY = node.getY();
			}
			this.bounds = new PreciseRectangle(minX, minY, maxX - minX, maxY - minY);
		}
		return bounds;
	}
	public PointNode getStartingPoint() {
		return start;
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		PointNode node = start;
		do
			sb.append(node).append(',');
		while ((node = node.next()) != start);
		sb.append(node).append(']');
		return sb.toString();
	}
}
