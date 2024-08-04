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
package org.hyperledger.besu.tests.acceptance.dsl;

import java.io.File;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcceptanceTestBaseTestWatcher implements TestWatcher {
  private static final Logger LOG = LoggerFactory.getLogger(AcceptanceTestBaseTestWatcher.class);

  @Override
  public void testFailed(final ExtensionContext extensionContext, final Throwable e) {
    // add the result at the end of the log, so it is self-sufficient
    LOG.error(
        "==========================================================================================");
    LOG.error("Test failed. Reported Throwable at the point of failure:", e);
    LOG.error(e.getMessage());
  }

  @Override
  public void testSuccessful(final ExtensionContext extensionContext) {
    // if so configured, delete logs of successful tests
    if (!Boolean.getBoolean("acctests.keepLogsOfPassingTests")) {
      try {
        // log4j is configured to create a file per test
        // build/acceptanceTestLogs/${ctx:class}.${ctx:test}.log
        String pathname =
            "build/acceptanceTestLogs/"
                + extensionContext.getTestClass().get().getSimpleName()
                + "."
                + extensionContext.getTestMethod().get().getName()
                + ".log";
        LOG.info("Test successful, deleting log at {}", pathname);
        final File file = new File(pathname);
        file.delete();
      } catch (final Exception e) {
        LOG.error("could not delete test file", e);
      }
    }
  }
}
