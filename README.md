# MOE 365 FRC Stronghold Raspberry Pi Code
<table><tr><th colspan=2>Stronghold 2016 Code</th></tr><tr><td><a href="github.com/MOERobotics/2016Stronghold-CaMOElot">RoboRio</a></td><td>Raspberry Pi</td></tr></table>
Raspberry Pi code for FRC stronghold challenge.

# Details
The program is written in a heavily modular fashion, where (almost?) every segment of code can be disabled at runtime. This modularity is advantageous at competitions, as allows massive changes in the system with no or minimal changes to the code. In fact, we were able to use GRIP to detect the goal (as a fallback, in case the head referees decided that the flashing LED was against the rules), instead of the normal image processing module, by only changing a few command line flags. In spite of this model, the program is by no means fragmented, and each module is deeply integrated with multiple others.

## Image Capturing
To capture images from a variety of webcams, we use [v4l4j](/mailmindlin/v4l4j). With this library, we are able to not only recieve frames from the cameras, but also control the settings, to better process images.

### LED Control
More of a sub-module, MoePi uses the [Pi4J library](pi4j.com) to control a LED. While this task may seem simple, which it is, it plays a major part in the image processing, synchronized exactly with the camera.

## HTTP Server
A NIO-based HTTP server, it runs with little latency, while still serving with high bandwith and providing many featues. Not only offering a live MJPEG stream of the webcam, a HTML5 interface offers controls to the drivers drivers to draw overlays on the image (such as crosshairs; using the client's GPU) and even control the camera's settings.

## Image processing
Our image processor works by not looking for places in an image that are lit up, but by flashing a light at the retroreflective tape, and measuring the difference between two frames - one with the flash on, and one with it off. Through this technique, we are able to provide accurate results with low latency and error rates.

## Data Broadcasting
To keep latency and bandwidth low, we developed a custom UDP packet structure to communicate to the RoboRio.
