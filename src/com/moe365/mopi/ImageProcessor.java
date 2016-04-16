package com.moe365.mopi;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.moe365.mopi.geom.PreciseRectangle;

import au.edu.jcu.v4l4j.VideoFrame;

public class ImageProcessor implements Runnable {
	public static final int step = 1, tolerance = 70;
	
	public static byte saturateByte(int num) {
		return (byte) (num > 0xFF ? 0xFF : num < 0 ? 0 : num);
	}
	
	public boolean saveDiff = false;
	AtomicBoolean imageLock = new AtomicBoolean(false);
	protected final RoboRioClient client;
	/**
	 * A frame where the flash is off
	 */
	final AtomicReference<VideoFrame> frameOff = new AtomicReference<>();
	/**
	 * A frame where the flash is on
	 */
	final AtomicReference<VideoFrame> frameOn = new AtomicReference<>();
	final int width, height;
	final short[][] onRed, onGreen, onBlue, offRed, offGreen, offBlue;
	BufferedImage img;
	AtomicInteger i = new AtomicInteger(0);
	Thread thread;
	public AtomicReference<List<Rectangle>> lastResult = new AtomicReference<>(Collections.emptyList());
	public ImageProcessor(int width, int height, RoboRioClient client) {
		this.width = width;
		this.height = height;
		this.client = client;
		
		this.onRed = new short[height][width];
		this.onGreen = new short[height][width];
		this.onBlue = new short[height][width];
		
		this.offRed = new short[height][width];
		this.offGreen = new short[height][width];
		this.offBlue = new short[height][width];
		
		img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		this.thread = new Thread(this);
		thread.setName("ProcessorThread-" + thread.getId());
	}
	public ImageProcessor start() {
		thread.start();
		return this;
	}
	public boolean update(VideoFrame frame, boolean flash) {
		if (imageLock.get()) {
			frame.recycle();
			return false;
		}
		VideoFrame oldFrame = (flash ? frameOn : frameOff).getAndSet(frame);
		if (oldFrame != null)
			oldFrame.recycle();
		return true;
	}
	@Override
	public void run() {
		try {
			while (!Thread.interrupted()) {
				while (frameOff.get() == null || frameOn.get() == null)
					Thread.sleep(100);
				if (!imageLock.compareAndSet(false, true))
					throw new IllegalStateException();
				//check again, just to be safe
				if (frameOff.get() != null && frameOn.get() != null) {
					if (saveDiff)
						calcDeltaWithDiff(img);
					else
						calcDeltaAdv();
				}
				if (!imageLock.compareAndSet(true, false))
					throw new IllegalStateException();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		}
	}
	public void read(VideoFrame frame, short[][] red, short[][] green, short[][] blue) {
		System.out.println("Reading...");
		BufferedImage img = frame.getBufferedImage();
		int[] row = new int[width];
		for (int y = 0; y < height; y++) {
			short[] r = red[y], g = green[y], b = blue[y];
			img.getRGB(0, y, width, 1, row, 0, 0);
			for (int x = 0; x < width; x++) {
				int px = row[x];
				r[x] = (short) ((px >>> 16) & 0xFF);
				g[x] = (short) ((px >>>  8) & 0xFF);
				b[x] = (short) (px & 0xFF);
			}
		}
		System.out.println("Done reading");
	}
	@SuppressWarnings("unused")
	@Deprecated
	public void calcDeltaSimple(BufferedImage img) {
		System.out.println("Calculating...");
		img.flush();
		int[] pixels = new int[width];
		for (int y=0; y < height; y++) {
			short[] r = onRed[y], R = offRed[y], g = onGreen[y], G = offGreen[y], b = onBlue[y], B = offBlue[y];
			for (int x=0; x<width; x++) {
				int dR = 0;//Math.min(Math.max(0, r[x] - R[x]), 255);
				int dG = (g[x] - G[x]) > 30 ? 255 : 0 ;
				int dB = 0;//Math.abs(B[x] - b[x]);
				pixels[x] = ((dR & 0xFF) << 16) | ((dG & 0xFF) << 8) | (dB & 0xFF);
			}
			img.setRGB(0, y, width, 1, pixels, 0, 0);
		}
		System.out.println("(done)");
	}
	public void split(int px, int[] buf) {
		buf[0] = (px >>> 16) & 0xFF;
		buf[1] = (px >> 8) & 0xFF;
		buf[2] = px & 0xFF;
	}
	public void calcDeltaWithDiff(BufferedImage img) {
		// calculated yet)
		boolean[][] processed = new boolean[height][width];
		// boolean array of the results. A cell @ result[y][x] is only
		// valid if processed[y][x] is true.
		boolean[][] result = new boolean[height][width];
		System.out.println("Calculating...");
		img.flush();
		if (this.frameOff.get() == null || this.frameOn.get() == null)
			return;
		BufferedImage off = this.frameOff.get().getBufferedImage();
		BufferedImage on = this.frameOn.get().getBufferedImage();
		System.out.println("CM: " + on.getColorModel());
		System.out.println("CMCL: " + on.getColorModel().getClass());
		int[] pxOn = new int[3], pxOff = new int[3];
		for (int y=step; y < height - step; y+=step) {
			for (int x = step + ((y % (2 * step) == 0) ? step/2 : 0); x < width - step; x += step) {
				if (processed[y][x])
					continue;
				processed[y][x] = true;
				int px = 0;
				split(on.getRGB(x, y), pxOn);
				split(off.getRGB(x, y), pxOff);
				int dR = pxOn[0] - pxOff[0];
				int dG =  pxOn[1] - pxOff[1];
				int dB =  pxOn[2] - pxOff[2];
				if (dG > tolerance)
					px = (saturateByte(dR) << 16) | (saturateByte(dG) << 8) | saturateByte(dB);
				/*if (dG > tolerance) {
					for (int i = y - step/2; i < y + step/2; i++) {
						for (int j = x - step/2; j < x + step/2; x++) {
							int rA = on.getRGB(j, i);
							int rB = on.getRGB(j, i);
							img.setRGB(j, i, (((rA >> 16) & 0xFF) - ((rB >> 16) & 0xFF)<<16));
						}
					}
				}*/
//				px = ((Math.max(pxOn[0] - pxOff[0], 0) & 0xFF) << 16) | ((Math.max(pxOn[1] - pxOff[1], 0) & 0xFF) << 8) | (Math.max(pxOn[2] - pxOff[2], 0) & 0xFF);
//				if (dR > tolerance)
//					px |= 0xFF0000;
				if (dG > tolerance)
					result[y][x] = true;
//				if (dB > tolerance)
//					px |= 0xFF;
				img.setRGB(x, y, px);
				/*if (dG > 50) {
					for (int y1 = Math.max(0, y-15); y1 < Math.min(height, y + 15); y1++) {
						for (int x1 = Math.max(0, x-15); x1 < Math.min(width, x + 15); x1++) {
							if (processed[y1][x1])
								continue;
							processed[y1][x1] = true;
							px = 0;
							split(on.getRGB(x1, y1), pxOn);
							split(off.getRGB(x1, y1), pxOff);
							if (pxOn[0] - pxOff[0] > 50)
								px |= 0xFF0000;
							if (pxOn[1] - pxOff[1] > 50)
								px |= 0xFF00;
							if (pxOn[2] - pxOff[2] > 50)
								px |= 0xFF;
							img.setRGB(x1, y1, px);
						}
					}
				}*/
			}
		}
		try {
			File file = new File("img/delta" + i.getAndIncrement() + ".png");
			System.out.println("Saving image to " + file);
			ImageIO.write(img, "PNG", file);
		} catch (Exception e) {
			e.printStackTrace();
		}
		processBooleanMap(result);
	}
	@SuppressWarnings("unused")
	public void calcDeltaAdv() {
		// Whether the value of any cell in result[][] is valid (has been
		// calculated yet)
		boolean[][] processed = new boolean[height][width];
		// boolean array of the results. A cell @ result[y][x] is only
		// valid if processed[y][x] is true.
		boolean[][] result = new boolean[height][width];
		System.out.println("Calculating...");
		if (this.frameOff.get() == null || this.frameOn.get() == null)
			return;
		BufferedImage off = this.frameOff.get().getBufferedImage();
		BufferedImage on = this.frameOn.get().getBufferedImage();
		int[] pxOn = new int[3], pxOff = new int[3];
		for (int y = step; y < height - step; y += step) {
			for (int x = step + ((y % (2 * step) == 0) ? step/2 : 0); x < width - step; x += step) {
				if (processed[y][x])
					continue;
				processed[y][x] = true;
				split(on.getRGB(x, y), pxOn);
				split(off.getRGB(x, y), pxOff);
				int dR = pxOn[0] - pxOff[0];
				int dG =  pxOn[1] - pxOff[1];
				int dB =  pxOn[2] - pxOff[2];
//				if (dR > tolerance)
//					px |= 0xFF0000;
				if (dG > tolerance)//TODO fix
					result[y][x] = true;
//				if (dB > tolerance)
//					px |= 0xFF;
			}
		}
		processBooleanMap(result);
	}
	protected void processBooleanMap(boolean[][] processed) {
		// List of the rectangles to be generated by boundingBoxRecursive
		List<PreciseRectangle> rectangles;
		{
			List<Rectangle> upRects = new LinkedList<>();
			BoundingBoxThing.boundingBoxRecursive(processed, upRects, 0, processed[0].length - 1, 0, processed.length - 1, -1, -1, -1, -1);
			upRects.sort((a,b)->(Double.compare(b.getWidth() * b.getHeight(), a.getWidth() * a.getHeight())));
			final double wf = 1.0 / ((double) width);
			final double hf = 1.0 / ((double) height);
			rectangles = upRects.stream()
					.map(r->(new PreciseRectangle(r).scale(wf, hf, wf, hf)))
					.collect(Collectors.toList());
		}
		for (PreciseRectangle rectangle : rectangles) {
			double x = rectangle.getX();
			double y = rectangle.getY();
			double rw = rectangle.getWidth();
			double rh = rectangle.getHeight();
			System.out.println("=>X: " + x + "; Y: " + y + "; W: " + rw + "; H:" + rh);
		}
		try {
			if (client != null) {
				if (rectangles.isEmpty()) {
					client.writeNoneFound();
				} else {
					PreciseRectangle rect0 = rectangles.get(0);
					double x0 = rect0.getX();
					double y0 = rect0.getY();
					double w0 = rect0.getWidth();
					double h0 = rect0.getHeight();
					if (rectangles.size() == 1) {
						client.writeOneFound(x0, y0, w0, h0);
					} else {
						PreciseRectangle rect1 = rectangles.get(1);
						double x1 = rect1.getX();
						double y1 = rect1.getY();
						double w1 = rect1.getWidth();
						double h1 = rect1.getHeight();
						client.writeTwoFound(x0, y0, w0, h0, x1, h1, w1, h1);
					}
				}
			}
		} catch (IOException | NullPointerException e) {
			e.printStackTrace();
		}
		if (Main.httpServer != null)
			Main.httpServer.offerRectangles(rectangles);
		System.out.println("(done)");
	}
}
