package net.wovenmc.serverlauncher.jimfs;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * This exists as a shim, to act as the URLStreamHandler implementation for jimfs but forwarding to
 * com.google.common.jimfs.Handler in the loaded classloader. As long as this is registered before the main one,
 * there should be no issues with Java using the main class instead of the shim.
 */
public final class Handler extends URLStreamHandler {
	private static MethodHandle openConnectionTarget;

	public static void register(ClassLoader targetLoader) {
		String existingPackages = System.getProperty("java.protocol.handler.pkgs");
		if (existingPackages == null) {
			System.setProperty("java.protocol.handler.pkgs", "net.wovenmc.serverlauncher");
		} else {
			System.setProperty("java.protocol.handler.pkgs", existingPackages + "|net.wovenmc.serverlauncher");
		}

		try {
			Class<?> handlerClass = targetLoader.loadClass("com.google.common.jimfs.Handler");
			Method openConnectionMethod = handlerClass.getDeclaredMethod("openConnection", URL.class);
			openConnectionMethod.setAccessible(true);
			Object targetHandler = handlerClass.newInstance();
			openConnectionTarget = MethodHandles.lookup().unreflect(openConnectionMethod).bindTo(targetHandler);
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | ClassCastException e) {
			throw new RuntimeException("Failed to load shim handler", e);
		}
	}

	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		try {
			return (URLConnection) openConnectionTarget.invokeExact(u);
		} catch (Throwable e) {
			if (e instanceof IOException) {
				throw (IOException) e;
			} else {
				throw new RuntimeException("Fatal jimfs invocation failure", e);
			}
		}
	}

	@Override
	protected synchronized InetAddress getHostAddress(URL u) {
		// See com.google.common.jimfs.Handler
		return null;
	}
}
