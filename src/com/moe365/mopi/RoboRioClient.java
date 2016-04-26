package com.moe365.mopi;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import com.moe365.mopi.geom.PreciseRectangle;

/**
 * UDP server to broadcast data at the RIO.
 * <p>
 * All packets sent from this class start with a 32 bit unsigned integer
 * sequence number, which will always increase between consecutive packets.
 * Format of the UDP packets:
 * 
 * <pre>
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          Sequence Number                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           Status code         |               ACK             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 * <dl>
 * <dt>Sequence Number: 32 bits</dt>
 * <dd>The packet number, always increasing. If packet A is received with a
 * sequence number of 5, then all future packets with sequence numbers under 5
 * may be discarded. This may be a timestamp</dd>
 * <dt>Status code: 16 bits</dt>
 * <dd>One of the following:
 * <ol start=0> <li>NOP</li> <li>NONE_FOUND</li> <li>ONE_FOUMD</li>
 * <li>TWO_FOUND</li> <li>GOODBYE</li> </ol> All other status codes are reserved
 * for future use. </dd> <dt>Flag: 8 bits</dt> <dd>Like a secondary status code,
 * it is used for stuff like QOS. If unused, set to 0. <table> <thead> <tr>
 * <th>#</th> <th>Name</th> <th>Description</th> </tr> </thead> <tbody> <tr>
 * <td>0</td> <td>Ping</td> <td>Sends a ping request. For latency
 * measurement</td> </tr> <tr> <td>1</td> <li>PING</li> <li>PONG</li>
 * <li>ARE_YOU_STILL_THERE</li> <li>YES_I_AM</li> </tbpdy> </table> </dd> </dl>
 * </p>
 * 
 * @author mailmindlin
 */
public class RoboRioClient implements Closeable {
	public static final int RIO_PORT = 5801;
	/**
	 * Size of the buffer.
	 */
	public static final int BUFFER_SIZE = 72;
	/**
	 * mDNS address of the RoboRio.
	 * 
	 * TODO make more portable for other teams
	 */
	public static final String RIO_ADDRESS = "roboRIO-365-FRC.local";
	
	/**
	 * Denotes a packet that should be ignored. No idea why we would need
	 * to use this, though.
	 */
	public static final short STATUS_NOP = 0;
	/**
	 * Denotes a packet telling the Rio that no target(s) were found.
	 */
	public static final short STATUS_NONE_FOUND = 1;
	/**
	 * Denotes a packet telling the Rio that one target has been
	 * detected. The position data MUST be included in the packet.
	 */
	public static final short STATUS_ONE_FOUND = 2;
	/**
	 * Denotes a packet telling the Rio that two or more targets
	 * have been found. The position data of the two largest targets
	 * found (by area) MUST be included in the packet. 
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
	 * 8 byte packet. Used for sending {@link #STATUS_NONE_FOUND} packets.
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
	protected final SocketAddress address;
	/**
	 * UDP socket
	 */
	protected DatagramSocket socket;
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
	 * are recieved out of order. Always increasing.
	 */
	protected AtomicInteger packetNum = new AtomicInteger(0);
	/**
	 * Create a client with default settings
	 * @throws SocketException 
	 * @throws IOException 
	 */
	public RoboRioClient() throws SocketException {
		this(RIO_PORT, BUFFER_SIZE, new InetSocketAddress(RIO_ADDRESS, RIO_PORT));
	}
	public RoboRioClient(int port, SocketAddress addr) throws SocketException {
		this(port, BUFFER_SIZE, addr);
	}
	public RoboRioClient(int port, int buffSize, SocketAddress addr) throws SocketException {
		this.port = port;
		this.address = addr;
		this.buffer = ByteBuffer.allocate(buffSize);
		System.out.println("Connecting to RIO: " + port + " | " + addr);
		this.socket = new DatagramSocket(port);
		socket.setTrafficClass(0x10);//Low delay
		this.packet_8 = new DatagramPacket(buffer.array(), 0, 8, address);
		this.packet_40 = new DatagramPacket(buffer.array(), 0, 40, address);
		this.packet_72 = new DatagramPacket(buffer.array(), 0, 72, address);
	}
	public void build(short status, short ack) {
		buffer.position(0);
		buffer.putInt(packetNum.getAndIncrement());
		buffer.putShort(status);
		buffer.putShort(ack);
	}
	public void write(short status) throws IOException {
		build(status, (short) 0);
		socket.send(packet_8);
	}
	public void write(short status, short ack) throws IOException {
		build(status, ack);
		socket.send(packet_8);
	}
	public void writeNoneFound() throws IOException {
		write(STATUS_NONE_FOUND);
	}
	public void writeOneFound(PreciseRectangle rect) throws IOException {
		writeOneFound(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
	}
	public void writeOneFound(double left, double top, double width, double height) throws IOException {
		build(STATUS_ONE_FOUND, (short) 0);
		buffer.putDouble(left);
		buffer.putDouble(top);
		buffer.putDouble(width);
		buffer.putDouble(height);
		socket.send(packet_40);
	}
	public void writeTwoFound(PreciseRectangle rect1, PreciseRectangle rect2) throws IOException {
		writeTwoFound(rect1.getX(), rect1.getY(), rect1.getWidth(), rect1.getHeight(), rect2.getX(), rect2.getY(), rect2.getWidth(), rect2.getHeight());
	}
	public void writeTwoFound(double left1, double top1, double width1, double height1, double left2, double top2, double width2, double height2) throws IOException {
		build(STATUS_TWO_FOUND, (short) 0);
		buffer.putDouble(left1);
		buffer.putDouble(top1);
		buffer.putDouble(width1);
		buffer.putDouble(height1);
		buffer.putDouble(left2);
		buffer.putDouble(top2);
		buffer.putDouble(width2);
		buffer.putDouble(height2);
		socket.send(packet_72);
	}
	public void writeError(long errorCode) throws IOException {
		build(STATUS_ERROR, (short)0);
		buffer.putLong(errorCode);
		socket.send(packet_8);
	}
	@Override
	public void close() throws IOException {
		socket.close();
	}
}
