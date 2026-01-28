/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EvmOperationCounters;

import java.util.HashSet;
import java.util.Set;

/**
 * Collects execution statistics during block processing for performance monitoring.
 *
 * <p>Tracks timing breakdowns, state access counts, and unique state access patterns following the
 * cross-client execution metrics specification.
 */
public class ExecutionStats {

  // Timing in nanoseconds
  private long executionStartNanos;
  private long executionTimeNanos;
  private long stateReadTimeNanos;
  private long stateHashTimeNanos;
  private long commitTimeNanos;

  // Gas metrics
  private long gasUsed;
  private int transactionCount;

  // State read counters
  private int accountReads;
  private int storageReads;
  private int codeReads;
  private long codeBytesRead;

  // State write counters
  private int accountWrites;
  private int storageWrites;
  private int codeWrites;
  private long codeBytesWritten;
  private int accountCreates;
  private int accountDestructs;

  // EIP-7702 delegation tracking
  private int eip7702DelegationsSet;
  private int eip7702DelegationsCleared;

  // EVM operation counters
  private int sloadCount;
  private int sstoreCount;
  private int callCount;
  private int createCount;

  // Cache statistics
  private long accountCacheHits;
  private long accountCacheMisses;
  private long storageCacheHits;
  private long storageCacheMisses;
  private long codeCacheHits;
  private long codeCacheMisses;

  // Unique tracking
  private final Set<Address> uniqueAccountsTouched = new HashSet<>();
  private final Set<StorageSlotKey> uniqueStorageSlots = new HashSet<>();
  private final Set<Address> uniqueContractsExecuted = new HashSet<>();

  /** Creates a new ExecutionStats instance. */
  public ExecutionStats() {
    reset();
  }

  /** Resets all statistics to zero. */
  public void reset() {
    executionStartNanos = 0;
    executionTimeNanos = 0;
    stateReadTimeNanos = 0;
    stateHashTimeNanos = 0;
    commitTimeNanos = 0;
    gasUsed = 0;
    transactionCount = 0;
    accountReads = 0;
    storageReads = 0;
    codeReads = 0;
    codeBytesRead = 0;
    accountWrites = 0;
    storageWrites = 0;
    codeWrites = 0;
    codeBytesWritten = 0;
    accountCreates = 0;
    accountDestructs = 0;
    eip7702DelegationsSet = 0;
    eip7702DelegationsCleared = 0;
    sloadCount = 0;
    sstoreCount = 0;
    callCount = 0;
    createCount = 0;
    accountCacheHits = 0;
    accountCacheMisses = 0;
    storageCacheHits = 0;
    storageCacheMisses = 0;
    codeCacheHits = 0;
    codeCacheMisses = 0;
    uniqueAccountsTouched.clear();
    uniqueStorageSlots.clear();
    uniqueContractsExecuted.clear();
  }

  // Timing methods

  /** Marks the start of execution timing. */
  public void startExecution() {
    executionStartNanos = System.nanoTime();
  }

  /** Marks the end of execution timing. */
  public void endExecution() {
    executionTimeNanos = System.nanoTime() - executionStartNanos;
  }

  /**
   * Adds state read time.
   *
   * @param nanos the state read time in nanoseconds
   */
  public void addStateReadTime(final long nanos) {
    stateReadTimeNanos += nanos;
  }

  /**
   * Adds state hash time (Merkle trie rehashing).
   *
   * @param nanos the state hash time in nanoseconds
   */
  public void addStateHashTime(final long nanos) {
    stateHashTimeNanos += nanos;
  }

  /**
   * Adds commit time.
   *
   * @param nanos the commit time in nanoseconds
   */
  public void addCommitTime(final long nanos) {
    commitTimeNanos += nanos;
  }

  // Gas methods

  /**
   * Adds gas used.
   *
   * @param gas the gas used
   */
  public void addGasUsed(final long gas) {
    gasUsed += gas;
  }

  /** Increments transaction count. */
  public void incrementTransactionCount() {
    transactionCount++;
  }

  // State read methods

  /** Increments account read counter. */
  public void incrementAccountReads() {
    accountReads++;
  }

  /** Increments storage read counter. */
  public void incrementStorageReads() {
    storageReads++;
  }

  /** Increments code read counter. */
  public void incrementCodeReads() {
    codeReads++;
  }

  /**
   * Adds bytes read for code.
   *
   * @param bytes the number of bytes read
   */
  public void addCodeBytesRead(final long bytes) {
    codeBytesRead += bytes;
  }

