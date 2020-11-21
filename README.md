# Bundle Server Launcher

Bundle Server Launcher (formerly Woven Server Launcher) is a single-jar server bootstrapper for Fabric loader, which does not require an installer for
users to use.

The `build` task (`gradlew build`) will make:

- A complete `-all` jar with all dependencies and configuration bundled, which can be distributed to users
- A jar containing only the compiled Bundle Server Launcher code, which you can merge yourself with the dependencies
  (Fabric loader, it's dependencies as listed in meta.fabricmc.net except for guava, and intermediary mappings),
  replacing the jimfs `META-INF/services/java.nio.file.spi.FileSystemProvider` file with the one provided, and a
  `bundle-server-launcher.properties` file with appropriate values for `launch.mainClass`, `serverJarUrl` and `serverJarHash`.
- A sources jar - not really necessary, but it's there in case you want it

Use the `run` task to directly run the complete jar.
