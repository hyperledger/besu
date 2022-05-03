/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.chainimport.JsonBlockImporter;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.logging.BesuLoggingConfigurationFactory;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.services.BesuPluginContextImpl;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.RunLast;

public final class Besu {
  private static final int SUCCESS_EXIT_CODE = 0;
  private static final int ERROR_EXIT_CODE = 1;

  public static void main(final String... args) {
    final Logger logger = setupLogging();

    final BesuCommand besuCommand =
        new BesuCommand(
            logger,
            RlpBlockImporter::new,
            JsonBlockImporter::new,
            RlpBlockExporter::new,
            new RunnerBuilder(),
            new BesuController.Builder(),
            new BesuPluginContextImpl(),
            System.getenv());

    besuCommand.parse(
        new RunLast().andExit(SUCCESS_EXIT_CODE),
        besuCommand.exceptionHandler().andExit(ERROR_EXIT_CODE),
        System.in,
        args);
  }

  private static Logger setupLogging() {
    InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
    try {
      System.setProperty(
          "vertx.logger-delegate-factory-class-name",
          "io.vertx.core.logging.Log4j2LogDelegateFactory");
      System.setProperty(
          "log4j.configurationFactory", BesuLoggingConfigurationFactory.class.getName());
      System.setProperty("log4j.skipJansi", String.valueOf(false));
    } catch (SecurityException e) {
      System.out.println(
          "Could not set logging system property as the security manager prevented it:"
              + e.getMessage());
    }
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
}
