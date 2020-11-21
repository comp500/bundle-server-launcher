/*
 * Copyright (c) 2020 comp500
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package link.infra.bundle.server;

import link.infra.bundle.server.jimfs.Handler;
import link.infra.bundle.server.jimfs.ShimJimfsFileSystemProvider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Properties;

public class Main {
	private static final String VERSION = Main.class.getPackage().getImplementationVersion();
	private static final String USER_AGENT = "bundle-server-launcher/" + VERSION;
	private static final String DEF_SERVER_JAR_PATH = ".fabric/bundle-cache/server.jar";
	private static final int MAX_DOWNLOAD_TRIES = 3;

	public static void main(String[] args) {
		Properties launcherProps = new Properties();

		// Read properties from own JAR
		try (InputStream is = Main.class.getClassLoader().getResourceAsStream("bundle-server-launcher.properties")) {
			if (is == null) {
				throw new FileNotFoundException("bundle-server-launcher.properties");
			}

			launcherProps.load(is);
		} catch (IOException e) {
			System.err.println("Failed to load bundle-server-launcher included properties:");
			e.printStackTrace();
			System.err.println("The launcher jar is corrupt, please redownload it!");
			System.exit(1);
		}

		// Properties can be overridden; bundle-server-launcher takes priority over fabric-server-launcher
		Path bundlePropPath = Paths.get("bundle-server-launcher.properties");
		Path fabricPropPath = Paths.get("fabric-server-launcher.properties");

		if (Files.exists(bundlePropPath)) {
			try (BufferedReader br = Files.newBufferedReader(bundlePropPath)) {
				launcherProps.load(br);
			} catch (NoSuchFileException ignored) {
				// Ignored
			} catch (IOException e) {
				System.err.println("Failed to read configuration file bundle-server-launcher.properties:");
				e.printStackTrace();
				System.exit(1);
			}
		} else if (Files.exists(fabricPropPath)) {
			String origServerPath = launcherProps.getProperty("serverJar", DEF_SERVER_JAR_PATH);

			// Try to read from fabric-server-launcher, and copy serverJar into bundle-server-launcher to preserve it
			try (BufferedReader br = Files.newBufferedReader(fabricPropPath)) {
				launcherProps.load(br);
			} catch (NoSuchFileException ignored) {
				// Ignored
			} catch (IOException e) {
				System.err.println("Failed to read configuration file fabric-server-launcher.properties:");
				e.printStackTrace();
				System.exit(1);
			}

			if (!origServerPath.equals(launcherProps.getProperty("serverJar", origServerPath))) {
				try (BufferedWriter bw = Files.newBufferedWriter(bundlePropPath)) {
					Properties overrideProps = new Properties();
					overrideProps.put("serverJar", launcherProps.getProperty("serverJar"));
					overrideProps.store(bw, "Bundle server launcher properties - Delete this file to use the default location");
				} catch (IOException e) {
					System.err.println("Failed to save configuration file bundle-server-launcher.properties:");
					e.printStackTrace();
					System.exit(1);
				}
			}
		}

		String mainClass = launcherProps.getProperty("launch.mainClass");
		URL serverJarUrl = null;

		try {
			serverJarUrl = new URI(launcherProps.getProperty("serverJarUrl")).toURL();
		} catch (MalformedURLException | URISyntaxException e) {
			System.err.println("Invalid value of serverJarUrl: " + launcherProps.getProperty("serverJarUrl"));
			e.printStackTrace();
			System.exit(1);
		}

		byte[] serverJarHash = hexToBytes(launcherProps.getProperty("serverJarHash"));
		// Should work backwards-compatible with fabric-server-launcher
		Path serverJar = Paths.get(launcherProps.getProperty("serverJar", DEF_SERVER_JAR_PATH));

		boolean valid = false;

		try {
			verifyMinecraftJar(serverJar, serverJarHash);
			valid = true;
		} catch (InvalidHashException e) {
			System.out.println("Minecraft jar has invalid hash (expected " + e.expectedHash + ", found " + e.hashFound
					+ ") attempting to download...");
		} catch (NoSuchFileException e) {
			System.out.println("Downloading Minecraft jar...");
		} catch (IOException e) {
			System.out.println("Minecraft jar could not be read, attempting to download...");
		}

		if (!valid) {
			for (int i = 0; i < MAX_DOWNLOAD_TRIES; i++) {
				try {
					downloadMinecraftJar(serverJarUrl, serverJar);
					verifyMinecraftJar(serverJar, serverJarHash);
					valid = true;
					System.out.println("Successfully downloaded Minecraft jar!");
					break;
				} catch (InvalidHashException e) {
					System.err.println("Downloaded Minecraft jar has invalid hash (expected " + e.expectedHash + ", found "
							+ e.hashFound + ")");
				} catch (IOException e) {
					System.err.println("Download failed!");
					e.printStackTrace();
				}

				if (i < MAX_DOWNLOAD_TRIES - 1) {
					System.out.println("Retrying... (" + (i + 1) + "/" + (MAX_DOWNLOAD_TRIES - 1) + ")");
				}
			}
		}

		if (!valid) {
			System.err.println("Failed to download or validate the Minecraft jar!");
			System.err.println("If you are lacking a direct internet connection, you can download it from:");
			System.err.println("    " + serverJarUrl);
			System.err.println("and place it at " + serverJar);
			System.err.println("It should have the following SHA-1 hash: " + bytesToHex(serverJarHash));
			System.exit(1);
		}

		System.setProperty("fabric.gameJarPath", serverJar.toAbsolutePath().toString());

		URL serverJarFileUrl = null;

		try {
			serverJarFileUrl = serverJar.toUri().toURL();
		} catch (MalformedURLException e) {
			System.err.println("Invalid path for server jar: " + serverJar);
			e.printStackTrace();
			System.exit(1);
		}

		// Set the parent classloader to the parent of the AppClassLoader
		// This will be ExtClassLoader on Java 8 or older, PlatformClassLoader on Java 9 or newer - ensures extension classes will work
		URLClassLoader gameLoader = new URLClassLoader(new URL[] {
				serverJarFileUrl,
				Main.class.getProtectionDomain().getCodeSource().getLocation()
		}, ClassLoader.getSystemClassLoader().getParent());

		// Register the jimfs URL handler shim and FileSystemProvider shim
		Handler.register(gameLoader);
		ShimJimfsFileSystemProvider.initialize(gameLoader);

		Thread.currentThread().setContextClassLoader(gameLoader);

		try {
			Class<?> clazz = gameLoader.loadClass(mainClass);
			Method main = clazz.getMethod("main", String[].class);
			main.invoke(null, (Object) args);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Failed to load the Fabric class", e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("Failed to load the Fabric main method", e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Failed to call the Fabric main method", e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e.getTargetException());
		}
	}

	private static class InvalidHashException extends IOException {
		public final String expectedHash;
		public final String hashFound;

		InvalidHashException(String expectedHash, String hashFound) {
			this.expectedHash = expectedHash;
			this.hashFound = hashFound;
		}
	}

	private static void verifyMinecraftJar(Path serverJar, byte[] serverJarHash) throws IOException {
		MessageDigest digest;

		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to verify Minecraft server JAR", e);
		}

		// TODO: change to a stream?
		byte[] foundHash = digest.digest(Files.readAllBytes(serverJar));

		if (!Arrays.equals(foundHash, serverJarHash)) {
			throw new InvalidHashException(bytesToHex(serverJarHash), bytesToHex(foundHash));
		}
	}

	private static void downloadMinecraftJar(URL serverJarUrl, Path serverJar) throws IOException {
		Files.createDirectories(serverJar.getParent());

		URLConnection conn = serverJarUrl.openConnection();
		conn.setRequestProperty("User-Agent", USER_AGENT);
		conn.setRequestProperty("Accept", "application/octet-stream");
		// 2 minute read/connect timeouts
		conn.setConnectTimeout(2 * 60 * 1000);
		conn.setReadTimeout(2 * 60 * 1000);

		try (InputStream in = conn.getInputStream()) {
			Files.copy(in, serverJar, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];

		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}

		return new String(hexChars);
	}

	public static byte[] hexToBytes(String s) {
		int len = s.length();
		if (len % 2 != 0) throw new RuntimeException("Invalid hash " + s);
		byte[] data = new byte[len / 2];

		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
				+ Character.digit(s.charAt(i+1), 16));
		}

		return data;
	}
}
