package com.moe365.mopi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class PiStatusThread implements Runnable {
	public static final String MARGIN = "                     ";
	@Override
	public void run() {
		Runtime rt = Runtime.getRuntime();
		System.out.println("Started pi status task @ " + System.currentTimeMillis());
		rt.addShutdownHook(new Thread(()->{System.out.println("Shutting down at " + System.currentTimeMillis());}));
		try {
			throw new InterruptedException();
		} catch (InterruptedException e2) {
			e2.printStackTrace();
			System.err.println("Unable to get CPU speed");
		}
		while (!Thread.interrupted()) {
			System.out.println("CPU T: ");
			Process cpuTempProcess = null, gpuTempProcess = null;
			try {
				cpuTempProcess = rt.exec(new String[]{"/opt/vc/bin/vcgencmd", "measure_temp"});
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			try {
				gpuTempProcess = rt.exec(new String[]{"/opt/vc/bin/vcgencmd", "measure_temp"});
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			try {
				cpuTempProcess.waitFor();
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}
			try (BufferedReader br = new BufferedReader(new InputStreamReader(cpuTempProcess.getInputStream()))) {
				while (br.ready())
					System.out.println(br.readLine());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	protected String fill(String f, int len) {
		if (f.length() < len)
			return MARGIN.substring(len - f.length()).concat(f);
		return f;
	}
}
