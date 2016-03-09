package com.divisors.projectcuttlefish.httpserver.util;

import java.nio.ByteBuffer;

/**
 * 
 * @author mailmindlin
 *
 */
public class ByteUtils {
	/**
	 * Lets you do stuff like {@link String#split(String)}, but with ByteBuffers
	 * 
	 * TODO just a random idea, but this *might* be a lot faster if implemented natively.
	 *  Just an idea.
	 * @author mailmindlin
	 */
	public static final class ByteBufferTokenizer {
		/**
		 * Default capacity for buffer
		 */
		public static final int DEFAULT_CAPACITY = 8196;
		/**
		 * Token to search for
		 */
		protected final byte[] token;
		/**
		 * Buffer to search in
		 */
		protected final ByteBuffer buffer;
		/**
		 * Offset from the start of the buffer to continue searching
		 */
		protected int offset = 0;
		public ByteBufferTokenizer(byte[] token) {
			this(token, ByteBuffer.allocate(DEFAULT_CAPACITY));
		}
		public ByteBufferTokenizer(byte[] token, int capacity) {
			this(token, ByteBuffer.allocate(capacity));
		}
		/**
		 * Create with token and buffer
		 * @param token token to search for
		 * @param buffer haystack
		 */
		public ByteBufferTokenizer(byte[] token, ByteBuffer buffer) {
			this.token = token;
			this.buffer = buffer;
		}
		/**
		 * How many bytes are available in the buffer to search
		 * @return that
		 */
		public int available() {
			return buffer.remaining();
		}
		public ByteBuffer getBuffer() {
			return this.buffer;
		}
		/**
		 * Put given bytes in buffer
		 * @param bytes data to put in
		 * @return success
		 */
		public boolean put(byte...bytes) {
			try {
				buffer.put(bytes);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		/**
		 * Put given bytes in buffer.
		 * @param bytes data to put in (unsigned 8 bit values)
		 * @return success
		 */
		public boolean put(int...bytes) {
			byte[] arr = new byte[bytes.length];
			for (int i=0;i<bytes.length;i++)
				arr[i]=(byte)bytes[i];
			return this.put(arr);
		}
		public boolean put(ByteBuffer buf) {
			try {
				buffer.put(buf);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}
		public ByteBufferTokenizer clear() {
			synchronized (buffer) {
				this.buffer.clear();
			}
			return this;
		}
		/**
		 * ByteBuffer containing segment from the current position to next token parsed. The ByteBuffer returned
		 * will contain the bytes matching the token at the end.
		 * 
		 * @return a mirror ByteBuffer containing a section of this. Any changes to this buffer's content will be reflected in the original
		 *         buffer. If no match is found, then this method returns null.
		 */
		public ByteBuffer next() {
			synchronized (this.buffer) {
				ByteBuffer mirror = buffer.duplicate();
				mirror.position(offset);
				//small buffer for reading tokens
				final byte[] minibuf;//(make it only once to decrease the total allocation of this fn)
				if (token.length > 1)
					minibuf = new byte[token.length - 1];
				else
					minibuf = null;
				search:
				while (mirror.remaining() >= token.length) {
					if (mirror.get() == token[0]) {
						if (token.length > 1) {
							mirror.mark();
							mirror.get(minibuf);
							for (int i=0; i < minibuf.length; i++)
								if (this.token[i+1] != minibuf[i]) {
									mirror.reset();
									continue search;
								}
						}
						int size = mirror.position() - offset;
						mirror.flip().position(offset);
						offset += size;
						return mirror;
					}
				}
			}
			return null;
		}
		/**
		 * @return a buffer containing whatever bytes have not yet been tokenized.
		 */
		public ByteBuffer remaining() {
			ByteBuffer mirror = buffer.duplicate().asReadOnlyBuffer();
			mirror.position(offset).limit(buffer.position());
			return mirror;
		}
	}
	/**
	 * 
	 * @param buffer
	 * @return
	 */
	public static byte[] toArray(ByteBuffer buffer) {
		byte[] result = new byte[buffer.remaining()];
		buffer.get(result);
		return result;
	}
}
