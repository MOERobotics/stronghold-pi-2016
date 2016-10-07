package com.moe365.mopi;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import com.moe365.mopi.geom.PreciseRectangle;

/**
 * UDP server to broadcast data at the RIO. <strong>Not</strong> thread safe.
 * <p>
 * 
 * <section id="header">
 * <h2>Packet header format</h2>
 * <pre>
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          Sequence Number                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           Status code         |              Flag             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 * <dl>
 * <dt>Sequence Number</dt>
 * <dd>32-bit packet number, always increasing. If packet A is received with a
 * sequence number of 5, then all future packets with sequence numbers under 5
 * may be discarded. Because this field is not assured to be consecutive, unix
 * (or other) timestamps may be used, assuming no consecutive packets are sent
 * within the same lowest resolution time unit. 
 * </dd>
 * <dt>Status code</dt>
 * <dd>16-bit code indicating the intent of the packet. See <a href="#statusCodes">Status Codes</a>.</dd>
 * <dt>Flag</dt>
 * <dd>8-bit secondary status code, it is used for stuff like QOS. If unused,
 * set to 0. See <a href="#flags">Flags</a>.
 * </dd>
 * </dl>
 * </section>
 * <section id="statusCodes">
 * <h3>Status Codes</h3>
 * A status code may be one of the following:
 * <ol start="0">
 * <li>{@linkplain #STATUS_NOP NOP}</li>
 * <li>{@linkplain #STATUS_NONE_FOUND NONE_FOUND}</li>
 * <li>{@linkplain #STATUS_ONE_FOUND ONE_FOUND}</li>
 * <li>{@linkplain #STATUS_TWO_FOUND TWO_FOUND}</li>
 * <li>{@linkplain #STATUS_ERROR ERROR}</li>
 * <li>{@linkplain #STATUS_GOODBYE GOODBYE}</li>
 * </ol>
 * All other status codes are reserved for future use.
 * </section>
 * <section id="flags">
 * <h3>Flags</h3>
 * <table style="border:1px solid black;border-collapse:collapse;">
 * <thead>
 * <tr>
 * <th style="text-align:left">#</th>
 * <th style="text-align:left">Name</th>
 * <th style="text-align:left">Description</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <td>0</td>
 * <td>PING</td>
 * <td>Sends a ping request. For latency measurement</td>
 * </tr>
 * <tr>
 * <td>1</td>
 * <td>PONG</td>
 * <td>A response to a ping request</td>
 * </tr>
 * <tr>
 * <td>2</td>
 * <td>ARE_YOU_STILL_THERE</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>3</td>
 * <td>YES_I_AM</td>
 * <td></td>
 * </tr>
 * </tbody>
 * </table>
 * </section>
 * @author mailmindlin
 */
public class RoboRioClient implements Closeable {
	public static final int RIO_PORT = 5801;
	/**
	 * Size of the buffer.
	 */
	public static final int BUFFER_SIZE = 72;
	public static final int RESOLVE_RETRY_TIME = 5_000;
	/**
	 * mDNS address of the RoboRio.
	 * 
	 * TODO make more portable for other teams
	 */
	public static final String RIO_ADDRESS = "roboRIO-365-FRC.local";
	
	/**
	 * Denotes a packet that should be ignored. No idea why we would need to use
	 * this, though.
	 */
	public static final short STATUS_NOP = 0;
	/**
	 * Denotes a packet telling the Rio that no target(s) were found.
	 */
	public static final short STATUS_NONE_FOUND = 1;
	/**
	 * Denotes a packet telling the Rio that one target has been detected. The
	 * position data MUST be included in the packet.
	 */
	public static final short STATUS_ONE_FOUND = 2;
	/**
	 * Denotes a packet telling the Rio that two or more targets have been
	 * found. The position data of the two largest targets found (by area) MUST
	 * be included in the packet.
	 */
	public static final short STATUS_TWO_FOUND = 3;
	public static final short STATUS_ERROR = 4;
	public static final short STATUS_HELLO_WORLD = 5;
	public static final short STATUS_GOODBYE = 6;
	public static final short STATUS_PING = 7;
	public static final short STATUS_PONG = 8;
	public static final short STATUS_ARE_YOU_THERE = 9;
	public static final short STATUS_YES_I_AM = 10;
	public static final short STATUS_REQUEST_CONFIG = 11;
	public static final short STATUS_CONFIG = 12;
	
