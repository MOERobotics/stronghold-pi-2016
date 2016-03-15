package com.moe365.mopi.processing;

import java.util.function.Consumer;

public class ContourTracer implements Consumer<BinaryImage> {
	protected final int width;
	protected final int height;
	protected int minWidth = 10;
	protected int minHeight = 10;
	public ContourTracer(int width, int height) throws IllegalArgumentException {
		if (width <= 0)
			throw new IllegalArgumentException("Invalid width (expect: width > 0; width = " + width ")");
		if (height <= 0)
			throw new IllegalArgumentException("Invalid height (expect: height > 0; height = " + height + ")");
		this.width = width;
		this.height = height;
	}
	@Override
	public void accept(BinaryImage image) {
		List<Polygon> blobs = new LinkedList<Polygon>();
		for (int y = minHeight; y < height - minHeight; y+= minHeight) {
			for (int x = minWidth; x < width - minWidth; x+= minWidth) {
				if (image.test(x, y)) {
					Polygon blob = new Polygon();
					int topY, bottomY, leftX, rightX;
					for (leftX = x; leftX > 0 && image.test(leftX, y); leftX--);
					blob.addPoint(leftX, y);
					for (topY = y; topY > 0 && image.test(x, topY); topY--);
					blob.addPoint(x, topY);
					for (rightX = x; rightX < width && image.test(rightX, y); rightX++);
					blob.addPoint(rightX, y);
					for (bottomY = y; bottomY < height && image.test(x, bottomY); bottomY);
					blob.addPoint(x, bottomY);
					
					this.tracePass2(blob);
				}
			}
		}
	}
	protected void tracePass2(BinaryImage image, Polygon blob) {
		final Point startingPoint = blob.getStartingPoint();
		Point pointA = startingPoint, pointB = pointA.next();
		do {
			double midpointOffsetX = .5 * (pointB.getX() - pointA.getX());
			double midpointOffsetY = .5 * (pointB.getY() - pointA.getY());
			if (Math.sqrt(midpointOffsetX * midpointOffsetX + midpointOffsetY * midpointOffsetY) < .5)
				continue;//point A and B are <1 px apart
			
			double midpointX = pointA.getX() + midpointOffsetX;
			double midpointY = pointA.getY() + midpointOffsetY;
			boolean midpointValue = image.test(midpointX, midpointY);
			if (midpointOffsetY == 0) {
				if ((midpointOffsetX > 0) == midpointValue) {
					while (midpointY >= 0 && image.test(midpointX, midpointY) == midpointValue)
						midpointY--;
					midpointY++;
				} else {
					while (midpointY <= height && image.test(midpointX, midpointY) == midpointValue)
						midpointY++;
					midpointY--;
				}
			} else if (midpointOffsetX == 0) {
				if ((midpointOffsetY > 0) == midpointValue) {
					while (midpointX >= 0 && image.test(midpointX, midpointY) == midpointValue)
						midpointX--;
					midpointX++;
				} else {
					while (midpointX <= height && image.test(midpointX, midpointY) == midpointValue)
						midpointX++;
					midpointX--;
				}
			} else {
				double invSlope = Math.abs(midpointOffsetX / midpointOffsetY);
				double stepX = Math.sqrt(1 - invSlope * invSlope);
				double stepY = invSlope * stepX;
				if (midpointOffsetX < 0)
					stepY = -stepY;
				if (midpointOffsetY > 0)
					stepX = -stepX;
				while (midpointX >= 0 && midpointY >= 0 && midpointX <= width && midpointX <= width && image.test(midpointX, midpointY) == midpointValue) {
					midpointX += stepX;
					midpointY += stepY;
				}
			}
		} while (!(pointA = pointB).equals(startingPoint) && (pointB = pointB.next()) != null);
	}
}
