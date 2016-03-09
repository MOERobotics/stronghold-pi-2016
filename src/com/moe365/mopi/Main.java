package com.moe365.mopi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.moe365.mopi.CommandLineParser.ParsedCommandLineArguments;

import au.edu.jcu.v4l4j.CaptureCallback;
import au.edu.jcu.v4l4j.JPEGFrameGrabber;
import au.edu.jcu.v4l4j.V4L4JConstants;
import au.edu.jcu.v4l4j.VideoDevice;
import au.edu.jcu.v4l4j.VideoFrame;
import au.edu.jcu.v4l4j.exceptions.V4L4JException;

/**
 * Main entry point for Moe Pi.
 * <p>
 * Command line options include:
 * <dl>
 * <dt>-?</dt>
 * <dt>-h</dt>
 * <dt>--help</dt>
 * <dd>Displays help and quits</dd>
 * <dt>-C [device]</dt>
 * <dt>--camera [device]</dt>
 * <dd>Specify which camera to use</dd>
 * <dt>-v</dt>
 * <dt>--verbose</dt>
 * <dd>Enable verbose printing</dd>
 * <dt>--out</dt>
 * <dd>Specify where to print output to. STDOUT prints to standard out, and NULL disables logging
 * (similar to printing to <code>/dev/null</code>)</dd>
 * </dl>
 * </p>
 * 
 * @author mailmindlin
 */
public class Main {
	public static final String version = "0.0.0-alpha";
	public static void main(String...fred) throws IOException, V4L4JException {
		CommandLineParser parser = loadParser();
		ParsedCommandLineArguments parsed = parser.apply(fred);
		if (parsed.isFlagSet("--help")) {
			System.out.println(parser.getHelpString());
			return;
		}
		
		final MJPEGServer server = initServer(parsed);
		
		final VideoDevice device = initCamera(parsed);
		
		JPEGFrameGrabber fg = device.getJPEGFrameGrabber(640, 480, 0, V4L4JConstants.STANDARD_WEBCAM, 80);
		fg.setFrameInterval(1, 10);
		fg.setCaptureCallback(new CaptureCallback() {
			@Override
			public void nextFrame(VideoFrame frame) {
				if (server != null)
					server.onNextFrame(frame);
				frame.recycle();
			}

			@Override
			public void exceptionReceived(V4L4JException e) {
				e.printStackTrace();
				fg.stopCapture();
			}
		});
		fg.startCapture();
	}
	protected static MJPEGServer initServer(ParsedCommandLineArguments args) throws IOException {
		int port = 5800;
		if (args.isFlagSet("--port")) {
			try {
				port = Integer.parseInt(args.get("--port"));
			} catch (NumberFormatException | NullPointerException e) {}
		}
		if (port < 0 || !args.isFlagSet("--no-server")) {
			MJPEGServer server = new MJPEGServer(new InetSocketAddress(port));
			server.runOn(Executors.newCachedThreadPool());
			server.start();
			return server;
		}
		return null;
	}
	protected static VideoDevice initCamera(ParsedCommandLineArguments args) throws V4L4JException {
		String devName = "/dev/video0";
		if (args.isFlagSet("--camera"))
			devName = args.get("--camera");
		
		VideoDevice device = new VideoDevice(devName);
		System.out.println("Connected to camera @ " + device.getDevicefile());
		return device;
	}
	protected static CommandLineParser loadParser() {
		try (ObjectInput in = new ObjectInputStream(Main.class.getResourceAsStream("/resources/parser.ser"))) {
			return (CommandLineParser) in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	protected static void buildParser() {
		CommandLineParser parser = CommandLineParser.builder()
			.addFlag("--help", "Displays the help message and exits")
			.alias("-h", "--help")
			.alias("-?", "--help")
			.addKvPair("--camera", "device", "Specify the camera to use")
			.alias("-C", "--camera")
			.addFlag("--verbose", "Enable verbose output")
			.alias("-v", "--verbose")
			.addFlag("--out", "Specify where to write log messages to")
			.addKvPair("--port", "port", "Specify the port to listen on. Default 0, or autodetect; a negative port is equivalent to --no-server")
			.alias("-p", "--port")
			.addFlag("--no-server", "Disables server")
			.addFlag("--version", "Print version string")
			.addKvPair("--rio-addr", "address", "Specify where to find the RoboRio")
			.addKvPair("--props", "file", "Specify the file to read properties from")
			.addKvPair("--write-props", "file", "Write properties to file, which can be passed into the --props arg in the future")
			.build();
		File outputFile = new File("parser.ser");
		try (ObjectOutput out = new ObjectOutputStream(new FileOutputStream(outputFile))) {
			out.writeObject(parser);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public static void printHelp() {
		System.out.println("Moe Pi version " + version);
		System.out.println();
		System.out.println("Usage: java -jar moepi.jar <options>");
		System.out.println("Options:");
		System.out.println("  -?");
		System.out.println("  -h");
		System.out.println("  --help");
		System.out.println("    Displays this message and exits");
		System.out.println("  -C [device]");
		System.out.println("  --camera [device]");
		System.out.println("    Specify the camera to use");
		System.out.println("  -v");
		System.out.println("  --verbose");
		System.out.println("    Enables verbose output");
		System.out.println("  --out [file]");
		System.out.println("    Specify file to write output to");
		System.out.println("  -p [port]");
		System.out.println("  --port [port]");
		System.out.println("    Specify port to listen on.");
		System.out.println("    Specifying port 0 will automatically select one.");
		System.out.println("    Specifying a port <0 is equivalent to --no-server");
		System.out.println("  --no-server");
		System.out.println("  ");
		
	}
}
