package com.moe365.mopi.geom;

import java.awt.Point;

public class Polygon {
	PointNode start;
	public PointNode startAt(int x, int y) {
		return start = new PointNode(x, y);
	}
	public class PointNode extends Point2D {
		public PointNode next;
		public PointNode prev;
		public PointNode() {
			
		}
		public PointNode(int x, int y) {
			this.x = x;
			this.y = y;
		}
		public PointNode(PointNode prev, int x, int y) {
			this.prev = prev;
			this.x = x;
			this.y = y;
		}
		public PointNode(PointNode prev, int x, int y, PointNode next) {
			this.prev = prev;
			this.x = x;
			this.y = y;
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
		public PointNode insertNext(int x, int y) {
			PointNode node = new PointNode(this, x, y, this.next);
			this.next.prev = node;
			this.next = node;
			return this;
		}
		public PointNode insertNext(PointNode node) {
			this.next.prev = node;
			this.next = node;
			return this;
		}
		public PointNode removeNext() {
			this.next = this.next.next;
			this.next.prev = this;
			return this;
		}
	}
	public void addPoint(int leftX, int y) {
		// TODO Auto-generated method stub
		
	}
	public Point getStartingPoint() {
		// TODO Auto-generated method stub
		return null;
	}
}
