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
package org.hyperledger.besu.evm;

/**
 * Thread-local counters for EVM operations used for cross-client execution metrics.
 *
 * <p>This class provides a way to track EVM operation counts (SLOAD, SSTORE, CALL, CREATE) during
 * block execution without adding dependencies between modules. The counters are thread-local and
 * should be reset at the start of each block execution.
 */
public final class EvmOperationCounters {

  private static final ThreadLocal<Counters> COUNTERS = ThreadLocal.withInitial(Counters::new);

  private EvmOperationCounters() {}

  /** Container for operation counters. */
  private static final class Counters {
    private int sloadCount;
    private int sstoreCount;
    private int callCount;
    private int createCount;

    void reset() {
      sloadCount = 0;
      sstoreCount = 0;
      callCount = 0;
      createCount = 0;
    }
  }

  /** Increment the SLOAD counter. */
  public static void incrementSload() {
    COUNTERS.get().sloadCount++;
  }

  /** Increment the SSTORE counter. */
  public static void incrementSstore() {
    COUNTERS.get().sstoreCount++;
  }

  /** Increment the CALL counter (includes DELEGATECALL, STATICCALL, CALLCODE). */
  public static void incrementCall() {
    COUNTERS.get().callCount++;
  }

  /** Increment the CREATE counter (includes CREATE2). */
  public static void incrementCreate() {
    COUNTERS.get().createCount++;
  }

  /**
   * Get the current SLOAD count.
   *
   * @return the SLOAD count
   */
  public static int getSloadCount() {
    return COUNTERS.get().sloadCount;
  }

  /**
   * Get the current SSTORE count.
   *
   * @return the SSTORE count
   */
  public static int getSstoreCount() {
    return COUNTERS.get().sstoreCount;
  }

  /**
   * Get the current CALL count.
   *
   * @return the CALL count
   */
  public static int getCallCount() {
    return COUNTERS.get().callCount;
  }

  /**
   * Get the current CREATE count.
   *
   * @return the CREATE count
   */
  public static int getCreateCount() {
    return COUNTERS.get().createCount;
  }

  /** Reset all counters to zero. Should be called at the start of block execution. */
  public static void reset() {
    COUNTERS.get().reset();
  }

  /** Clear the thread-local counters. Should be called when block execution completes. */
  public static void clear() {
    COUNTERS.remove();
  }
}