	/**
	 * 8 byte packet. Used for sending {@link #STATUS_NONE_FOUND} and
	 * {@link #STATUS_NOP} packets.
	 */
	protected DatagramPacket packet_8;
	/**
	 * 40 byte packet. Used for sending {@link #STATUS_ONE_FOUND} and
	 * {@link #STATUS_ERROR} packets.
	 */
	protected DatagramPacket packet_40;
	/**
	 * 72 byte packet. Used for sending {@link #STATUS_TWO_FOUND} packets.
	 */
	protected DatagramPacket packet_72;
	/**
	 * RoboRIO's address
	 */
	protected SocketAddress address;
	/**
	 * UDP socket
	 */
	protected DatagramSocket socket;
	/**
	 * Executor for background tasks to run, currently only consisting of
	 * resolving mDNS addresses in the background.
	 */
	protected final ExecutorService executor;
	/**
	 * The port that we are sending packets from (on the local machine)
	 */
	protected final int port;
	/**
	 * Buffer backing packets
	 */
	protected final ByteBuffer buffer;
	/**
	 * Packet number. This number is to allow the client to ignore packets that
	 * are received out of order. Always increasing.
	 */
	protected AtomicInteger packetNum = new AtomicInteger(0);
	protected volatile boolean isResolved = false;
	
	/**
	 * Create a client with default settings
	 * 
	 * @param executor Executor to run background tasks on
	 * @throws SocketException 
	 * @throws IOException
	 */
	public RoboRioClient(ExecutorService executor) throws SocketException, IOException {
		this(executor, RIO_PORT, new InetSocketAddress(RIO_ADDRESS, RIO_PORT));
	}
	
	/**
	 * 
	 * @param executor
	 * @param port
	 * @param addr
	 * @throws SocketException
	 * @throws IOException
	 */
	public RoboRioClient(ExecutorService executor, int port, SocketAddress addr) throws SocketException, IOException {
		this(executor, RESOLVE_RETRY_TIME, port, addr);
	}
	
	/**
	 * 
	 * @param executor
	 * @param port
	 * @param addr
	 * @throws SocketException
	 * @throws IOException
	 */
	public RoboRioClient(ExecutorService executor, long resolveRetryTime, int port, SocketAddress addr)
			throws SocketException, IOException {
		this(executor, resolveRetryTime, port, BUFFER_SIZE, addr);
	}
	
