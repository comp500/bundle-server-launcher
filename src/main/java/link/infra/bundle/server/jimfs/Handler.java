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

package link.infra.bundle.server.jimfs;

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
			System.setProperty("java.protocol.handler.pkgs", "link.infra.bundle.server");
		} else {
			System.setProperty("java.protocol.handler.pkgs", existingPackages + "|link.infra.bundle.server");
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
