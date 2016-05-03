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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.moe365.mopi.CommandLineParser.ParsedCommandLineArguments;
import com.moe365.mopi.geom.Polygon;
import com.moe365.mopi.geom.Polygon.PointNode;
import com.moe365.mopi.geom.PreciseRectangle;
import com.moe365.mopi.processing.AbstractImageProcessor;
import com.moe365.mopi.processing.ContourTracer;
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
	/**
	 * Version string. Should be semantically versioned.
	 * @see <a href="semver.org">semver.org</a>
	 */
	public static final String version = "0.3.4-alpha";
	public static int width;
	public static int height;
	public static volatile boolean processorEnabled = true;
	public static VideoDevice camera;
	public static RoboRioClient rioClient;
	public static JPEGFrameGrabber frameGrabber;
	public static AbstractImageProcessor<?> processor;
	/**
	 * Main entry point.
	 * @param fred Command line arguments
	 * @throws IOException
	 * @throws V4L4JException
	 * @throws InterruptedException
	 */
	public static void main(String...fred) throws IOException, V4L4JException, InterruptedException {
		CommandLineParser parser = loadParser();
		ParsedCommandLineArguments parsed = parser.apply(fred);
		
		if (parsed.isFlagSet("--help")) {
			System.out.println(parser.getHelpString());
			System.exit(0);
		}
		
		if (parsed.isFlagSet("--rebuild-parser")) {
			System.out.print("Building parser...\t");
			buildParser();
			System.out.println("Done.");
			System.exit(0);
		}
		
		width = parsed.getOrDefault("--width", 640);
		height = parsed.getOrDefault("--height", 480);
		System.out.println("Frame size: " + width + "x" + height);
		
		final ExecutorService executor = Executors.newCachedThreadPool();
		
		final MJPEGServer server = initServer(parsed, executor);
		
		final VideoDevice device = camera = initCamera(parsed);
		
		final GpioPinDigitalOutput gpioPin = initGpio(parsed);
		
		final RoboRioClient client = initClient(parsed, executor);
		
		final AbstractImageProcessor<?> tracer = processor = initProcessor(parsed, server, client);
		
		//The state of the LED. Used for timing.
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
				case "sse":
					testSSE(server);
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
							tracer.offerFrame(frame, ledState.get());
						} else {
							frame.recycle();
						}
