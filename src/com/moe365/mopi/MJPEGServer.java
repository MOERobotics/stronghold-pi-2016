package com.moe365.mopi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.divisors.projectcuttlefish.httpserver.util.ByteUtils;
import com.divisors.projectcuttlefish.httpserver.util.ByteUtils.ByteBufferTokenizer;
import com.moe365.mopi.geom.Polygon;
import com.moe365.mopi.geom.PreciseRectangle;

import au.edu.jcu.v4l4j.VideoFrame;

/**
 * HTTP server used to stream data to web browsers.
 * @author mailmindlin
 * @since 0.0.1
 */
public class MJPEGServer implements Runnable {
	/**
	 * Main HTTP page (HTML) bytes
	 */
	public static final ByteBuffer HTTP_PAGE_MAIN;
	/**
	 * 404 page bytes
	 */
	public static final ByteBuffer HTTP_PAGE_404;
	/**
	 * MJPEG header (sent at the top of the response) bytes
	 */
	public static final ByteBuffer HTTP_HEAD_MJPEG;
	/**
	 * MJPEG frame header (sent before each frame) bytes
	 */
	public static final ByteBuffer HTTP_FRAME_MJPEG;
	/**
	 * Generic 200 OK response bytes
	 */
	public static final ByteBuffer HTTP_PAGE_200;
	/**
	 * SSE header (sent at the top of the response) bytes.
	 */
	public static final ByteBuffer HTTP_SSE_HEAD;
	
