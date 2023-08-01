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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.util.LogConfigurator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This class can be used to extend tests. It allows to rerun tests with trace tests enabled
 * whenever they failed.
 *
 * <p>The test only reruns if the logging is not configured to use trace logging.
 *
 * <p>To turn trace logging manually, you can set 2 system properties during execution of the tests:
 *
 * <ul>
 *   <li>-Devm.log.level=trace
 *   <li>-Droot.log.level=trace
 * </ul>
 */
public abstract class AbstractRetryingTest {

  private static final String originalEvmLogLevel = System.getProperty("evm.log.level");
  private static final String originalRootLogLevel = System.getProperty("root.log.level");

  /** Sets the logging system back to the original parameters with which the tests were launched. */
  @BeforeEach
  public void resetLoggingToOriginalConfiguration() {
    if (originalRootLogLevel == null) {
      System.clearProperty("root.log.level");
    } else {
      System.setProperty("root.log.level", originalRootLogLevel);
    }
    if (originalEvmLogLevel == null) {
      System.clearProperty("evm.log.level");
    } else {
      System.setProperty("evm.log.level", originalEvmLogLevel);
    }
    resetLogging();
  }

  /** Run the test case. */
  @Test
  public void execution() {
    try {
      runTest();
    } catch (final RuntimeException | AssertionError e) {
      if (!"trace".equalsIgnoreCase(originalRootLogLevel)
          || !"trace".equalsIgnoreCase(originalEvmLogLevel)) {
        // try again, this time with more logging, so we can capture more information.
        System.setProperty("root.log.level", "trace");
        System.setProperty("evm.log.level", "trace");
        resetLogging();
        runTest();
      } else {
        throw e;
      }
    }
  }

  private void resetLogging() {
    LogConfigurator.reconfigure();
  }

  /** Subclasses should implement this method to run the actual JUnit test. */
  protected abstract void runTest();
}
