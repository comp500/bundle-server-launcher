/**
 * This package contains shim classes for the jimfs (<a href="https://github.com/google/jimfs">https://github.com/google/jimfs</a>)
 * URL handler and FileSystemProvider. These are necessary as follows:
 *
 * <ul>
 *     <li>In order to make Minecraft's classes available for class loading, we need to add them to the classpath.
 *     <li>We can't add the Minecraft jar directly to the current classloader, as it would require reflection to
 *         internal Java classes or even instrumentation!
 *     <li>Therefore, we need to use our own classloader to launch the game, which has our jar and the Minecraft jar.
 *     <li>This classloader needs to be isolated from the system classloader, as Fabric references dependencies in the
 *         Minecraft jar - classes loaded on the system classloader can't reference classes on our own classloader
 *         directly.
 *     <li>However, Java's URLStreamHandler and FileSystemProvider interface implementations are only searched for on
 *         the system classloader, so jimfs will break.
 *     <li>We can work around this by making shim classes that have a reference to our own classloader and call the
 *         jimfs classes on that classloader using reflection - so guava and jimfs are only loaded once!
 * </ul>
 *
 * This technique also avoids the "duplicate input class" issue in fabric-server-launcher, caused by having Guava in
 * both Minecraft's jar and the fabric-server-launcher jar.
 */
package net.wovenmc.serverlauncher.jimfs;