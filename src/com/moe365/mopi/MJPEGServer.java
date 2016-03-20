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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.divisors.projectcuttlefish.httpserver.util.ByteUtils;
import com.divisors.projectcuttlefish.httpserver.util.ByteUtils.ByteBufferTokenizer;

import au.edu.jcu.v4l4j.VideoFrame;

public class MJPEGServer implements Runnable {
	/**
	 * Main HTTP page (HTML)
	 */
	public static final ByteBuffer HTTP_PAGE_MAIN;
	/**
	 * 404 page
	 */
	public static final ByteBuffer HTTP_PAGE_404;
	/**
	 * MJPEG header
	 */
	public static final ByteBuffer HTTP_HEAD_MJPEG;
	/**
	 * MJPEG frame header
	 */
	public static final ByteBuffer HTTP_FRAME_MJPEG;
	
	static {
		HTTP_PAGE_MAIN = loadHttp("main");
		HTTP_PAGE_404 = loadHttp("404");
		HTTP_HEAD_MJPEG = loadHttp("mjpeg-head");
		HTTP_FRAME_MJPEG = loadHttp("mjpeg-frame-head");
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
	protected ByteBuffer writeBuffer = ByteBuffer.allocateDirect(1024 * 100);
	protected AtomicBoolean isWriteBufferLocked = new AtomicBoolean(false);
	protected AtomicBoolean isImageAvailable = new AtomicBoolean(false);
	protected ConcurrentHashMap<Long, SocketChannel> channelMap = new ConcurrentHashMap<>();
	protected volatile Set<Long> mjpegChannels = ConcurrentHashMap.newKeySet();
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
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Server stopped.");
	}
	
	public void onNextFrame(VideoFrame frame) {
		if (isImageAvailable.get() || (!isWriteBufferLocked.compareAndSet(false, true))) {
			System.err.print('D');
			return;
		}
		try {
			writeBuffer.clear();
			writeBuffer.put(Integer.toString(frame.getFrameLength()).getBytes());
			writeBuffer.put(new byte[]{'\r','\n','\r','\n'});
			writeBuffer.put(frame.getBytes(), 0, frame.getFrameLength());
			writeBuffer.flip();
			isImageAvailable.compareAndSet(false, true);
			selector.wakeup();
		} finally {
			isWriteBufferLocked.compareAndSet(true, false);
		}
	}
	
	protected void attemptWriteNextFrame() {
		if (this.mjpegChannels.size() == 0 || !(this.isImageAvailable.get() && isWriteBufferLocked.compareAndSet(false, true)))
			return;
		System.out.print('W');
		System.out.print(this.mjpegChannels.size());
		try {
			for (Long id : this.mjpegChannels) {
				SocketChannel channel = this.channelMap.get(id);
				if (channel == null || !channel.isOpen()) {
					this.mjpegChannels.remove(id);
					continue;
				}
				try {
					if (channel.write(HTTP_FRAME_MJPEG.duplicate()) < 0 || channel.write(writeBuffer) < 0) {
						mjpegChannels.remove(id);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} finally {
			isImageAvailable.set(false);
			isWriteBufferLocked.set(false);
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
			System.out.println("JPG stream");
			channel.write(MJPEGServer.HTTP_HEAD_MJPEG.duplicate());
			mjpegChannels.add(id);
		} else if (header[1].endsWith("ico")) {
			System.out.println("Requested ICO");
			channel.write(MJPEGServer.HTTP_PAGE_404.duplicate());
			channel.close();
			channelMap.remove(id);
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
