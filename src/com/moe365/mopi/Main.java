package com.moe365.mopi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.moe365.mopi.CommandLineParser.ParsedCommandLineArguments;
import com.moe365.mopi.geom.PreciseRectangle;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import au.edu.jcu.v4l4j.CaptureCallback;
import au.edu.jcu.v4l4j.Control;
import au.edu.jcu.v4l4j.ControlList;
import au.edu.jcu.v4l4j.ImagePalette;
import au.edu.jcu.v4l4j.JPEGFrameGrabber;
import au.edu.jcu.v4l4j.V4L4JConstants;
import au.edu.jcu.v4l4j.VideoDevice;
import au.edu.jcu.v4l4j.VideoFrame;
import au.edu.jcu.v4l4j.encoder.JPEGEncoder;
import au.edu.jcu.v4l4j.exceptions.ControlException;
import au.edu.jcu.v4l4j.exceptions.StateException;
import au.edu.jcu.v4l4j.exceptions.UnsupportedMethod;
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
	public static final int DEFAULT_PORT = 5800;
	public static final String version = "0.1.6-alpha";
	public static int width;
	public static int height;
	public static volatile boolean processorEnabled = true;
	public static MJPEGServer httpServer;
	public static VideoDevice camera;
	public static RoboRioClient rioClient;
	public static JPEGFrameGrabber frameGrabber;
	public static ImageProcessor processor;
	public static void main(String...fred) throws IOException, V4L4JException, InterruptedException {
		CommandLineParser parser = loadParser();
		ParsedCommandLineArguments parsed = parser.apply(fred);
		
		if (parsed.isFlagSet("--help")) {
			System.out.println(parser.getHelpString());
			return;
		}
		
		if (parsed.isFlagSet("--rebuild-parser")) {
			System.out.print("Building parser...\t");
			buildParser();
			System.out.println("Done.");
			return;
		}
		
		width = parsed.getOrDefault("--width", 640);
		height = parsed.getOrDefault("--height", 480);
		System.out.println("Frame size: " + width + "x" + height);
		
		final MJPEGServer server = httpServer = initServer(parsed);
		
		final VideoDevice device = camera =  initCamera(parsed);
		
		final GpioPinDigitalOutput gpioPin = initGpio(parsed);
		
		final RoboRioClient client = rioClient = initClient(parsed);
		
		final ImageProcessor tracer = processor = initProcessor(parsed, client);
		
		final AtomicBoolean ledState = new AtomicBoolean(false);
		
		if (parsed.isFlagSet("--test")) {
			String target = parsed.get("--test");
			switch (target) {
				case "converter":
					testConverter(device);
					break;
				case "controls":
					testControls(device);
					break;
				case "client":
					testClient(client);
					break;
				default:
					System.err.println("Unknown test '" + target + "'");
			}
			device.release();
			System.exit(0);
		}
		
		final int jpegQuality = parsed.getOrDefault("--jpeg-quality", 80);
		System.out.println("JPEG quality: " + jpegQuality + "%");
		if (device != null) {
			final JPEGFrameGrabber fg = frameGrabber = device.getJPEGFrameGrabber(width, height, 0, V4L4JConstants.STANDARD_WEBCAM, jpegQuality);
			fg.setFrameInterval(parsed.getOrDefault("--fps-num", 1), parsed.getOrDefault("--fps-denom", 10));
			System.out.println("Framerate: " + fg.getFrameInterval());
			
			fg.setCaptureCallback(new CaptureCallback() {
				@Override
				public void nextFrame(VideoFrame frame) {
					try {
						if (server != null && ledState.get())
							server.offerFrame(frame);
						if (tracer != null && processorEnabled) {
							tracer.update(frame, ledState.get());
						} else {
							frame.recycle();
						}
//						System.out.println("Frame, " + ledState.get() + ", " + fg.getNumberOfRecycledVideoFrames());
						ledState.set(!ledState.get());
						gpioPin.setState(ledState.get() || (!processorEnabled));
					} catch (Exception e) {
						e.printStackTrace();
						throw e;
					}
				}
	
				@Override
				public void exceptionReceived(V4L4JException e) {
					e.printStackTrace();
					fg.stopCapture();
					if (server != null)
						try {
							server.shutdown();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
				}
			});
			fg.startCapture();
		}
	}
	protected static void testClient(RoboRioClient client) throws IOException, InterruptedException {
		System.out.println("RUNNING TEST: CLIENT");
		//just spews out UDP packets on a 3s loop
		while (true) {
			System.out.println("Writing r0");
			client.writeNoneFound();
			Thread.sleep(1000);
			System.out.println("Wrinting r1");
			client.writeOneFound(1.0, 2.0, 3.0, 4.0);
			Thread.sleep(1000);
			System.out.println("Writing r2");
			client.writeTwoFound(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.2);
			Thread.sleep(1000);
		}
	}
	protected static void testControls(VideoDevice device) throws ControlException, UnsupportedMethod, StateException {
		System.out.println("RUNNING TEST: CONTROLS");
		ControlList controls = device.getControlList();
		for (Control control : controls.getList()) {
			switch (control.getType()) {
				case V4L4JConstants.CTRL_TYPE_STRING:
					System.out.print("String control: " + control.getName() + " - min: " + control.getMinValue() + " - max: "
							+ control.getMaxValue() + " - step: " + control.getStepValue() + " - value: ");
					try {
						System.out.println(control.getStringValue());
					} catch (V4L4JException ve) {
						System.out.println(" ERROR");
						ve.printStackTrace();
					}
					break;
				case V4L4JConstants.CTRL_TYPE_LONG:
					System.out.print("Long control: " + control.getName() + " - value: ");
					try {
						System.out.println(control.getLongValue());
					} catch (V4L4JException ve) {
						System.out.println(" ERROR");
					}
					break;
				case V4L4JConstants.CTRL_TYPE_DISCRETE:
					Map<String, Integer> valueMap = control.getDiscreteValuesMap();
					System.out.print("Menu control: " + control.getName() + " - value: ");
					try {
						int value = control.getValue();
						System.out.print(value);
						try {
							System.out.println(" (" + control.getDiscreteValueName(control.getDiscreteValues().indexOf(value)) + ")");
						} catch (Exception e) {
							System.out.println(" (unknown)");
						}
					} catch (V4L4JException ve) {
						System.out.println(" ERROR");
					}
					System.out.println("\tMenu entries:");
					for (String s : valueMap.keySet())
						System.out.println("\t\t" + valueMap.get(s) + " - " + s);
					break;
				default:
					System.out.print("Control: " + control.getName() + " - min: " + control.getMinValue() + " - max: " + control.getMaxValue()
							+ " - step: " + control.getStepValue() + " - value: ");
					try {
						System.out.println(control.getValue());
					} catch (V4L4JException ve) {
						System.out.println(" ERROR");
					}
			}
		}
		device.releaseControlList();
	}
	/**
	 * Initialize the GPIO, getting the pin that the LED is attached to.
	 * @param args parsed command line arguments
	 * @return LED pin
	 */
	protected static GpioPinDigitalOutput initGpio(ParsedCommandLineArguments args) {
		if (args.isFlagSet("--no-gpio"))
			return null;
		final GpioController gpio = GpioFactory.getInstance();
		GpioPinDigitalOutput pin;
		if (args.isFlagSet("--gpio-pin")) {
			pin = gpio.provisionDigitalOutputPin(RaspiPin.getPinByName(args.get("--gpio-pin")), "LED pin", PinState.LOW);
		} else {
			pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "LED pin", PinState.LOW);
		}
		System.out.println("Using GPIO pin " + pin.getPin());
		pin.setMode(PinMode.DIGITAL_OUTPUT);
		pin.setState(false);
		return pin;
	}
	protected static RoboRioClient initClient(ParsedCommandLineArguments args) throws SocketException {
		if (args.isFlagSet("--no-udp"))
			return null;
		int port = args.getOrDefault("--rio-port", RoboRioClient.RIO_PORT);
		if (port < 0)
			return null;
		String address = args.getOrDefault("--rio-addr", RoboRioClient.RIO_ADDRESS);
		System.out.println("Address: " + address);
		InetSocketAddress addr = new InetSocketAddress(address, port);
		return new RoboRioClient(port, addr);
	}
	protected static ImageProcessor initProcessor(ParsedCommandLineArguments args, RoboRioClient client) {
		if (args.isFlagSet("--no-process")) {
			System.out.println("PROCESSOR DISABLED");
			return null;
		} else {
			processor = new ImageProcessor(width, height, client);
			if (args.isFlagSet("--save-diff"))
				processor.saveDiff = true;
			processor.start();
			enableProcessor();
			return processor;
			//new ContourTracer(width, height, parsed.getOrDefault("--x-skip", 10), parsed.getOrDefault("--y-skip", 20));
		}
	}
	/**
	 * Create and initialize the server
	 * @param args the command line arguments
	 * @return server, if created, or null
	 * @throws IOException
	 */
	protected static MJPEGServer initServer(ParsedCommandLineArguments args) throws IOException {
		int port = args.getOrDefault("--port", DEFAULT_PORT);
		
		if (port > 0 && !args.isFlagSet("--no-server")) {
			MJPEGServer server = new MJPEGServer(new InetSocketAddress(port));
			server.runOn(Executors.newCachedThreadPool());
			server.start();
			return server;
		} else {
			System.out.println("SERVER DISABLED");
		}
		return null;
	}
	protected static void testConverter(VideoDevice dev) {
		JPEGEncoder encoder = JPEGEncoder.to(width, height, ImagePalette.YUYV);
	}
	protected static VideoDevice initCamera(ParsedCommandLineArguments args) throws V4L4JException {
		String devName = args.getOrDefault("--camera", "/dev/video0");
		if (args.isFlagSet("--no-camera"))
			return null;
		System.out.print("Attempting to connect to camera @ " + devName + "...\t");
		VideoDevice device;
		try {
			device = new VideoDevice(devName);
		} catch (V4L4JException e) {
			System.out.println("ERROR");
			throw e;
		}
		System.out.println("SUCCESS");
		return device;
	}
	protected static CommandLineParser loadParser() {
		try (ObjectInput in = new ObjectInputStream(Main.class.getResourceAsStream("/resources/parser.ser"))) {
			return (CommandLineParser) in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			System.err.println("Error reading parser");
			e.printStackTrace();
		}
		return buildParser();
	}
	protected static CommandLineParser buildParser() {
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
			.addKvPair("--rio-port", "port", "Specify the port to use on the RoboRio")
			.addKvPair("--props", "file", "Specify the file to read properties from")
			.addKvPair("--write-props", "file", "Write properties to file, which can be passed into the --props arg in the future")
			.addFlag("--rebuild-parser", "Rebuilds the parser object")
			.addKvPair("--test", "target", "Run test by name")
			.addKvPair("--gpio-pin", "pin number", "Set which GPIO pin to use")
			.addKvPair("--x-skip", "px", "Number of pixels to skip on the x axis")
			.addKvPair("--y-skip", "px", "Number of pixels to skip on the y axis")
			.addKvPair("--width", "px", "Width of image")
			.addKvPair("--height", "px", "Height of image")
			.addKvPair("--fps-num", "numerator", "Set FPS numerator")
			.addKvPair("--fps-denom", "denom", "Set FPS denominator")
			.addFlag("--no-process", "Disable image processing")
			.addFlag("--no-camera", "Do not specify a camera")
			.addFlag("--no-udp", "Disable broadcasting UDP")
			.addFlag("--no-gpio", "Do not specify a gpio pin")
			.addFlag("--save-diff", "Save the diff image to a file. Requires processor.")
			.addKvPair("--jpeg-quality", "quality", "Set the JPEG quality to request")
			.build();
		File outputFile = new File("src/resources/parser.ser");
		if (outputFile.exists())
			outputFile.delete();
		try (ObjectOutput out = new ObjectOutputStream(new FileOutputStream(outputFile))) {
			out.writeObject(parser);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return parser;
	}
}