  // State write methods

  /** Increments account write counter. */
  public void incrementAccountWrites() {
    accountWrites++;
  }

  /** Increments storage write counter. */
  public void incrementStorageWrites() {
    storageWrites++;
  }

  /** Increments code write counter. */
  public void incrementCodeWrites() {
    codeWrites++;
  }

  /**
   * Adds bytes written for code.
   *
   * @param bytes the number of bytes written
   */
  public void addCodeBytesWritten(final long bytes) {
    codeBytesWritten += bytes;
  }

  /** Increments account create counter. */
  public void incrementAccountCreates() {
    accountCreates++;
  }

  /** Increments account destruct counter. */
  public void incrementAccountDestructs() {
    accountDestructs++;
  }

  // EIP-7702 delegation tracking methods

  /** Increments EIP-7702 delegations set counter. */
  public void incrementEip7702DelegationsSet() {
    eip7702DelegationsSet++;
  }

  /** Increments EIP-7702 delegations cleared counter. */
  public void incrementEip7702DelegationsCleared() {
    eip7702DelegationsCleared++;
  }

  // EVM operation methods

  /** Increments SLOAD counter. */
  public void incrementSloadCount() {
    sloadCount++;
  }

  /** Increments SSTORE counter. */
  public void incrementSstoreCount() {
    sstoreCount++;
  }

  /** Increments CALL counter. */
  public void incrementCallCount() {
    callCount++;
  }

  /** Increments CREATE counter. */
  public void incrementCreateCount() {
    createCount++;
  }

  /**
   * Collects EVM operation counters from the thread-local EvmOperationCounters. This should be
   * called at the end of block execution to aggregate the counters from the EVM module.
   */
  public void collectEvmCounters() {
    this.sloadCount = EvmOperationCounters.getSloadCount();
    this.sstoreCount = EvmOperationCounters.getSstoreCount();
    this.callCount = EvmOperationCounters.getCallCount();
    this.createCount = EvmOperationCounters.getCreateCount();
  }

  // Cache statistics methods

  /**
   * Sets cache statistics for all cache types.
   *
   * @param accountHits account cache hits
   * @param accountMisses account cache misses
   * @param storageHits storage cache hits
   * @param storageMisses storage cache misses
   * @param codeHits code cache hits
   * @param codeMisses code cache misses
   */
  public void setCacheStats(
      final long accountHits,
      final long accountMisses,
      final long storageHits,
      final long storageMisses,
      final long codeHits,
      final long codeMisses) {
    this.accountCacheHits = accountHits;
    this.accountCacheMisses = accountMisses;
    this.storageCacheHits = storageHits;
    this.storageCacheMisses = storageMisses;
    this.codeCacheHits = codeHits;
    this.codeCacheMisses = codeMisses;
  }

  /** Increments account cache hit counter. */
  public void incrementAccountCacheHits() {
    accountCacheHits++;
  }

  /** Increments account cache miss counter. */
  public void incrementAccountCacheMisses() {
    accountCacheMisses++;
  }

  /** Increments storage cache hit counter. */
  public void incrementStorageCacheHits() {
    storageCacheHits++;
  }

  /** Increments storage cache miss counter. */
  public void incrementStorageCacheMisses() {
    storageCacheMisses++;
  }

  /** Increments code cache hit counter. */
  public void incrementCodeCacheHits() {
    codeCacheHits++;
  }

  /** Increments code cache miss counter. */
  public void incrementCodeCacheMisses() {
    codeCacheMisses++;
  }

  /**
   * Calculates hit rate percentage for a cache.
   *
   * @param hits the number of hits
   * @param misses the number of misses
   * @return the hit rate as a percentage (0-100)
   */
  private static double calculateHitRate(final long hits, final long misses) {
    long total = hits + misses;
    if (total > 0) {
      return (hits * 100.0) / total;
    }
    return 0.0;
  }

  /**
   * Gets account cache hits.
   *
   * @return the account cache hit count
   */
  public long getAccountCacheHits() {
    return accountCacheHits;
  }

  /**
   * Gets account cache misses.
   *
   * @return the account cache miss count
   */
  public long getAccountCacheMisses() {
    return accountCacheMisses;
  }

  /**
   * Gets storage cache hits.
   *
   * @return the storage cache hit count
   */
  public long getStorageCacheHits() {
    return storageCacheHits;
  }

  /**
   * Gets storage cache misses.
   *
   * @return the storage cache miss count
   */
  public long getStorageCacheMisses() {
    return storageCacheMisses;
  }

