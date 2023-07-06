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
package org.hyperledger.besu.ethereum.api.query;

import org.hyperledger.besu.ethereum.api.handlers.RpcMethodTimeoutException;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackendQuery {
  private static final Logger LOG = LoggerFactory.getLogger(BackendQuery.class);

  public static <T> T runIfAlive(final Callable<T> task, final Supplier<Boolean> alive)
      throws Exception {
    return runIfAlive(Optional.empty(), task, alive);
  }

  public static <T> T runIfAlive(
      final String taskName, final Callable<T> task, final Supplier<Boolean> alive)
      throws Exception {
    return runIfAlive(Optional.ofNullable(taskName), task, alive);
  }

  public static <T> T runIfAlive(
      final Optional<String> taskName, final Callable<T> task, final Supplier<Boolean> alive)
      throws Exception {
    if (!alive.get()) {
      LOG.warn(
          "Zombie backend query detected [ {} ], aborting process.", taskName.orElse("unnamed"));
      throw new RpcMethodTimeoutException();
    }
    return task.call();
  }

  public static void stopIfExpired(final Supplier<Boolean> alive) throws Exception {
    runIfAlive(() -> null, alive);
  }
}
