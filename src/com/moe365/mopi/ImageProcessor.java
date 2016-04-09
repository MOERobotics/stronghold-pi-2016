package com.moe365.mopi;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import au.edu.jcu.v4l4j.VideoFrame;

public class ImageProcessor implements Runnable {
	public static final int step = 6, tolerance = 70;
	AtomicBoolean imageLock = new AtomicBoolean(false);
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
	public ImageProcessor(int width, int height) {
		this.width = width;
		this.height = height;
		
		this.onRed = new short[height][width];
		this.onGreen = new short[height][width];
		this.onBlue = new short[height][width];
		
		this.offRed = new short[height][width];
		this.offGreen = new short[height][width];
		this.offBlue = new short[height][width];
		
		img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		this.thread = new Thread(this);
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
				calcDeltaAdv(img);
				if (!imageLock.compareAndSet(true, false))
					throw new IllegalStateException();
				try {
					ImageIO.write(img, "PNG", new File("img/delta" + i.getAndIncrement() + ".png"));
				} catch (Exception e) {
					e.printStackTrace();
				}
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
	@SuppressWarnings("unused")
	public void calcDeltaAdv(BufferedImage img) {
		boolean[][] processed = new boolean[height][width];
		System.out.println("Calculating...");
		img.flush();
		if (this.frameOff.get() == null || this.frameOn.get() == null)
			return;
		BufferedImage off = this.frameOff.get().getBufferedImage();
		BufferedImage on = this.frameOn.get().getBufferedImage();
		int[] pxOn = new int[3], pxOff = new int[3];
		for (int y=step; y < height - step; y+=step) {
			for (int x = step + ((y % (2 * step) == 0) ? step/2 : 0); x < width - step; x += step) {
				if (processed[y][x])
					continue;
				processed[y][x] = true;
				int px = 0;
				split(on.getRGB(x, y), pxOn);
				split(off.getRGB(x, y), pxOff);
				if (pxOn[0] - pxOff[0] > tolerance)
					px |= 0xFF0000;
				if (pxOn[1] - pxOff[1] > tolerance)
					px |= 0xFF00;
				if (pxOn[2] - pxOff[2] > tolerance)
					px |= 0xFF;
				img.setRGB(x, y, px);
				if (false && pxOn[1] - pxOff[1] > 50) {
					for (int y1 = Math.max(0, y-15); y1 < Math.min(height, y + 15); y1++) {
						for (int x1 = Math.max(0, x-15); x1 < Math.min(width, x + 15); x1++) {
							if (processed[y1][x1])
								continue;
							processed[y1][x1] = true;
							px = 0;
							split(on.getRGB(x1, y1), pxOn);
							split(off.getRGB(x1, y1), pxOff);
//							if (pxOn[0] - pxOff[0] > 50)
//								px |= 0xFF0000;
							if (pxOn[1] - pxOff[1] > 50)
								px |= 0xFF00;
//							if (pxOn[2] - pxOff[2] > 50)
//								px |= 0xFF;
							img.setRGB(x1, y1, px);
						}
					}
				}
			}
		}
		System.out.println("(done)");
	}
}
