package com.moe365.mopi.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PiStatus {
	public static final String CPU_CURRENT_FREQUENCY_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
	public static final String CPU_MIN_FREQUENCY_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq";
	public static final String CPU_MAX_FREQUENCY_FILE = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
	public static final String CPU_TEMP_FILE = "/sys/class/thermal/thermal_zone0/temp";
	public static final List<String> VCGENCMD_FREQ_TARGETS = Collections.unmodifiableList(Arrays.asList("arm","core","h264","isp","v3d","uart","pwm","emmc","pixel","vec","hdmi","dpi"));
	public static final List<String> VCGENCMD_VOLT_TARGETS = Collections.unmodifiableList(Arrays.asList("core","sdram_c","sdram_i","sdram_p"));
	public static final List<String> VCGENCMD_CODECS = Collections.unmodifiableList(Arrays.asList("H264","MPG2","WVC1","MPG4","MJPG","WMV9"));
	protected final Runtime runtime;
	public PiStatus(Runtime runtime) {
		this.runtime = runtime;
	}
	
	public double getCpuFrequency() throws IOException, InterruptedException {
		return catDouble(CPU_CURRENT_FREQUENCY_FILE);
	}
	public double getCpuMaxFrequency() throws IOException, InterruptedException {
		return catDouble(CPU_MAX_FREQUENCY_FILE);
	}
	public double getCpuMinFrequency() throws IOException, InterruptedException {
		return catDouble(CPU_MIN_FREQUENCY_FILE);
	}
	public double getCpuTemperature() throws IOException, InterruptedException {
		return catDouble(CPU_MIN_FREQUENCY_FILE);
	}
	public double vcgencmd(String cmd, String target) throws IOException, InterruptedException {
		Process vcgencmd = runtime.exec(new String[]{"/opt/vc/bin/vcgencmd",cmd, target});
		vcgencmd.waitFor();
		try (InputStream is = vcgencmd.getInputStream()) {
			byte[] data = new byte[is.available()];
			is.read(data);
			return Double.parseDouble(new String(data));
		}
	}
	protected double catDouble(String file) throws IOException, InterruptedException {
		byte[] data = cat(file);
		String text = new String(data, Charset.defaultCharset());
		return Double.parseDouble(text);
	}
	protected byte[] cat(String file) throws IOException, InterruptedException {
		Process catProcess = runtime.exec(new String[]{"cat", file});
		catProcess.waitFor();
		try (InputStream is = catProcess.getInputStream()) {
			byte[] buffer = new byte[is.available()];
			is.read(buffer);
			return buffer;
		}
	}
}
