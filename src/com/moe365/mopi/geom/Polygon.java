package com.moe365.mopi.geom;

public class Polygon {
	PointNode start;
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
			PointNode node = new PointNode(this, x, y, this.next);
			this.next.prev = node;
			this.next = node;
			return node;
		}
		public PointNode insertNext(PointNode node) {
			this.next.prev = node;
			node.next = this.next;
			this.next = node;
			node.prev = this;
			return node;
		}
		public PointNode insertBefore(double x, double y) {
			PointNode node = new PointNode(this.prev, x, y, this);
			this.prev.next = node;
			this.prev = node;
			return node;
		}
		public PointNode insertBefore(PointNode node) {
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
			this.prev.next = this.next;
			this.next.prev = this.prev;
			if (this == start)
				start = this.next;
			return this.next;
		}
		public PointNode set(double x, double y) {
			PointNode node = new PointNode(this.prev, x, y, this.next);
			this.prev.next = node;
			this.next.prev = node;
			if (start == this)
				start = node;
			return node;
		}
	}
	public void addPoint(double x, double y) {
		start.insertBefore(x, y);
	}
	public double getArea() {
		double sum = 0.0;
		PointNode current = start;
		PointNode next = current.next();
		do
			sum += (current.getX() * next.getY() - current.getY() * next.getY());
		while ((current = next) != null && (next = next.nextIfNotNull()) != start);
		return sum * .5;
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
