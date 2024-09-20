/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.logging.BesuLoggingConfigurationFactory;
import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.components.DaggerBesuComponent;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.RunLast;

import java.util.Locale;

/** Besu bootstrap class. */
public final class Besu {
  /** Default constructor. */
  public Besu() {
  }

  /**
   * The main entrypoint to Besu application
   *
   * @param args command line arguments.
   */
  public static void main(final String... args) {
    setupNative();
    setupLogging();
    final BesuComponent besuComponent = DaggerBesuComponent.create();
    final BesuCommand besuCommand = besuComponent.getBesuCommand();
    int exitCode =
        besuCommand.parse(
            new RunLast(),
            besuCommand.parameterExceptionHandler(),
            besuCommand.executionExceptionHandler(),
            System.in,
            besuComponent,
            args);

    System.exit(exitCode);
  }

  /**
   * a Logger setup for handling any exceptions during the bootstrap process, to indicate to users
   * their CLI configuration had problems.
   */
  public static void setupLogging() {
    try {
      InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
    } catch (Throwable t) {
      System.out.printf("Could not set netty log4j logger factory: %s - %s%n",
          t.getClass().getSimpleName(), t.getMessage());
    }
    try {
      System.setProperty("vertx.logger-delegate-factory-class-name",
          "io.vertx.core.logging.Log4j2LogDelegateFactory");
      System.setProperty("log4j.configurationFactory",
          BesuLoggingConfigurationFactory.class.getName());
      System.setProperty("log4j.skipJansi", String.valueOf(false));
    } catch (Throwable t) {
      System.out.printf("Could not set logging system property: %s - %s%n",
          t.getClass().getSimpleName(), t.getMessage());
    }
  }

  /**
   * Returns the first logger to be created. This is used to set the default uncaught exception
   *
   * @return Logger
   */
  public static Logger getFirstLogger() {
    final Logger logger = LoggerFactory.getLogger(Besu.class);
    Thread.setDefaultUncaughtExceptionHandler(slf4jExceptionHandler(logger));
    Thread.currentThread().setUncaughtExceptionHandler(slf4jExceptionHandler(logger));

    return logger;
  }

  private static Thread.UncaughtExceptionHandler slf4jExceptionHandler(final Logger logger) {
    return (thread, error) -> {
      if (logger.isErrorEnabled()) {
        logger.error(String.format("Uncaught exception in thread \"%s\"", thread.getName()), error);
      }
    };
  }

  /**
   * Set up the system jna path to include the paths used by besu-native.  Different JVMs
   * have different expectations about platform-arch strings for jna library locations.  This
   * method ensures we at least have the conventions used in besu-native.
   */
  public static void setupNative() {
    // Get existing jna.library.path if set, otherwise use an empty string
    String currentLibraryPath = System.getProperty("jna.library.path", "");

    // Get the OS and architecture properties
    String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    String arch = System.getProperty("os.arch");

    // Determine the correct directory based on OS and architecture
    String additionalLibraryPath = "";

    if (os.contains("linux")) {
      if (arch.equalsIgnoreCase("x86_64") || arch.equalsIgnoreCase("amd64")) {
        additionalLibraryPath = "linux-gnu-x86_64";
      } else if (arch.equalsIgnoreCase("aarch64")) {
        additionalLibraryPath = "linux-gnu-aarch64";
      }
    } else if (os.contains("mac")) {
      if (arch.equalsIgnoreCase("x86_64")) {
        additionalLibraryPath = "darwin-x86-64";
      } else if (arch.equalsIgnoreCase("aarch64")) {
        additionalLibraryPath = "darwin-aarch64";
      }
    }

    // If current path is not empty, append with the system path separator
    if (!currentLibraryPath.isEmpty()) {
      currentLibraryPath += System.getProperty("path.separator");
    }

    // Add the new path
    currentLibraryPath += additionalLibraryPath;

    // Set the updated jna.library.path
    System.setProperty("jna.library.path", currentLibraryPath);
  }
}
