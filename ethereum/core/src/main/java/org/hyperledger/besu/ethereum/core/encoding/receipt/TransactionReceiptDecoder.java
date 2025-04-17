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
package org.hyperledger.besu.ethereum.core.encoding.receipt;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class TransactionReceiptDecoder {

  /**
   * Creates a transaction receipt for the given RLP
   *
   * @param input the RLP-encoded transaction receipt
   * @return the transaction receipt
   */
  public static TransactionReceipt readFrom(final RLPInput input) {
    return readFrom(input, true);
  }

  /**
   * Creates a transaction receipt for the given RLP
   *
   * @param rlpInput the RLP-encoded transaction receipt
   * @param revertReasonAllowed whether the rlp input is allowed to have a revert reason
   * @return the transaction receipt
   */
  public static TransactionReceipt readFrom(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    RLPInput input = rlpInput;
    TransactionType transactionType = TransactionType.FRONTIER;
    if (!rlpInput.nextIsList()) {
      final Bytes typedTransactionReceiptBytes = input.readBytes();
      transactionType = TransactionType.of(typedTransactionReceiptBytes.get(0));
      input = new BytesValueRLPInput(typedTransactionReceiptBytes.slice(1), false);
    }

    input.enterList();
    // Get the first element to check later to determine the
    // correct transaction receipt encoding to use.
    final RLPInput firstElement = input.readAsRlp();
    final long cumulativeGas = input.readLongScalar();

    LogsBloomFilter bloomFilter = null;

    final boolean hasLogs = !input.nextIsList() && input.nextSize() == LogsBloomFilter.BYTE_SIZE;
    if (hasLogs) {
      // The logs below will populate the bloom filter upon construction.
      bloomFilter = LogsBloomFilter.readFrom(input);
    }
    // TODO consider validating that the logs and bloom filter match.
    final boolean compacted = !hasLogs;
    final List<Log> logs = input.readList(logInput -> Log.readFrom(logInput, compacted));
    if (compacted) {
      bloomFilter = LogsBloomFilter.builder().insertLogs(logs).build();
    }

    final Optional<Bytes> revertReason;
    if (input.isEndOfCurrentList()) {
      revertReason = Optional.empty();
    } else {
      if (!revertReasonAllowed) {
        throw new RLPException("Unexpected value at end of TransactionReceipt");
      }
      revertReason = Optional.of(input.readBytes());
    }

    // Status code-encoded transaction receipts have a single
    // byte for success (0x01) or failure (0x80).
    if (firstElement.raw().size() == 1) {
      final int status = firstElement.readIntScalar();
      input.leaveList();
      return new TransactionReceipt(
          transactionType, status, cumulativeGas, logs, bloomFilter, revertReason);
    } else {
      final Hash stateRoot = Hash.wrap(firstElement.readBytes32());
      input.leaveList();
      return new TransactionReceipt(
          transactionType, stateRoot, cumulativeGas, logs, bloomFilter, revertReason);
    }
  }
}
