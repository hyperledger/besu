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

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;
import java.util.Objects;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A transaction receipt representation used for syncing. */
public class SyncTransactionReceipt {
    private static final Logger LOG = LoggerFactory.getLogger(SyncTransactionReceipt.class);

  private final Bytes rlpBytes;
  private final List<Log> logs;

  /**
   * Creates an instance of a receipt that just contains the rlp bytes.
   *
   * @param rlpBytes bytes of the RLP-encoded transaction receipt
   */
  public SyncTransactionReceipt(final Bytes rlpBytes) {
    this.rlpBytes = rlpBytes;
    this.logs = parseForLogs(rlpBytes);
  }

  private List<Log> parseForLogs(final Bytes bytes) {
    RLPInput in = RLP.input(bytes);
    if (!in.nextIsList()) {
      return decodeTypedReceipt(in);
    } else {
      return decodeFlatReceipt(in);
    }
  }

  private List<Log> decodeTypedReceipt(final RLPInput rlpInput) {
    RLPInput input = rlpInput;
    final Bytes typedTransactionReceiptBytes = input.readBytes();
    LOG.info("Attempting to decodeTypedReceipt, read {} from full input {}", typedTransactionReceiptBytes.toHexString(), rlpBytes.toHexString());
    input = new BytesValueRLPInput(typedTransactionReceiptBytes.slice(1), false);
    input.enterList();
    // statusOrStateRoot
    input.readAsRlp();
    // cumulativeGas
    input.readLongScalar();
    final boolean isCompacted = isNextNotBloomFilter(input);
    if (!isCompacted) {
      //    bloomFilter
      Bytes rawBloomFilter = input.readBytes();
      assert rawBloomFilter.size() == LogsBloomFilter.BYTE_SIZE;
    }
    return input.readList(logInput -> Log.readFrom(logInput, isCompacted));
  }

  private List<Log> decodeFlatReceipt(final RLPInput rlpInput) {
    rlpInput.enterList();
    // Flat receipts can be either legacy or eth/69 receipts.
    // To determine the type, we need to examine the logs' position, as the bloom filter cannot be
    // used. This is because compacted legacy receipts also lack a bloom filter.
    // The first element can be either the transaction type (eth/69 or stateRootOrStatus (eth/68
    rlpInput.readAsRlp();
    // The second element can be either the state root or status (eth/68) or cumulative gas (eth/69)
    rlpInput.readAsRlp();
    final boolean isCompacted = isNextNotBloomFilter(rlpInput);
    Bytes rawBloomFilter = null;
    if (!isCompacted) {
      //    bloomFilter
      rawBloomFilter = rlpInput.readBytes();
      assert rawBloomFilter.size() == LogsBloomFilter.BYTE_SIZE;
    }
    boolean isEth69Receipt = isCompacted && !rlpInput.nextIsList();
    List<Log> logs;
    if (isEth69Receipt) {
      logs = decodeEth69Receipt(rlpInput);
    } else {
      logs = decodeLegacyReceipt(rlpInput, rawBloomFilter == null);
    }
    rlpInput.leaveList();
    return logs;
  }

  private List<Log> decodeEth69Receipt(final RLPInput input) {
    // cumulativeGas
    input.readLongScalar();
    return input.readList(logInput -> Log.readFrom(logInput, false));
  }

  private List<Log> decodeLegacyReceipt(final RLPInput input, final boolean isCompacted) {
    return input.readList(logInput -> Log.readFrom(logInput, isCompacted));
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
    SyncTransactionReceipt ret;
    if (rlpInput.nextIsList()) {
      ret = new SyncTransactionReceipt(rlpInput.currentListAsBytesNoCopy(true));
    } else {
      ret = new SyncTransactionReceipt(rlpInput.readBytes());
    }
    return ret;
  }

  /**
   * Returns the state root for a state root-encoded transaction receipt
   *
   * @return the state root if the transaction receipt is state root-encoded; otherwise {@code null}
   */
  public Bytes getRlp() {
    return rlpBytes;
  }

  public List<Log> getLogsList() {
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
