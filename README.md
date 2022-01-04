# Eurotoken

## What is Eurotoken?
Eurotoken is a project that aims to explore the feasibility of designing a Central Bank Digital Currency (CDBC) using [IPv8](https://github.com/Tribler/kotlin-ipv8) and Trustchain. Current research goals include:
- Supporting 1 million transactions per second.
- Connecting to strong identities to deter fraud.
- Improving robustness with an offline disaster-proof mode.

This repository contains a command line application of the Eurotoken project, built upon [Kotlin IPv8](https://github.com/Tribler/kotlin-ipv8).

## Building Eurotoken
After cloning this repository (and the `kotlin-ipv8` submodule), first start building `kotlin-ipv8`. The module contains its own `build.gradle`, `gradlew`, and build instructions in `README.md`.

After `kotlin-ipv8` has been built successfully, we can start building `eurotoken`. Enter the `kotlin-ipv8` folder once more and switch to the Git branch `fix-trustchain-coroutines-jvm`, presumably using `git checkout fix-trustchain-coroutines-jvm`. Now navigate to the `eurotoken` folder.

As when building `kotlin-ipv8`, run `gradlew build` using JDK 1.8. Either modify your `JAVA_HOME` path variable to point to JDK 1.8 or add a line to `gradle.properties` with `org.gradle.java.home=</path_to_jdk_directory>` (see this [stackoverflow link](https://stackoverflow.com/questions/18487406/how-do-i-tell-gradle-to-use-specific-jdk-version) for a discussion on the topic). Note that both `kotlin-ipv8` and `eurotoken` have separate `gradle.properties` files that both need to be modified. Make sure to use forward slashes (`/`) for your path. Next, use `gradlew run` to run.

To run `Main.kt` with a startup script, for instance to initialise clients with some eurotoken, first create the file (which we will call `script.txt`) in the `eurotoken` root and then run Gradle with `gradlew run --args=script.txt`.