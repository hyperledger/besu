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
package org.hyperledger.besu.tests.acceptance.dsl;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;

public class WaitUtils {
  public static void waitFor(final ThrowingRunnable condition) {
    waitFor(30, condition);
  }

  public static void waitFor(final int timeout, final ThrowingRunnable condition) {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(timeout, TimeUnit.SECONDS)
        .untilAsserted(condition);
  }
}