	/**
	 * 
	 * @param executor
	 * @param port
	 * @param buffSize
	 * @param addr
	 * @throws SocketException
	 *             if the socket could not be opened, or the socket could not
	 *             bind to the specified local port.
	 * @throws SecurityException
	 *             If this program is not allowed to connect to the given socket
	 */
	public RoboRioClient(ExecutorService executor, long resolveRetryTime, int port, int buffSize, SocketAddress addr)
			throws SocketException, SecurityException {
		this.executor = executor;
		this.port = port;
		this.address = addr;
		this.buffer = ByteBuffer.allocate(buffSize);
		System.out.println("Connecting to RIO: " + port + " | " + addr);
		this.socket = new DatagramSocket(port);
		try {
			socket.setTrafficClass(0x10);// Low delay
		} catch (SocketException e) {
			e.printStackTrace();
		}
		System.out.println("ATTEMPT RESOLVE " + address);
		executor.submit(() -> {
			while (!(this.isResolved || Thread.interrupted())) {
				try {
					this.packet_8 = new DatagramPacket(buffer.array(), 0, 8, address);
					this.packet_40 = new DatagramPacket(buffer.array(), 0, 40, address);
					this.packet_72 = new DatagramPacket(buffer.array(), 0, 72, address);
					this.isResolved = true;
					System.out.println("RESOLVED " + address);
					return;
				} catch (IllegalArgumentException e) {
					System.err.println("UNABLE TO RESOLVE " + address + " (retry in " + resolveRetryTime + "ms)");
				}
				try {
					Thread.sleep(resolveRetryTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
				Thread.yield();
			}
		});
	}
	
	public RoboRioClient(ExecutorService executor, long resolveRetryTime, int port, String addr) throws SocketException, IOException {
		this.executor = executor;
		this.port = port;
		this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
		System.out.println("Connecting to RIO: " + port + " | " + addr);
		this.socket = new DatagramSocket(port);
		try {
			socket.setTrafficClass(0x10);// Low delay
		} catch (SocketException e) {
			e.printStackTrace();
		}
		System.out.println("ATTEMPT RESOLVE " + address);
		executor.submit(() -> {
			String host = addr;
			int addrPort = 5801;
			if (host.contains(":")) {
				int idx = host.lastIndexOf(':');
				addrPort = Integer.valueOf(host.substring(idx + 1));
				host = host.substring(0, idx);
			}
			while (!(this.isResolved || Thread.interrupted())) {
				try {
					InetAddress address = InetAddress.getByName(addr);
					InetSocketAddress sAddr = new InetSocketAddress(address, addrPort);
					this.packet_8 = new DatagramPacket(buffer.array(), 0, 8, sAddr);
					this.packet_40 = new DatagramPacket(buffer.array(), 0, 40, sAddr);
					this.packet_72 = new DatagramPacket(buffer.array(), 0, 72, sAddr);
					this.address = sAddr;
					this.isResolved = true;
					System.out.println("RESOLVED " + sAddr);
					return;
				} catch (IllegalArgumentException | UnknownHostException e) {
					System.err.println("UNABLE TO RESOLVE " + addr + " (retry in " + resolveRetryTime + "ms)");
				}
				try {
					Thread.sleep(resolveRetryTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
				Thread.yield();
			}
		});
	}
	
	/**
	 * Build a packet
	 * @param status
	 * @param flag
	 */
	protected void build(short status, short flag) {
		buffer.position(0);
		buffer.putInt(packetNum.getAndIncrement());
		buffer.putShort(status);
		buffer.putShort(flag);
	}
	
	protected void send(DatagramPacket packet) throws IOException {
		socket.send(packet);
	}
	
	public void write(short status) throws IOException {
		build(status, (short) 0);
		send(packet_8);
	}
	
	public void write(short status, short ack) throws IOException {
		build(status, ack);
		send(packet_8);
	}
	
	public void writeNoneFound() throws IOException {
		write(STATUS_NONE_FOUND);
	}
	
	public void writeOneFound(PreciseRectangle rect) throws IOException {
		writeOneFound(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
	}
	
	public void writeOneFound(double left, double top, double width, double height) throws IOException {
		if (!isResolved)
			return;
		build(STATUS_ONE_FOUND, (short) 0);
		buffer.putDouble(left);
		buffer.putDouble(top);
		buffer.putDouble(width);
		buffer.putDouble(height);
		send(packet_40);
	}
	
	public void writeTwoFound(PreciseRectangle rect1, PreciseRectangle rect2) throws IOException {
		writeTwoFound(rect1.getX(), rect1.getY(), rect1.getWidth(), rect1.getHeight(), rect2.getX(), rect2.getY(), rect2.getWidth(), rect2.getHeight());
	}
	
	public void writeTwoFound(double left1, double top1, double width1, double height1, double left2, double top2, double width2, double height2) throws IOException {
		if (!isResolved)
			return;
		build(STATUS_TWO_FOUND, (short) 0);
		buffer.putDouble(left1);
		buffer.putDouble(top1);
		buffer.putDouble(width1);
		buffer.putDouble(height1);
		buffer.putDouble(left2);
		buffer.putDouble(top2);
		buffer.putDouble(width2);
		buffer.putDouble(height2);
		send(packet_72);
	}
	
	public void writeError(long errorCode) throws IOException {
		if (!isResolved)
			return;
		build(STATUS_ERROR, (short) 0);
		buffer.putLong(errorCode);
		// Pad with 0s
		buffer.putLong(0);
		buffer.putLong(0);
		buffer.putLong(0);
		send(packet_40);
	}
	
	@Override
	public void close() throws IOException {
		socket.close();
	}
}