//						System.out.println("Frame, " + ledState.get() + ", " + fg.getNumberOfRecycledVideoFrames());
						ledState.set(!ledState.get());
						gpioPin.setState(ledState.get() || (!processorEnabled));
					} catch (Exception e) {
						//Make sure to print any/all exceptions
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
	protected static void testSSE(MJPEGServer server) throws InterruptedException {
		System.out.println("RUNNING TEST: SSE");
		while (true) {
			List<PreciseRectangle> rects = new LinkedList<>();
			server.offerRectangles(rects);
			Thread.sleep(1000);
			rects.add(new PreciseRectangle(0.0,0.0,0.2,0.2));
			server.offerRectangles(rects);
			Thread.sleep(1000);
			rects.add(new PreciseRectangle(0.25,0.75,0.3,0.1));
			server.offerRectangles(rects);
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
			//GPIO is disabled, so return null
			return null;
		//Get the GPIO object
		final GpioController gpio = GpioFactory.getInstance();
		GpioPinDigitalOutput pin;
		if (args.isFlagSet("--gpio-pin")) {
			//Get the GPIO pin by name
			String pinName = args.get("--gpio-pin");
			if (!pinName.startsWith("GPIO ") && pinName.matches("\\d+"))
				pinName = "GPIO " + pinName.trim();
			pin = gpio.provisionDigitalOutputPin(RaspiPin.getPinByName(pinName), "LED pin", PinState.LOW);
		} else {
			//Get the GPIO_01 pin 
			pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "LED pin", PinState.LOW);
		}
		System.out.println("Using GPIO pin " + pin.getPin());
		pin.setMode(PinMode.DIGITAL_OUTPUT);
		pin.setState(false);//turn it off
		return pin;
	}
	protected static RoboRioClient initClient(ParsedCommandLineArguments args, ExecutorService executor) throws SocketException {
		if (args.isFlagSet("--no-udp")) {
			System.out.println("CLIENT DISABLED");
			return null;
		}
		int port = args.getOrDefault("--udp-port", RoboRioClient.RIO_PORT);
		int retryTime = args.getOrDefault("--mdns-resolve-retry", 5_000);
		if (port < 0) {
			System.out.println("CLIENT DISABLED");
			return null;
		}
		String address = args.getOrDefault("--udp-addr", RoboRioClient.RIO_ADDRESS);
		System.out.println("Address: " + address);
		try {
			return new RoboRioClient(executor, retryTime, port, address);
		} catch (IOException e) {
			//restrict scope of broken stuff
			e.printStackTrace();
			System.err.println("CLIENT DISABLED");
			return null;
		}
	}
	protected static AbstractImageProcessor<?> initProcessor(ParsedCommandLineArguments args, final MJPEGServer httpServer, final RoboRioClient client) {
		if (args.isFlagSet("--no-process"))
			System.out.println("PROCESSOR DISABLED");
			if (args.isFlagSet("--trace-contours")) {
				ContourTracer processor = new ContourTracer(width, height, polygons -> {
					for (Polygon polygon : polygons) {
						System.out.println("=> " + polygon);
						PointNode node = polygon.getStartingPoint();
						// Scale
						do {
							node = node.set(node.getX() / width, node.getY() / height);
						} while (!(node = node.next()).equals(polygon.getStartingPoint()));
					}
					if (httpServer != null)
						httpServer.offerPolygons(polygons);
				});
				Main.processor = processor;
			} else {
				ImageProcessor processor = new ImageProcessor(width, height, rectangles-> {
					//Filter based on AR
					rectangles.removeIf(rectangle-> {
						double ar = rectangle.getHeight() / rectangle.getWidth();
						return ar < .1 || ar > 10;
					});
					//print the rectangles' dimensions to STDOUT
					for (PreciseRectangle rectangle : rectangles)
						System.out.println("=> " + rectangle);
					
					//send the largest rectangle(s) to the Rio
					try {
						if (client != null) {
							if (rectangles.isEmpty()) {
								client.writeNoneFound();
							} else if (rectangles.size() == 1) {
								client.writeOneFound(rectangles.get(0));
							} else {
								client.writeTwoFound(rectangles.get(0), rectangles.get(1));
							}
						}
					} catch (IOException | NullPointerException e) {
						e.printStackTrace();
					}
					//Offer the rectangles to be put in the SSE stream
					if (httpServer != null)
						httpServer.offerRectangles(rectangles);
				});
				if (args.isFlagSet("--save-diff"))
					processor.saveDiff = true;
				Main.processor = processor;
			}
			processor.start();
			enableProcessor();
			return processor;
			//new ContourTracer(width, height, parsed.getOrDefault("--x-skip", 10), parsed.getOrDefault("--y-skip", 20));
	}
	public static void enableProcessor() {
		System.out.println("ENABLING CV");
		if (processor == null) {
			if (processorEnabled)
				disableProcessor(null);
			return;
		}
		if (camera == null) {
			processorEnabled = false;
			return;
		}
		try {
			ControlList controls = camera.getControlList();
			controls.getControl("Exposure, Auto").setValue(1);
			controls.getControl("Exposure (Absolute)").setValue(19);
			controls.getControl("Contrast").setValue(10);
//			Control whiteBalanceControl = controls.getControl(don't remember what goes here);
//			whiteBalanceControl.setValue(whiteBalanceControl.getMaxValue());
			Control saturationControl = controls.getControl("Saturation");
			saturationControl.setValue(saturationControl.getMaxValue());
			Control sharpnessControl = controls.getControl("Sharpness");
			sharpnessControl.setValue(50);
			Control brightnessControl = controls.getControl("Brightness");
			brightnessControl.setValue(42);
		} catch (ControlException | UnsupportedMethod | StateException e) {
			e.printStackTrace();
		} finally {
			camera.releaseControlList();
		}
		processorEnabled = true;
	}
	/**
	 * PEOPLEVISION(r)(tm): The only way for people to look at things (c)(sm)(r)
	 * <p>
	 * This 
	 * @param server 
	 */
	public static void disableProcessor(MJPEGServer server) {
		System.out.println("DISABLING CV");
		if (camera == null) {
			processorEnabled = false;
			return;
		}
		if (server != null)
			server.offerRectangles(Collections.emptyList());
		try {
			ControlList controls = camera.getControlList();
			try {
				controls.getControl("Exposure, Auto").setValue(2);
			} catch (ControlException e) {}
			controls.getControl("Exposure (Absolute)").setValue(156);
			controls.getControl("Contrast").setValue(10);
			controls.getControl("Saturation").setValue(83);
			controls.getControl("Sharpness").setValue(50);
			controls.getControl("Brightness").setValue(42);
		} catch (ControlException | UnsupportedMethod | StateException e) {
			e.printStackTrace();
		} finally {
			camera.releaseControlList();
		}
		processorEnabled = false;
	}
	/**
	 * Set the JPEG quality from the camera. Tests have shown that this does <b>NOT</b> reduce the
	 * MJPEG stream's bandwidth.
	 * @param quality quality to set. Must be 0 to 100 (inclusive)
	 */
	public static void setQuality(int quality) {
		if (frameGrabber == null)
			return;
		System.out.println("SETTING QUALITY TO " + quality);
		frameGrabber.setJPGQuality(quality);
	}
	/**
	 * Create and initialize the server
	 * @param args the command line arguments
	 * @return server, if created, or null
	 * @throws IOException
	 */
	protected static MJPEGServer initServer(ParsedCommandLineArguments args, ExecutorService executor) throws IOException {
		int port = args.getOrDefault("--port", DEFAULT_PORT);
		
		if (port > 0 && !args.isFlagSet("--no-server")) {
			MJPEGServer server = new MJPEGServer(new InetSocketAddress(port));
			server.runOn(executor);
			server.start();
			return server;
		} else {
			System.out.println("SERVER DISABLED");
		}
		return null;
	}
	protected static void testConverter(VideoDevice dev) {
		@SuppressWarnings("unused")
		JPEGEncoder encoder = JPEGEncoder.to(width, height, ImagePalette.YUYV);
	}
	/**
	 * Binds to and initializes the camera.
	 * @param args parsed command line arguments
	 * @return the camera device, or null if <kbd>--no-camera</kbd> is set.
	 * @throws V4L4JException
	 */
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
	/**
	 * Loads the CommandLineParser from inside the JAR.
	 * @return parser.
	 */
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
			.addFlag("--verbose", "Enable verbose output (not implemented)")
			.alias("-v", "--verbose")
			.addFlag("--version", "Print the version string.")
			.addFlag("--out", "Specify where to write log messages to (not implemented)")
			.addKvPair("--test", "target", "Run test by name. Tests include 'converter', 'controls', 'client', and 'sse'.")
			.addKvPair("--props", "file", "Specify the file to read properties from (not implemented)")
			.addKvPair("--write-props", "file", "Write properties to file, which can be passed into the --props arg in the future (not implemented)")
			.addFlag("--rebuild-parser", "Rebuilds the parser binary file")
			// Camera options
			.addKvPair("--camera", "device", "Specify the camera device file to use. Default '/dev/video0'")
			.alias("-C", "--camera")
			.addKvPair("--width", "px", "Set the width of image to capture/broadcast")
			.addKvPair("--height", "px", "Set the height of image to capture/broadcast")
			.addKvPair("--jpeg-quality", "quality", "Set the JPEG quality to request. Must be 1-100")
			.addKvPair("--fps-num", "numerator", "Set the FPS numerator. If the camera does not support the set framerate, the closest one available is chosen.")
			.addKvPair("--fps-denom", "denom", "Set the FPS denominator. If the camera does not support the set framerate, the closest one available is chosen.")
			// HTTP server options
			.addKvPair("--port", "port", "Specify the port for the HTTP server to listen on. Default 5800; a negative port number is equivalent to --no-server")
			.alias("-p", "--port")
			// GPIO options
			.addKvPair("--gpio-pin", "pin number", "Set which GPIO pin to use. Is ignored if --no-gpio is set")
			// Image processor options
			.addKvPair("--x-skip", "px", "Number of pixels to skip on the x axis when processing sweep 1 (not implemented)")
			.addKvPair("--y-skip", "px", "Number of pixels to skip on the y axis when processing sweep 1 (not implemented)")
			.addFlag("--trace-contours", "Enable the (dev) contour tracing algorithm")
			.addFlag("--save-diff", "Save the diff image to a file (./img/delta[#].png). Requires processor.")
			// Client options
			.addKvPair("--udp-target", "address", "Specify the address to broadcast UDP packets to")
			.alias("--rio-addr", "--udp-target")
			.addKvPair("--udp-port", "port", "Specify the port to send UDP packets to. Default 5801; a negative port number is equivalent to --no-udp.")
			.alias("--rio-port", "--udp-port")
			.addKvPair("--mdns-resolve-retry", "time", "Set the interval to retry to resolve the Rio's address")
			.alias("--rio-resolve-retry", "--mdns-resolve-retry")
			// Disabling stuff options
			.addFlag("--no-server", "Disable the HTTP server.")
			.addFlag("--no-process", "Disable image processing.")
			.addFlag("--no-camera", "Do not specify a camera. This option will cause the program to not invoke v4l4j.")
			.addFlag("--no-udp", "Disable broadcasting UDP.")
			.addFlag("--no-gpio", "Disable attaching to a pin. Invoking this option will not invoke WiringPi. Note that the pin is reqired for image processing.")
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