	static {
		HTTP_PAGE_MAIN = loadHttp("main");
		HTTP_PAGE_404 = loadHttp("404");
		HTTP_HEAD_MJPEG = loadHttp("mjpeg-head");
		HTTP_FRAME_MJPEG = loadHttp("mjpeg-frame-head");
		HTTP_PAGE_200 = loadHttp("200");
		HTTP_SSE_HEAD = loadHttp("sse-head");
	}
	/**
	 * Load file from the <code>resources</code> package inside the jar.
	 * @param name name of file
	 * @return bytebuffer containing 
	 */
	protected static ByteBuffer loadHttp(String name) {
		String tmp;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(Main.class.getResourceAsStream("/resources/" + name + ".http")))) {
			StringBuilder sb = new StringBuilder();
			while (br.ready())
				sb.append(br.readLine().replace("\t","").replace("\\t", "\t").replace("\\r", "\r").replace("\\n", "\n"));
			tmp = sb.toString();
		} catch (IOException e) {
			e.printStackTrace();
			tmp = "Error loading resource";
		}
		byte[] tmpBytes = tmp.getBytes(StandardCharsets.UTF_8);
		ByteBuffer result = ByteBuffer.allocateDirect(tmpBytes.length);
		result.put(tmpBytes);
		result.flip();
		return result;
	}
	
	ServerSocketChannel serverSocket;
	Selector selector;
	SocketAddress address;
	ExecutorService executor;
	protected AtomicLong channelId = new AtomicLong(0);
	protected ByteBuffer readBuffer = ByteBuffer.allocateDirect(1024 * 10);
	/**
	 * Buffer for queuing frames to be written in
	 */
	protected ByteBuffer jpegWriteBuffer = ByteBuffer.allocateDirect(1024 * 100);
	protected AtomicBoolean isJpegBufferLocked = new AtomicBoolean(false);
	protected AtomicBoolean isImageAvailable = new AtomicBoolean(false);
	protected volatile ByteBuffer rectangleWriteBuffer = null;
	protected AtomicBoolean areRectanglesAvailable = new AtomicBoolean(false);
	protected ConcurrentHashMap<Long, SocketChannel> channelMap = new ConcurrentHashMap<>();
	protected volatile Set<Long> mjpegChannels = ConcurrentHashMap.newKeySet();
	protected volatile Set<Long> jsonSSEChannels = ConcurrentHashMap.newKeySet();
	protected AtomicBoolean shouldBeRunning = new AtomicBoolean(false);

	/**
	 * Create server with address
	 * @param address
	 * @throws IOException
	 */
	public MJPEGServer(SocketAddress address) throws IOException {
		this.address = address;

		this.selector = Selector.open();
		this.serverSocket = ServerSocketChannel.open();
		this.serverSocket.configureBlocking(false);
		this.serverSocket.socket().setReuseAddress(true);
		this.serverSocket.socket().bind(address);
		this.serverSocket.register(this.selector, SelectionKey.OP_ACCEPT);
	}

	/**
	 * Set ExecutorService for server to spawn thread(s) on.
	 * @param executor service to run on
	 * @return self
	 */
	public MJPEGServer runOn(ExecutorService executor) {
		this.executor = executor;
		return this;
	}

	/**
	 * Start the server
	 */
	public void start() {
		System.out.println("Starting MJPEG server @ " + address.toString());
		if (executor == null) {
			run();
		} else {
			executor.submit(this);
		}
	}

	@Override
	public void run() {
		shouldBeRunning.set(true);
		try {
			while (shouldBeRunning.get() && !Thread.interrupted()) {
				if (selector.select() > 0) {
					Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
					while (keys.hasNext()) {
						SelectionKey key = keys.next();
						keys.remove();
						if (!key.isValid()) {
							System.out.println("Invalid key");
							continue;
						}
						if (key.isAcceptable()) {
							accept(key);
							continue;
						}
						if (key.isReadable()) {
							read(key);
							continue;
						}
					}
				}
				attemptWriteNextFrame();
				attemptUpdateSSE();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Server stopped.");
	}
	
	/**
	 * Offer a frame to be served in the MJPEG stream. After calling this
	 * method, the frame CAN be recycled.
	 * @param frame Frame to add to the MJPEG stream
	 */
	public void offerFrame(VideoFrame frame) {
		if (isImageAvailable.get() || (!isJpegBufferLocked.compareAndSet(false, true))) {
			System.err.print('D');
			return;
		}
		try {
			jpegWriteBuffer.clear();
			jpegWriteBuffer.put(Integer.toString(frame.getFrameLength()).getBytes());
			jpegWriteBuffer.put(new byte[]{'\r','\n','\r','\n'});
			System.out.println("Frame size: " + (frame.getFrameLength()/1024));
			jpegWriteBuffer.put(frame.getBytes(), 0, frame.getFrameLength());
			jpegWriteBuffer.flip();
			isImageAvailable.compareAndSet(false, true);
			selector.wakeup();
		} finally {
			isJpegBufferLocked.compareAndSet(true, false);
		}
	}
	
	public void offerRectangles(List<PreciseRectangle> rectangles) {
		if (jsonSSEChannels.size() == 0)
			//Don't waste time on building the data, if nobody's there to listen
			return;
		StringBuffer sb = new StringBuffer("event: udrects\r\ndata: [");
		if (rectangles != null && (!rectangles.isEmpty())) {
			for (PreciseRectangle rectangle : rectangles) {
				sb.append("[0,")
					.append(rectangle.getX()).append(',')
					.append(rectangle.getY()).append(',')
					.append(rectangle.getWidth()).append(',')
					.append(rectangle.getHeight()).append("],");
			}
			sb.delete(sb.length() - 1, sb.length());
		}
		sb.append("]\r\n\r\n");
		rectangleWriteBuffer = ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8));
		areRectanglesAvailable.set(true);
	}
	public void offerPolygons(List<Polygon> polygons) {
		if (jsonSSEChannels.size() == 0)
			//Don't waste time on building the data, if nobody's there to listen
			return;
		StringBuffer sb = new StringBuffer("event: udrects\r\ndata: [");
		if (polygons != null && (!polygons.isEmpty())) {
			for (Polygon polygon : polygons) {
				sb.append("[1,");
				Polygon.PointNode node = polygon.getStartingPoint();
				do {
					sb.append(node.getX()).append(',')
						.append(node.getY()).append(',');
				} while (!(node = node.next()).equals(polygon.getStartingPoint()));
				sb.delete(sb.length() - 1, sb.length())
					.append("],");
			}
			sb.delete(sb.length() - 1, sb.length());
		}
		sb.append("]\r\n\r\n");
		rectangleWriteBuffer = ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8));
		areRectanglesAvailable.set(true);
		System.out.println("Pushed polygons");
	}
	protected void attemptUpdateSSE() {
		if (this.jsonSSEChannels.isEmpty() || (!areRectanglesAvailable.get()))
			return;
		System.out.println("WRITING SSE to " + jsonSSEChannels.size() + " channels");
		try {
			ByteBuffer buffer = this.rectangleWriteBuffer;
			if (buffer == null)
				return;
			for (Long id : this.jsonSSEChannels) {
				SocketChannel channel = this.channelMap.get(id);
				if (channel == null || !channel.isOpen()) {
					this.jsonSSEChannels.remove(id);
					continue;
				}
				try {
					if (channel.write(buffer.duplicate()) < 0)
						this.jsonSSEChannels.remove(id);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} finally {
			areRectanglesAvailable.set(false);
		}
	}
	protected void attemptWriteNextFrame() {
		if (this.mjpegChannels.size() == 0 || !(this.isImageAvailable.get() && isJpegBufferLocked.compareAndSet(false, true)))
			return;
		System.out.print('W');
		System.out.print(this.mjpegChannels.size());
//		System.out.print('/');
//		System.out.print(Math.round(writeBuffer.limit()/102.4)/10.0);
		try {
			for (Long id : this.mjpegChannels) {
				SocketChannel channel = this.channelMap.get(id);
				if (channel == null || !channel.isOpen()) {
					this.mjpegChannels.remove(id);
					continue;
				}
				try {
					if (channel.write(HTTP_FRAME_MJPEG.duplicate()) < 0 || channel.write(jpegWriteBuffer) < 0)
						this.mjpegChannels.remove(id);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} finally {
			isImageAvailable.set(false);
			isJpegBufferLocked.set(false);
		}
	}

	private void accept(SelectionKey key) throws IOException {
		SocketChannel socket = serverSocket.accept();
		System.out.println("Accepting socket from " + socket.socket().getInetAddress());

		// setup socket
		socket.configureBlocking(false);
		socket.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
		socket.setOption(StandardSocketOptions.TCP_NODELAY, true);

		long id = this.channelId.incrementAndGet();
		this.channelMap.put(id, socket);

		socket.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, id);
	}

	private void read(SelectionKey key) throws IOException {
		long id = (long) key.attachment();
		SocketChannel channel = channelMap.get(id);

		// read from socket
		this.readBuffer.clear();
		int read;// number of bytes read
		try {
			read = channel.read(this.readBuffer);
			// if read<0, then the channel is closed
			if (read < 0) {
				// close channel
				channelMap.remove(id);
				System.out.println("\tClosing channel...");
				channel.close();
				return;
			}
		} catch (IOException e) {
			channelMap.remove(id);
			channel.close();
			e.printStackTrace();
			return;
		}
		
		this.readBuffer.flip();
		String[] header = parse(this.readBuffer);
		System.out.println("Got request: " + header[1]);
		if (header[1].endsWith("jpg")) {
			// send the video stream
			System.out.println("JPG stream");
			channel.write(MJPEGServer.HTTP_HEAD_MJPEG.duplicate());
			mjpegChannels.add(id);
		} else if (header[1].endsWith("ico")) {
			//Send a 404, because we have no icon
			System.out.println("Requested ICO");
			channel.write(MJPEGServer.HTTP_PAGE_404.duplicate());
			channel.close();
			channelMap.remove(id);
		} else if (header[1].endsWith("cvsn")) {
			//Enable ComputerVision (tm)
			Main.enableProcessor();
			channel.write(MJPEGServer.HTTP_PAGE_200.duplicate());
			ByteBuffer tmp = ByteBuffer.allocate(4);
			tmp.putChar(Main.processorEnabled ? '1' : '0');
			channel.write(tmp);
			channel.close();
		} else if (header[1].endsWith("pvsn")) {
			//Enable PeopleVision (tm)
			Main.disableProcessor();
			channel.write(MJPEGServer.HTTP_PAGE_200.duplicate());
			ByteBuffer tmp = ByteBuffer.allocate(4);
			tmp.putChar(Main.processorEnabled ? '1' : '0');
			channel.write(tmp);
			channel.close();
		} else if (header[1].endsWith("results.sse")) {
			System.out.println("Rectangle SSE stream");
			channel.write(MJPEGServer.HTTP_SSE_HEAD.duplicate());
			jsonSSEChannels.add(id);
		} else if (header[1].endsWith("qual/hi")) {
			//Set camera to high quality
			Main.setQuality(80);
			channel.write(MJPEGServer.HTTP_PAGE_200.duplicate());
			channel.close();
		} else if (header[1].endsWith("qual/lo")) {
			//Set camera to low quality
			Main.setQuality(50);
			channel.write(MJPEGServer.HTTP_PAGE_200.duplicate());
			channel.close();
		} else {
			System.out.println("Requested main");
			channel.write(MJPEGServer.HTTP_PAGE_MAIN.duplicate());
			channel.close();
			channelMap.remove(id);
		}
	}
	public void shutdown() throws IOException {
		this.shouldBeRunning.set(false);
		for (SocketChannel channel : channelMap.values())
			try {
				channel.close();
			} catch (IOException e) {}
		this.executor.shutdownNow();
		this.selector.close();
		this.serverSocket.close();
	}
	/**
	 * Parse byte array into HTTP header
	 * @param data
	 * @return parsed request
	 * @throws IOException 
	 */
	public static String[] parse(ByteBuffer data) throws IOException {
		ByteBufferTokenizer tokenizer = new ByteBufferTokenizer(new byte[]{'\r','\n'}, data);
		ByteBuffer token;
		
		//parse request line
		if ((token = tokenizer.next()) == null)
			throw new IOException("Token is null (while parsing request line)");
		String reqLine = new String(ByteUtils.toArray(token)).trim();
		String[] sections = reqLine.split(" ");//TODO optimize
		return sections;
	}
}
