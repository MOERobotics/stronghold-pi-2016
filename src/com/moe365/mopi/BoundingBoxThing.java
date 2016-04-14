package com.moe365.mopi;

import java.awt.Rectangle;
import java.util.List;

public class BoundingBoxThing {
	private static final int MINDIM = 8; // Smallest allowable dimension for any
											// side of box

	public static boolean boundingBoxRecursive(boolean[][] img, List<Rectangle> bbr, int limXmin, int limXmax,
			int limYmin, int limYmax, int boundXmin, int boundXmax, int boundYmin, int boundYmax) {
		if (((limXmax - limXmin) < MINDIM) || ((limYmax - limYmin) < MINDIM)) {
			return false; // box is too small, disregard
		} else {
			// try to split the box in half vertically or horizontally and call
			// recursively on the 2 halves
			int x, y;
			int splitX = limXmin + (limXmax - limXmin) / 2;
			boolean leftBool = false, rightBool = false;
			int leftOff = 1, rightOff = 1;
			for (x = splitX; x > limXmin; x--) // Left side of half split, test
												// all vertical lines till one
												// doesn't go thru a contour
			{
				leftOff = rightOff = 1;
				if (test(img, x, limYmin)) {
					leftBool = ((test(img, x - 1, limYmin)) && (test(img, x - 1, limYmin + 1)));
					rightBool = ((test(img, x + 1, limYmin)) && (test(img, x + 1, limYmin + 1)));
					if (leftBool && rightBool)
						continue;
					if (leftBool)
						leftOff = 0;
					if (rightBool)
						rightOff = 0;
				}
				if (test(img, x, limYmax)) {
					leftBool = ((test(img, x - 1, limYmax)) && (test(img, x - 1, limYmax - 1)));
					rightBool = ((test(img, x + 1, limYmax)) && (test(img, x + 1, limYmax - 1)));
					if (leftBool && rightBool)
						continue;
					if (leftBool)
						leftOff = 0;
					if (rightBool)
						rightOff = 0;
				}

				for (y = limYmin + 1; y < limYmax; y++) {

					if (test(img, x, y)) {
						leftBool = adjV(img, x - 1, y);
						rightBool = adjV(img, x + 1, y);
						if (leftBool && rightBool)
							break;
						if (leftBool)
							leftOff = 0;
						if (rightBool)
							rightOff = 0;
					}
				}
				if (y == limYmax) // valid split line, so split the rectangle
									// and return results
				{

					boolean firstHalf, secondHalf;
					if (0 == leftOff) // found a right edge
					{
						firstHalf = boundingBoxRecursive(img, bbr, limXmin, x, limYmin, limYmax, boundXmin, x, -1, -1);
					} else {
						firstHalf = boundingBoxRecursive(img, bbr, limXmin, x - leftOff, limYmin, limYmax, boundXmin,
								-1, -1, -1);
					}
					if (0 == rightOff) // found a left edge
					{
						secondHalf = (boundingBoxRecursive(img, bbr, x, limXmax, limYmin, limYmax, x, boundXmax, -1,
								-1));
					} else {
						secondHalf = (boundingBoxRecursive(img, bbr, x + rightOff, limXmax, limYmin, limYmax, -1,
								boundXmax, -1, -1));
					}
					return firstHalf || secondHalf; // looks bad but need to do
													// it this way or Java will
													// optimize out 2nd half of
													// the recursion!!
				}

			}
			if (boundXmin != x) // check for pixels on left edge of box
			{

				if ((test(img, x, limYmin))) {
					if ((test(img, x + 1, limYmin)) && (test(img, x + 1, limYmin + 1)))
						boundXmin = x;
				} else if ((test(img, x, limYmax))) {
					if ((test(img, x + 1, limYmax)) && (test(img, x + 1, limYmax - 1)))
						boundXmin = x;
				} else {
					for (y = limYmin + 1; y < limYmax; y++) {
						if (test(img, x, y)) {

							if (adjV(img, x + 1, y)) {
								boundXmin = x;
								break;
							}
						}
					}
				}
			}

			for (x = splitX + 1; x < limXmax; x++) // Right side of half split,
													// test all vertical lines
													// till one doesn't go thru
													// a contour
			{
				leftOff = rightOff = 1;
				if (test(img, x, limYmin)) {
					leftBool = ((test(img, x - 1, limYmin)) && (test(img, x - 1, limYmin + 1)));
					rightBool = ((test(img, x + 1, limYmin)) && (test(img, x + 1, limYmin + 1)));
					if (leftBool && rightBool)
						continue;
					if (leftBool)
						leftOff = 0;
					if (rightBool)
						rightOff = 0;
				}
				if (test(img, x, limYmax)) {
					leftBool = ((test(img, x - 1, limYmax)) && (test(img, x - 1, limYmax - 1)));
					rightBool = ((test(img, x + 1, limYmax)) && (test(img, x + 1, limYmax - 1)));
					if (leftBool && rightBool)
						continue;
					if (leftBool)
						leftOff = 0;
					if (rightBool)
						rightOff = 0;
				}
				for (y = limYmin + 1; y < limYmax; y++) {
					if (test(img, x, y)) {
						leftBool = adjV(img, x - 1, y);
						rightBool = adjV(img, x + 1, y);
						if (leftBool && rightBool)
							break;
						if (leftBool)
							leftOff = 0;
						if (rightBool)
							rightOff = 0;
					}
				}
				if (y == limYmax) // valid split line, so split the rectangle
									// and return results
				{

					boolean firstHalf, secondHalf;
					if (0 == leftOff) // found a right edge
					{
						firstHalf = boundingBoxRecursive(img, bbr, limXmin, x, limYmin, limYmax, boundXmin, x, -1, -1);
					} else {
						firstHalf = boundingBoxRecursive(img, bbr, limXmin, x - leftOff, limYmin, limYmax, boundXmin,
								-1, -1, -1);
					}
					if (0 == rightOff) // found a left edge
					{
						secondHalf = (boundingBoxRecursive(img, bbr, x, limXmax, limYmin, limYmax, x, boundXmax, -1,
								-1));
					} else {
						secondHalf = (boundingBoxRecursive(img, bbr, x + rightOff, limXmax, limYmin, limYmax, -1,
								boundXmax, -1, -1));
					}
					return firstHalf || secondHalf; // looks bad but need to do
													// it this way or Java will
													// optimize out 2nd half of
													// the recursion!!
				}

			}
			if (boundXmax != x) // check for pixels on right edge of box
			{

				if ((test(img, x, limYmin))) {
					if ((test(img, x - 1, limYmin)) && (test(img, x - 1, limYmin + 1)))
						boundXmax = x;
				} else if ((test(img, x, limYmax))) {
					if ((test(img, x - 1, limYmax)) && (test(img, x - 1, limYmax - 1)))
						boundXmax = x;
				} else {
					for (y = limYmin + 1; y < limYmax; y++) {
						if (test(img, x, y)) {

							if (adjV(img, x - 1, y)) {
								boundXmax = x;
								break;
							}
						}
					}
				}
			}
			int splitY = limYmin + (limYmax - limYmin) / 2;
			boolean topBool = false, botBool = false;
			int topOff = 1, botOff = 1;
			for (y = splitY; y > limYmin; y--) // Top side of half split, test
												// all horizontal lines till one
												// doesn't go thru a contour
			{
				topOff = botOff = 1;
				if (test(img, limXmin, y)) {
					topBool = ((test(img, limXmin, y - 1)) && (test(img, limXmin + 1, y - 1)));
					botBool = ((test(img, limXmin, y + 1)) && (test(img, limXmin + 1, y + 1)));
					if (topBool && botBool)
						continue;
					if (topBool)
						topOff = 0;
					if (botBool)
						botOff = 0;
				}
				if (test(img, limXmax, y)) {
					topBool = ((test(img, limXmax, y - 1)) && (test(img, limXmax - 1, y - 1)));
					botBool = ((test(img, limXmax, y + 1)) && (test(img, limXmax - 1, y + 1)));
					if (topBool && botBool)
						continue;
					if (topBool)
						topOff = 0;
					if (botBool)
						botOff = 0;
				}
				for (x = limXmin + 1; x < limXmax; x++) {
					if (test(img, x, y)) {
						topBool = adjH(img, x, y - 1);
						botBool = adjH(img, x, y + 1);
						if (topBool && botBool)
							break;
						if (topBool)
							topOff = 0;
						if (botBool)
							botOff = 0;
					}
				}
				if (x == limXmax) // valid split line, so split the rectangle
									// and return results
				{
					//

					boolean firstHalf, secondHalf;
					if (0 == topOff) // found a bottom edge
					{
						firstHalf = boundingBoxRecursive(img, bbr, limXmin, limXmax, limYmin, y, -1, -1, boundYmin, y);
					} else {
						firstHalf = boundingBoxRecursive(img, bbr, limXmin, limXmax, limYmin, y - topOff, -1, -1,
								boundYmin, -1);
					}
					if (0 == rightOff) // found a top edge
					{
						secondHalf = (boundingBoxRecursive(img, bbr, limXmin, limXmax, y, limYmax, -1, -1, y,
								boundYmax));
					} else {
						secondHalf = (boundingBoxRecursive(img, bbr, limXmin, limXmax, y + botOff, limYmax, -1, -1, -1,
								boundYmax));
					}
					return firstHalf || secondHalf; // looks bad but need to do
													// it this way or Java will
													// optimize out 2nd half of
													// the recursion!!
				}

			}
			if (boundYmin != y) // check for pixels on top edge of box
			{

				if (test(img, limXmin, y)) {
					if ((test(img, limXmin, y + 1)) && (test(img, limXmin + 1, y + 1)))
						boundYmin = y;
				} else if (test(img, limXmax, y)) {
					if ((test(img, limXmax, y + 1)) && (test(img, limXmax - 1, y + 1)))
						boundYmin = y;
				} else {
					for (x = limXmin + 1; x < limXmax; x++) {
						if (test(img, x, y)) {

							if (adjH(img, x, y + 1)) {
								boundYmin = y;
								break;
							}
						}
					}
				}
			}
			for (y = splitY + 1; y < limYmax; y++) // Bottom side of half split,
													// test all horizontal lines
													// till one doesn't go thru
													// a contour
			{
				topOff = botOff = 1;
				if (test(img, limXmin, y)) {
					topBool = ((test(img, limXmin, y - 1)) && (test(img, limXmin + 1, y - 1)));
					botBool = ((test(img, limXmin, y + 1)) && (test(img, limXmin + 1, y + 1)));
					if (topBool && botBool)
						continue;
					if (topBool)
						topOff = 0;
					if (botBool)
						botOff = 0;
				}
				if (test(img, limXmax, y)) {
					topBool = ((test(img, limXmax, y - 1)) && (test(img, limXmax - 1, y - 1)));
					botBool = ((test(img, limXmax, y + 1)) && (test(img, limXmax - 1, y + 1)));
					if (topBool && botBool)
						continue;
					if (topBool)
						topOff = 0;
					if (botBool)
						botOff = 0;
				}
				for (x = limXmin + 1; x < limXmax; x++) {
					if (test(img, x, y)) {
						topBool = adjH(img, x, y - 1);
						botBool = adjH(img, x, y + 1);
						if (topBool && botBool)
							break;
						if (topBool)
							topOff = 0;
						if (botBool)
							botOff = 0;
					}
				}
				if (x == limXmax) // valid split line, so split the rectangle
									// and return results
				{

					boolean firstHalf, secondHalf;
					if (0 == topOff) // found a bottom edge
					{
						firstHalf = boundingBoxRecursive(img, bbr, limXmin, limXmax, limYmin, y, -1, -1, boundYmin, y);
					} else {
						firstHalf = boundingBoxRecursive(img, bbr, limXmin, limXmax, limYmin, y - topOff, -1, -1,
								boundYmin, -1);
					}
					if (0 == rightOff) // found a top edge
					{
						secondHalf = (boundingBoxRecursive(img, bbr, limXmin, limXmax, y, limYmax, -1, -1, y,
								boundYmax));
					} else {
						secondHalf = (boundingBoxRecursive(img, bbr, limXmin, limXmax, y + botOff, limYmax, -1, -1, -1,
								boundYmax));
					}
					return firstHalf || secondHalf; // looks bad but need to do
													// it this way or Java will
													// optimize out 2nd half of
													// the recursion!!
				}

			}
			if (boundYmax != y) // check for pixels on bottom edge of box
			{

				if (test(img, limXmin, y)) {
					if ((test(img, limXmin, y + 1)) && (test(img, limXmin + 1, y + 1)))
						boundYmax = y;
				} else if (test(img, limXmax, y)) {
					if ((test(img, limXmax, y + 1)) && (test(img, limXmax - 1, y + 1)))
						boundYmax = y;
				} else {
					for (x = limXmin + 1; x < limXmax; x++) {
						if (test(img, x, y)) {

							if (adjH(img, x, y - 1)) {
								boundYmax = y;
								break;
							}
						}
					}
				}
			}

			if ((boundXmin < boundXmax) && (boundXmin > -1) && (boundYmin < boundYmax) && (boundYmin > -1)) {
				bbr.add(new Rectangle(boundXmin, boundYmin, boundXmax - boundXmin, boundYmax - boundYmin));
				return true;
			}
		}
		return false;
	}

	// used to test a 3 pixel vertical line to determine if it is adjacent.
	// Middle pixel must meet threshold, plus on of the two others
	private static final boolean adjV(boolean[][] img, int x, int y) {
		return ((test(img, x, y)) && ((test(img, x, y - 1)) || (test(img, x, y + 1))));
	}

	// used to test a 3 pixel horizontal line to determine if it is adjacent.
	// Middle pixel must meet threshold, plus on of the two others
	private static final boolean adjH(boolean[][] img, int x, int y) {
		return ((test(img, x, y)) && ((test(img, x - 1, y)) || (test(img, x + 1, y))));
	}

	private static final boolean test(boolean[][] img, int x, int y) {
		return img[y][x];
	}
}
