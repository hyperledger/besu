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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;

/** A transaction receipt representation used for syncing. */
public class SyncTransactionReceipt {

  private final Bytes rlpBytes;

  private Bytes transactionTypeCode = null;
  private Bytes statusOrStateRoot = null;
  private Bytes cumulativeGasUsed = null;
  private Bytes bloomFilter = null;
  // each log consists of multiple 32 byte strings. Outer list contains full logs. Inner list
  // contains the 32 byte string components of the log
  private List<List<Bytes>> logs = new ArrayList<>();

  /**
   * Creates an instance of a receipt that just contains the rlp bytes.
   *
   * @param rlpBytes bytes of the RLP-encoded transaction receipt
   */
  public SyncTransactionReceipt(final Bytes rlpBytes) {
    this.rlpBytes = rlpBytes;
    parseComponents(RLP.input(rlpBytes));
  }

  public void parseComponents(final RLPInput rlpInput) {
    // The first byte indicates whether the receipt is typed (eth/68) or flat (eth/69).
    if (rlpInput.isDone()) {
      return;
    }
    if (!rlpInput.nextIsList()) {
      decodeTypedReceipt(rlpInput);
    } else {
      decodeFlatReceipt(rlpInput);
    }
  }

  private void decodeTypedReceipt(final RLPInput rlpInput) {
    RLPInput input = rlpInput;
    transactionTypeCode = input.readBytes();
    input.enterList();
    statusOrStateRoot = input.readBytes();
    cumulativeGasUsed = input.readBytes();
    final boolean isCompacted = isNextNotBloomFilter(input);
    if (!isCompacted) {
      bloomFilter = input.readBytes();
    }
    parseLogs(input);
    // if the receipt is compacted, we need to build the bloom filter from the logs
    if (isCompacted) {
      bloomFilter = LogsBloomFilter.builder().insertSyncLogs(logs).build();
    }
    input.leaveList();
  }

  private void decodeFlatReceipt(final RLPInput rlpInput) {
    rlpInput.enterList();
    // Flat receipts can be either legacy or eth/69 receipts.
    // To determine the type, we need to examine the logs' position, as the bloom filter cannot be
    // used. This is because compacted legacy receipts also lack a bloom filter.
    // The first element can be either the transaction type (eth/69 or stateRootOrStatus (eth/68
    final Bytes firstElement = rlpInput.readBytes();
    // The second element can be either the state root or status (eth/68) or cumulative gas (eth/69)
    final Bytes secondElement = rlpInput.readBytes();
    final boolean isCompacted = isNextNotBloomFilter(rlpInput);
    if (!isCompacted) {
      bloomFilter = rlpInput.readBytes();
    }
    boolean isEth69Receipt = isCompacted && !rlpInput.nextIsList();
    if (isEth69Receipt) {
      decodeEth69Receipt(rlpInput, firstElement, secondElement);
    } else {
      decodeLegacyReceipt(rlpInput, firstElement, secondElement);
    }
    rlpInput.leaveList();
  }

  private void decodeEth69Receipt(
      final RLPInput input, final Bytes transactionByteRlp, final Bytes statusOrStateRoot) {
    transactionTypeCode = transactionByteRlp;
    this.statusOrStateRoot = statusOrStateRoot;
    cumulativeGasUsed = input.readBytes();
    parseLogs(input);
    bloomFilter = LogsBloomFilter.builder().insertSyncLogs(logs).build();
  }

  private void parseLogs(final RLPInput input) {
    logs =
        input.readList(
            logInput -> {
              logInput.enterList();

              final Bytes logger = logInput.readBytes();

              final List<Bytes> topics = logInput.readList(RLPInput::readBytes32);
              final Bytes data = logInput.readBytes();

              logInput.leaveList();
              List<Bytes> result = new ArrayList<>(topics.size() + 2);
              result.add(logger);
              result.addAll(topics);
              result.add(data);
              return result;
            });
  }

  private void decodeLegacyReceipt(
      final RLPInput input, final Bytes statusOrStateRoot, final Bytes cumulativeGas) {
    transactionTypeCode = Bytes.of(TransactionType.FRONTIER.getSerializedType());
    this.statusOrStateRoot = statusOrStateRoot;
    this.cumulativeGasUsed = cumulativeGas;
    parseLogs(input);
    if (bloomFilter == null) {
      bloomFilter = LogsBloomFilter.builder().insertSyncLogs(logs).build();
    }
  }

  private boolean isNextNotBloomFilter(final RLPInput input) {
    return input.nextIsList() || input.nextSize() != LogsBloomFilter.BYTE_SIZE;
  }

  /**
   * Creates a transaction receipt for the given RLP
   *
   * @param rlpInput the RLP-encoded transaction receipt
   * @return the transaction receipt
   */
  public static SyncTransactionReceipt readFrom(final RLPInput rlpInput) {
    Bytes receiptBytes;
    if (rlpInput.nextIsList()) {
      receiptBytes = rlpInput.currentListAsBytesNoCopy(true);
    } else {
      receiptBytes = rlpInput.readBytes();
    }
    return new SyncTransactionReceipt(receiptBytes);
  }

  /**
   * Returns the state root for a state root-encoded transaction receipt
   *
   * @return the state root if the transaction receipt is state root-encoded; otherwise {@code null}
   */
  public Bytes getRlp() {
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

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof SyncTransactionReceipt)) {
      return false;
    }
    final SyncTransactionReceipt other = (SyncTransactionReceipt) obj;
    return rlpBytes.equals(other.rlpBytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rlpBytes);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("rlpBytes", rlpBytes).toString();
  }
}
