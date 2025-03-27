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
    return readFrom(input, TransactionReceiptDecodingOptions.DEFAULT);
  }

  /**
   * Creates a transaction receipt for the given RLP
   *
   * @param rlpInput the RLP-encoded transaction receipt
   * @param decodingOptions the decoding options
   * @return the transaction receipt
   */
  public static TransactionReceipt readFrom(
      final RLPInput rlpInput, final TransactionReceiptDecodingOptions decodingOptions) {
    RLPInput input = rlpInput;
    TransactionType transactionType = TransactionType.FRONTIER;
    boolean isTypedReceipt = false;
    if (!rlpInput.nextIsList()) {
      final Bytes typedTransactionReceiptBytes = input.readBytes();
      transactionType = TransactionType.of(typedTransactionReceiptBytes.get(0));
      input = new BytesValueRLPInput(typedTransactionReceiptBytes.slice(1), false);
      // if the first byte is transaction type, then it is not EIP-7642 receipt
      isTypedReceipt = true;
    }

    input.enterList();

    final RLPInput firstElement = input.readAsRlp();
    final RLPInput secondElement = input.readAsRlp();

    LogsBloomFilter bloomFilter = null;
    final boolean hasBloomFilter =
        !input.nextIsList() && input.nextSize() == LogsBloomFilter.BYTE_SIZE;
    if (hasBloomFilter) {
      // The logs below will populate the bloom filter upon construction.
      bloomFilter = LogsBloomFilter.readFrom(input);
    }

    final long cumulativeGas;
    final RLPInput statusOrStateRoot;
    boolean isEip7642Receipt = !hasBloomFilter && !input.nextIsList();
    if (isEip7642Receipt) {
      if (isTypedReceipt) {
        throw new RLPException("Receipt is EIP-7642 but has transaction type");
      }
      int transactionByte = firstElement.readIntScalar();
      transactionType =
          transactionByte == 0x00 ? TransactionType.FRONTIER : TransactionType.of(transactionByte);
      statusOrStateRoot = secondElement;
      cumulativeGas = input.readLongScalar();
    } else {
      statusOrStateRoot = firstElement;
      cumulativeGas = secondElement.readLongScalar();
    }

    // TODO consider validating that the logs and bloom filter match.
    final boolean compacted = !hasBloomFilter && !isEip7642Receipt;
    final List<Log> logs = input.readList(logInput -> Log.readFrom(logInput, compacted));
    if (compacted) {
      bloomFilter = LogsBloomFilter.builder().insertLogs(logs).build();
    }

    final Optional<Bytes> revertReason;
    if (input.isEndOfCurrentList()) {
      revertReason = Optional.empty();
    } else {
      if (!decodingOptions.isRevertReasonAllowed()) {
        throw new RLPException("Unexpected value at end of TransactionReceipt");
      }
      revertReason = Optional.of(input.readBytes());
    }

    input.leaveList();

    // Status code-encoded transaction receipts have a single byte for success (0x01) or failure
    // (0x80).
    if (statusOrStateRoot.raw().size() == 1) {
      final int status = statusOrStateRoot.readIntScalar();
      return new TransactionReceipt(
          transactionType, status, cumulativeGas, logs, bloomFilter, revertReason);
    } else {
      final Hash stateRoot = Hash.wrap(statusOrStateRoot.readBytes32());
      return new TransactionReceipt(
          transactionType, stateRoot, cumulativeGas, logs, bloomFilter, revertReason);
    }
  }
}
