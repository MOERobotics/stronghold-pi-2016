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
		for (int y = 0; y < width; y+= minWidth) {
		}
	}
}
