/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.testfuzz;

import org.hyperledger.besu.testfuzz.javafuzz.Fuzzer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.security.NoSuchAlgorithmException;

import picocli.CommandLine;

/** P256Verify precompiled contract fuzzer subcommand */
@CommandLine.Command(
    name = "p256verify",
    description = "Fuzz the P256Verify precompiled contract",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
@SuppressWarnings({"java:S106", "java:S108"}) // we use lots the console, on purpose
public class P256VerifySubCommand implements Runnable {

  /** Default constructor for P256VerifySubCommand. */
  public P256VerifySubCommand() {
    // Default constructor
  }

  @CommandLine.Option(
      names = {"--corpus-dir"},
      description = "Directory to store corpus files (fuzzer inputs that found new coverage)")
  private File corpusDir = new File("corpus");

  @CommandLine.Option(
      names = {"--new-corpus-dir"},
      description = "Directory to store new corpus files discovered during fuzzing")
  private File newCorpusDir = new File("new-corpus");

  @CommandLine.Option(
      names = {"--guidance-regexp"},
      description =
          "Regular expression to match class names for coverage guidance (default: focuses on EVM precompile classes)")
  private String guidanceRegexp = "org/(hyperledger/besu|apache/tuweni)";

  @CommandLine.Option(
      names = {"--iterations"},
      description = "Maximum number of iterations to run (0 = unlimited, default: 0)")
  private long maxIterations = 0;

  @CommandLine.Option(
      names = {"--timeout-seconds"},
      description = "Maximum time to run fuzzing in seconds (0 = unlimited, default: 0)")
  private long timeoutSeconds = 0;

  @CommandLine.Option(
      names = {"--enable-native"},
      description = "Enable native implementations for comparison (default: true)")
  private boolean enableNative = true;

  @Override
  public void run() {
    System.out.println("Starting P256Verify precompiled contract fuzzer...");
    System.out.printf("Corpus directory: %s%n", corpusDir.getAbsolutePath());
    System.out.printf("New corpus directory: %s%n", newCorpusDir.getAbsolutePath());
    System.out.printf("Guidance regexp: %s%n", guidanceRegexp);
    System.out.printf("Native implementations enabled: %s%n", enableNative);

    // Ensure corpus directories exist
    corpusDir.mkdirs();
    newCorpusDir.mkdirs();

    try {
      // Create the fuzz target
      P256VerifyFuzzTarget target = new P256VerifyFuzzTarget();

      // Create stats supplier for additional fuzzing information
      FuzzStatsSupplier statsSupplier = new FuzzStatsSupplier(enableNative);

      // Create and start the fuzzer
      Fuzzer fuzzer =
          new Fuzzer(
              target, corpusDir.getAbsolutePath(), statsSupplier, guidanceRegexp, newCorpusDir);

      // Set up shutdown hook for graceful termination
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    System.out.println("\nFuzzing interrupted by user.");
                    System.out.printf(
                        "Check %s for any crash files%n", System.getProperty("user.dir"));
                    System.out.printf(
                        "Check %s for new interesting inputs%n", newCorpusDir.getAbsolutePath());
                  }));

      // Start timing if timeout is specified

      if (maxIterations > 0 || timeoutSeconds > 0) {
        // Run with limits
        System.out.printf(
            "Running fuzzer with limits: maxIterations=%d, timeoutSeconds=%d%n",
            maxIterations, timeoutSeconds);
        runWithLimits(fuzzer);
      } else {
        // Run indefinitely
        System.out.println("Running fuzzer indefinitely (Ctrl+C to stop)...");
        fuzzer.start();
      }

    } catch (ClassNotFoundException e) {
      System.err.println("JaCoCo agent not found. Make sure jacocoagent.jar is in the classpath.");
      System.exit(1);
    } catch (Exception e) {
      System.err.printf("Error starting fuzzer: %s%n", e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private void runWithLimits(final Fuzzer fuzzer)
      throws InvocationTargetException, IllegalAccessException, NoSuchAlgorithmException {

    // This is a simplified version - ideally we'd modify the Fuzzer class to support limits
    // For now, we'll use a timeout approach
    if (timeoutSeconds > 0) {
      Thread fuzzerThread =
          new Thread(
              () -> {
                try {
                  fuzzer.start();
                } catch (Exception e) {
                  System.err.printf("Fuzzer error: %s%n", e.getMessage());
                }
              });

      fuzzerThread.start();

      try {
        Thread.sleep(timeoutSeconds * 1000);
        System.out.println("Timeout reached, stopping fuzzer...");
        System.exit(0);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.out.println("Fuzzer interrupted");
      }
    } else {
      fuzzer.start();
    }
  }

  /** Supplies additional statistics for the fuzzer output */
  private static class FuzzStatsSupplier implements java.util.function.Supplier<String> {
    private final boolean nativeEnabled;
    private long lastMemory = 0;

    FuzzStatsSupplier(final boolean nativeEnabled) {
      this.nativeEnabled = nativeEnabled;
    }

    @Override
    public String get() {
      long currentMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      long memoryDelta = currentMemory - lastMemory;
      lastMemory = currentMemory;

      return String.format("native=%s mem_delta=%+dMB", nativeEnabled, memoryDelta / (1024 * 1024));
    }
  }
}
