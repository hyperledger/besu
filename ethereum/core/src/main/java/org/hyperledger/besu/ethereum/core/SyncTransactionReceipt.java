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
package org.hyperledger.besu.ethereum.core;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public class SyncTransactionReceipt {

  private final Bytes rlpBytes;
  private Bytes transactionTypeCode;
  private Bytes statusOrStateRoot;
  private Bytes cumulativeGasUsed;
  private Bytes bloomFilter;
  private List<List<Bytes>> logs;

  public SyncTransactionReceipt(final Bytes rlpBytes) {
    this.rlpBytes = rlpBytes;
  }

  public SyncTransactionReceipt(
      final Bytes rlpBytes,
      final Bytes transactionTypeCode,
      final Bytes statusOrStateRoot,
      final Bytes cumulativeGasUsed,
      final Bytes bloomFilter,
      final List<List<Bytes>> logs) {
    this.rlpBytes = rlpBytes;
    this.transactionTypeCode = transactionTypeCode;
    this.statusOrStateRoot = statusOrStateRoot;
    this.cumulativeGasUsed = cumulativeGasUsed;
    this.bloomFilter = bloomFilter;
    this.logs = logs;
  }

  public Bytes getRlpBytes() {
    return rlpBytes;
  }

  public Bytes getTransactionTypeCode() {
    return transactionTypeCode;
  }

  public Bytes getStatusOrStateRoot() {
    return statusOrStateRoot;
  }

  public Bytes getCumulativeGasUsed() {
    return cumulativeGasUsed;
  }

  public Bytes getBloomFilter() {
    return bloomFilter;
  }

  public List<List<Bytes>> getLogs() {
    return logs;
  }

  /**
   * Clears all variables except rlpBytes to allow garbage collection immediately instead of after
   * the receipt has been fully processed
   */
  public void clearSubVariables() {
    transactionTypeCode = null;
    statusOrStateRoot = null;
    cumulativeGasUsed = null;
    bloomFilter = null;
    logs = null;
  }
}
