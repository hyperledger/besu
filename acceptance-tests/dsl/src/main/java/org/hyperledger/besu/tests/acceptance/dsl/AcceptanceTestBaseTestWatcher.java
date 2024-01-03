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
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcceptanceTestBaseTestWatcher implements TestWatcher {
  private static final Logger LOG = LoggerFactory.getLogger(AcceptanceTestBaseTestWatcher.class);

  @Override
  public void testAborted(final ExtensionContext extensionContext, final Throwable throwable) {
    LOG.info("test aborted:" + extensionContext.getDisplayName());
  }

  @Override
  public void testDisabled(
      final ExtensionContext extensionContext, final Optional<String> optional) {
    LOG.info("test disabled:" + extensionContext.getDisplayName());
  }

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
    // TODO where is the other side of this - what creates these log files?

    // if so configured, delete logs of successful tests
    if (!Boolean.getBoolean("acctests.keepLogsOfPassingTests")) {
      String pathname = "build/acceptanceTestLogs/" + extensionContext.getDisplayName() + ".log";
      LOG.info("Test successful, deleting log at {}", pathname);
      File file = new File(pathname);
      file.delete();
    }
  }
}
