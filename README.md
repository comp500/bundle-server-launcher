# Woven API Module Template

This repository is a template for every Woven module.

## Getting Started

Generate a new repository with this template and clone it.

Replace the namespace, the name, and description in `gradle.properties` and `fabric.mod.json`.
Then remove the dummy example classes in `src/main/java`.

Now you can start writing the module!

## Conventions

 - A namespace, or previously "modid", is written in snake_case, no hyphens are used.
 - All package names are singular.
 - API is in `net.wovenmc.woven.api[.(client)|(server)]` package. Other subpackages are allowed.
 - Implementation is in `net.wovenmc.woven.impl[.(client)|(server)]` package. Other subpackages are allowed. 
 - Mixins is in `net.wovenmc.woven.mixin[.(client)|(server)]` package. Other subpackages are allowed.
 - Mixins JSON are named `<namespace>.mixins.json`.
 - Mixin classes have `Mixin` as suffix, if the mixin is only an accessor then it has to be suffixed `Accessor` instead of `Mixin`.

## Source sets

 - `main` - The main module code, contains the API, mixins and implementation.
 - `test` - The test source set, only used for unit testing code that is not Minecraft-related.
 - `testmod` - The test mod made to test the module inside Minecraft.

## Tools

### Gradle

 - `build` task will produce the main Jar file, the remapped Jar file, 
   the sources Jar file, the remapped Sources Jar file, and the JavaDoc Jar file.
 - `genSources` task will generate the remapped Minecraft sources.

### Run client/server tasks

The tasks will launch the game with the `testmod` source set and the `main` source set as dependency.

### License header

To ensure that every Java files have a license header, please execute the `checkLicenses` Gradle task.

You can also use the `updateLicenses` task to update/add the license header to every Java file.

### Checkstyle

To ensure a common code style there is a checkstyle file, you can check your module code style 
by executing the `check` task which will check the coding style and produce a report.
