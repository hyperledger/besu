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

    // State read/write counters for cross-client execution metrics
    private int accountReads;
    private int storageReads;
    private int codeReads;
    private int codeBytesRead;
    private int accountWrites;
    private int storageWrites;
    private int codeWrites;
    private int codeBytesWritten;

    // EIP-7702 delegation counters
    private int eip7702DelegationsSet;
    private int eip7702DelegationsCleared;

    void reset() {
      sloadCount = 0;
      sstoreCount = 0;
      callCount = 0;
      createCount = 0;
      accountReads = 0;
      storageReads = 0;
      codeReads = 0;
      codeBytesRead = 0;
      accountWrites = 0;
      storageWrites = 0;
      codeWrites = 0;
      codeBytesWritten = 0;
      eip7702DelegationsSet = 0;
      eip7702DelegationsCleared = 0;
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

  // State read/write methods for cross-client execution metrics

  /** Increment the account reads counter. */
  public static void incrementAccountReads() {
    COUNTERS.get().accountReads++;
  }

  /** Increment the storage reads counter. */
  public static void incrementStorageReads() {
    COUNTERS.get().storageReads++;
  }

  /** Increment the code reads counter. */
  public static void incrementCodeReads() {
    COUNTERS.get().codeReads++;
  }

  /**
   * Add to the code bytes read counter.
   *
   * @param bytes the number of bytes read
   */
  public static void addCodeBytesRead(final int bytes) {
    COUNTERS.get().codeBytesRead += bytes;
  }

  /** Increment the account writes counter. */
  public static void incrementAccountWrites() {
    COUNTERS.get().accountWrites++;
  }

  /** Increment the storage writes counter. */
  public static void incrementStorageWrites() {
    COUNTERS.get().storageWrites++;
  }

  /** Increment the code writes counter. */
  public static void incrementCodeWrites() {
    COUNTERS.get().codeWrites++;
  }

  /**
   * Add to the code bytes written counter.
   *
   * @param bytes the number of bytes written
   */
  public static void addCodeBytesWritten(final int bytes) {
    COUNTERS.get().codeBytesWritten += bytes;
  }

  /**
   * Get the current account reads count.
   *
   * @return the account reads count
   */
  public static int getAccountReads() {
    return COUNTERS.get().accountReads;
  }

  /**
   * Get the current storage reads count.
   *
   * @return the storage reads count
   */
  public static int getStorageReads() {
    return COUNTERS.get().storageReads;
  }

  /**
   * Get the current code reads count.
   *
   * @return the code reads count
   */
  public static int getCodeReads() {
    return COUNTERS.get().codeReads;
  }

  /**
   * Get the current code bytes read count.
   *
   * @return the code bytes read count
   */
  public static int getCodeBytesRead() {
    return COUNTERS.get().codeBytesRead;
  }

  /**
   * Get the current account writes count.
   *
   * @return the account writes count
   */
  public static int getAccountWrites() {
    return COUNTERS.get().accountWrites;
  }

  /**
   * Get the current storage writes count.
   *
   * @return the storage writes count
   */
  public static int getStorageWrites() {
    return COUNTERS.get().storageWrites;
  }

  /**
   * Get the current code writes count.
   *
   * @return the code writes count
   */
  public static int getCodeWrites() {
    return COUNTERS.get().codeWrites;
  }

  /**
   * Get the current code bytes written count.
   *
   * @return the code bytes written count
   */
  public static int getCodeBytesWritten() {
    return COUNTERS.get().codeBytesWritten;
  }

  // EIP-7702 delegation tracking methods

  /** Increment the EIP-7702 delegations set counter. */
  public static void incrementEip7702DelegationsSet() {
    COUNTERS.get().eip7702DelegationsSet++;
  }

  /** Increment the EIP-7702 delegations cleared counter. */
  public static void incrementEip7702DelegationsCleared() {
    COUNTERS.get().eip7702DelegationsCleared++;
  }

  /**
   * Get the current EIP-7702 delegations set count.
   *
   * @return the EIP-7702 delegations set count
   */
  public static int getEip7702DelegationsSet() {
    return COUNTERS.get().eip7702DelegationsSet;
  }

  /**
   * Get the current EIP-7702 delegations cleared count.
   *
   * @return the EIP-7702 delegations cleared count
   */
  public static int getEip7702DelegationsCleared() {
    return COUNTERS.get().eip7702DelegationsCleared;
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
