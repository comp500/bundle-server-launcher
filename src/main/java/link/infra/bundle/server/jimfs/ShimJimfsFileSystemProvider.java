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
import java.lang.invoke.MethodType;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

/**
 * This exists as a shim, to act as the system FileSystemProvider for jimfs but forwarding to
 * SystemJimfsFileSystemProvider in the loaded classloader.
 */
public class ShimJimfsFileSystemProvider extends FileSystemProvider {
	// Should be constructed by Java itself
	public ShimJimfsFileSystemProvider() { }

	private static MethodHandle newFileSystemTarget;
	private static MethodHandle getFileSystemTarget;
	private static MethodHandle getPathTarget;
	private static MethodHandle removeFileSystemRunnableTarget;

	public static void initialize(ClassLoader targetLoader) {
		try {
			Class<?> providerClass = targetLoader.loadClass("com.google.common.jimfs.SystemJimfsFileSystemProvider");
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			Object targetProvider = providerClass.newInstance();

			newFileSystemTarget = lookup.findVirtual(providerClass, "newFileSystem",
				MethodType.methodType(FileSystem.class, URI.class, Map.class)).bindTo(targetProvider);
			getFileSystemTarget = lookup.findVirtual(providerClass, "getFileSystem",
				MethodType.methodType(FileSystem.class, URI.class)).bindTo(targetProvider);
			getPathTarget = lookup.findVirtual(providerClass, "getPath",
				MethodType.methodType(Path.class, URI.class)).bindTo(targetProvider);
			removeFileSystemRunnableTarget = lookup.findStatic(providerClass, "removeFileSystemRunnable",
				MethodType.methodType(Runnable.class, URI.class));
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | ClassCastException e) {
			throw new RuntimeException("Failed to load shim file system provider", e);
		}
	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		try {
			return (FileSystem) newFileSystemTarget.invokeExact(uri, env);
		} catch (Throwable e) {
			if (e instanceof IOException) {
				throw (IOException) e;
			} else {
				throw new RuntimeException("Fatal jimfs invocation failure", e);
			}
		}
	}

	@Override
	public FileSystem getFileSystem(URI uri) {
		try {
			return (FileSystem) getFileSystemTarget.invokeExact(uri);
		} catch (Throwable e) {
			throw new RuntimeException("Fatal jimfs invocation failure", e);
		}
	}

	@Override
	public Path getPath(URI uri) {
		try {
			return (Path) getPathTarget.invokeExact(uri);
		} catch (Throwable e) {
			throw new RuntimeException("Fatal jimfs invocation failure", e);
		}
	}

	// Called with reflection
	@SuppressWarnings("unused")
	public static Runnable removeFileSystemRunnable(final URI uri) {
		try {
			return (Runnable) removeFileSystemRunnableTarget.invokeExact(uri);
		} catch (Throwable e) {
			throw new RuntimeException("Fatal jimfs invocation failure", e);
		}
	}

	@Override
	public String getScheme() {
		return "jimfs";
	}

	/*
		Further methods are not implemented on com.google.common.jimfs.SystemJimfsFileSystemProvider
	 */

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
		throw new UnsupportedOperationException();
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void delete(Path path) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isSameFile(Path path, Path path2) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isHidden(Path path) {
		throw new UnsupportedOperationException();
	}

	@Override
	public FileStore getFileStore(Path path) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
		throw new UnsupportedOperationException();
	}
}