  /**
   * Gets code cache hits.
   *
   * @return the code cache hit count
   */
  public long getCodeCacheHits() {
    return codeCacheHits;
  }

  /**
   * Gets code cache misses.
   *
   * @return the code cache miss count
   */
  public long getCodeCacheMisses() {
    return codeCacheMisses;
  }

  // Unique tracking methods

  /**
   * Records an account touch.
   *
   * @param address the address touched
   */
  public void recordAccountTouched(final Address address) {
    uniqueAccountsTouched.add(address);
  }

  /**
   * Records a storage slot access.
   *
   * @param address the contract address
   * @param slot the storage slot key
   */
  public void recordStorageSlotAccessed(
      final Address address, final org.apache.tuweni.units.bigints.UInt256 slot) {
    uniqueStorageSlots.add(new StorageSlotKey(address, slot));
  }

  /**
   * Records a contract execution.
   *
   * @param address the contract address
   */
  public void recordContractExecuted(final Address address) {
    uniqueContractsExecuted.add(address);
  }

  // Getters

  /**
   * Gets execution time in milliseconds with sub-millisecond precision.
   *
   * @return the execution time in ms as a double
   */
  public double getExecutionTimeMs() {
    return executionTimeNanos / 1_000_000.0;
  }

  /**
   * Gets execution time in nanoseconds.
   *
   * @return the execution time in nanos
   */
  public long getExecutionTimeNanos() {
    return executionTimeNanos;
  }

  /**
   * Gets state read time in milliseconds with sub-millisecond precision.
   *
   * @return the state read time in ms as a double
   */
  public double getStateReadTimeMs() {
    return stateReadTimeNanos / 1_000_000.0;
  }

  /**
   * Gets state hash time in milliseconds (Merkle trie rehashing) with sub-millisecond precision.
   *
   * @return the state hash time in ms as a double
   */
  public double getStateHashTimeMs() {
    return stateHashTimeNanos / 1_000_000.0;
  }

  /**
   * Gets commit time in milliseconds with sub-millisecond precision.
   *
   * @return the commit time in ms as a double
   */
  public double getCommitTimeMs() {
    return commitTimeNanos / 1_000_000.0;
  }

  /**
   * Gets total time in milliseconds with sub-millisecond precision.
   *
   * @return the total time in ms as a double
   */
  public double getTotalTimeMs() {
    return (executionTimeNanos + stateHashTimeNanos + commitTimeNanos) / 1_000_000.0;
  }

  /**
   * Gets gas used.
   *
   * @return the gas used
   */
  public long getGasUsed() {
    return gasUsed;
  }

  /**
   * Gets transaction count.
   *
   * @return the transaction count
   */
  public int getTransactionCount() {
    return transactionCount;
  }

  /**
   * Gets MGas per second throughput.
   *
   * @return the throughput in MGas/s
   */
  public double getMgasPerSecond() {
    if (executionTimeNanos == 0) {
      return 0.0;
    }
    return (gasUsed / 1_000_000.0) / (executionTimeNanos / 1_000_000_000.0);
  }

  /**
   * Gets account reads.
   *
   * @return the account read count
   */
  public int getAccountReads() {
    return accountReads;
  }

  /**
   * Gets storage reads.
   *
   * @return the storage read count
   */
  public int getStorageReads() {
    return storageReads;
  }

  /**
   * Gets code reads.
   *
   * @return the code read count
   */
  public int getCodeReads() {
    return codeReads;
  }

  /**
   * Gets code bytes read.
   *
   * @return the bytes read
   */
  public long getCodeBytesRead() {
    return codeBytesRead;
  }

  /**
   * Gets account writes.
   *
   * @return the account write count
   */
  public int getAccountWrites() {
    return accountWrites;
  }

  /**
   * Gets storage writes.
   *
   * @return the storage write count
   */
  public int getStorageWrites() {
    return storageWrites;
  }

  /**
   * Gets code writes.
   *
   * @return the code write count
   */
  public int getCodeWrites() {
    return codeWrites;
  }

  /**
   * Gets code bytes written.
   *
   * @return the code bytes written
   */
  public long getCodeBytesWritten() {
    return codeBytesWritten;
  }

  /**
   * Gets SLOAD count.
   *
   * @return the SLOAD count
   */
  public int getSloadCount() {
    return sloadCount;
  }

  /**
   * Gets SSTORE count.
   *
   * @return the SSTORE count
   */
  public int getSstoreCount() {
    return sstoreCount;
  }

