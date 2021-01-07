# Bundle Server Launcher

Bundle Server Launcher (formerly Woven Server Launcher) is a single-jar server bootstrapper for Fabric loader, which does not require an installer for
users to use.

The `build` task (`gradlew build`) will make:

- A complete `bundle-fabric` jar with all dependencies and configuration bundled, which can be distributed to users
    - Configure what Fabric Loader and Minecraft versions to use in gradle.properties
- A `-base` jar containing only the compiled Bundle Server Launcher code, which you can merge yourself with the dependencies
  (Fabric loader, it's dependencies as listed in meta.fabricmc.net except for guava, and intermediary mappings),
  replacing the jimfs `META-INF/services/java.nio.file.spi.FileSystemProvider` file with the one provided, and a
  `bundle-server-launcher.properties` file with appropriate values for `launch.mainClass`, `serverJarUrl` and `serverJarHash`.

The `buildMulti` task makes JARs for several different Minecraft versions - used for CI

Use the `run` task to directly run the complete jar.
