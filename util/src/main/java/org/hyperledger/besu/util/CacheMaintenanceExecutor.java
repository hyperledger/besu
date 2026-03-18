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
package org.hyperledger.besu.util;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Shared single-thread executor for Caffeine cache maintenance (buffer draining, eviction). Using
 * this executor offloads housekeeping from caller threads, keeping the hot path lock-free.
 */
public final class CacheMaintenanceExecutor {

  private static final Executor INSTANCE =
      Executors.newSingleThreadExecutor(
          r -> {
            final Thread t = new Thread(r, "caffeine-maintenance");
            t.setDaemon(true);
            return t;
          });

  private CacheMaintenanceExecutor() {}

  /**
   * Returns the shared cache maintenance executor.
   *
   * @return the shared cache maintenance executor
   */
  public static Executor getInstance() {
    return INSTANCE;
  }
}