  /**
   * Gets CALL count.
   *
   * @return the CALL count
   */
  public int getCallCount() {
    return callCount;
  }

  /**
   * Gets CREATE count.
   *
   * @return the CREATE count
   */
  public int getCreateCount() {
    return createCount;
  }

  /**
   * Gets account creates count.
   *
   * @return the account creates count
   */
  public int getAccountCreates() {
    return accountCreates;
  }

  /**
   * Gets account destructs count.
   *
   * @return the account destructs count
   */
  public int getAccountDestructs() {
    return accountDestructs;
  }

  /**
   * Gets EIP-7702 delegations set count.
   *
   * @return the EIP-7702 delegations set count
   */
  public int getEip7702DelegationsSet() {
    return eip7702DelegationsSet;
  }

  /**
   * Gets EIP-7702 delegations cleared count.
   *
   * @return the EIP-7702 delegations cleared count
   */
  public int getEip7702DelegationsCleared() {
    return eip7702DelegationsCleared;
  }

  /**
   * Gets unique accounts touched.
   *
   * @return the count
   */
  public int getUniqueAccountsTouched() {
    return uniqueAccountsTouched.size();
  }

  /**
   * Gets unique storage slots accessed.
   *
   * @return the count
   */
  public int getUniqueStorageSlots() {
    return uniqueStorageSlots.size();
  }

  /**
   * Gets unique contracts executed.
   *
   * @return the count
   */
  public int getUniqueContractsExecuted() {
    return uniqueContractsExecuted.size();
  }

  /**
   * Checks if the block is considered slow based on execution time.
   *
   * @param thresholdMs the threshold in milliseconds
   * @return true if execution time exceeds threshold
   */
  public boolean isSlowBlock(final long thresholdMs) {
    return getExecutionTimeMs() > thresholdMs;
  }

  /**
   * Generates a JSON representation for slow block logging following the cross-client execution
   * metrics specification.
   *
   * @param blockNumber the block number
   * @param blockHash the block hash
   * @return the JSON string
   */
  public String toSlowBlockJson(final long blockNumber, final String blockHash) {
    return String.format(
        """
        {"level":"warn","msg":"Slow block",\
        "block":{"number":%d,"hash":"%s","gas_used":%d,"tx_count":%d},\
        "timing":{"execution_ms":%.3f,"state_read_ms":%.3f,"state_hash_ms":%.3f,"commit_ms":%.3f,"total_ms":%.3f},\
        "throughput":{"mgas_per_sec":%.2f},\
        "state_reads":{"accounts":%d,"storage_slots":%d,"code":%d,"code_bytes":%d},\
        "state_writes":{"accounts":%d,"storage_slots":%d,"code":%d,"code_bytes":%d,"eip7702_delegations_set":%d,"eip7702_delegations_cleared":%d},\
        "cache":{"account":{"hits":%d,"misses":%d,"hit_rate":%.2f},\
        "storage":{"hits":%d,"misses":%d,"hit_rate":%.2f},\
        "code":{"hits":%d,"misses":%d,"hit_rate":%.2f}},\
        "unique":{"accounts":%d,"storage_slots":%d,"contracts":%d},\
        "evm":{"sload":%d,"sstore":%d,"calls":%d,"creates":%d}}""",
        blockNumber,
        blockHash,
        gasUsed,
        transactionCount,
        getExecutionTimeMs(),
        getStateReadTimeMs(),
        getStateHashTimeMs(),
        getCommitTimeMs(),
        getTotalTimeMs(),
        getMgasPerSecond(),
        accountReads,
        storageReads,
        codeReads,
        codeBytesRead,
        accountWrites,
        storageWrites,
        codeWrites,
        codeBytesWritten,
        eip7702DelegationsSet,
        eip7702DelegationsCleared,
        accountCacheHits,
        accountCacheMisses,
        calculateHitRate(accountCacheHits, accountCacheMisses),
        storageCacheHits,
        storageCacheMisses,
        calculateHitRate(storageCacheHits, storageCacheMisses),
        codeCacheHits,
        codeCacheMisses,
        calculateHitRate(codeCacheHits, codeCacheMisses),
        getUniqueAccountsTouched(),
        getUniqueStorageSlots(),
        getUniqueContractsExecuted(),
        sloadCount,
        sstoreCount,
        callCount,
        createCount);
  }

  /** Inner class to represent a unique storage slot key. */
  private record StorageSlotKey(Address address, org.apache.tuweni.units.bigints.UInt256 slot) {}
}
