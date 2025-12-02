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

/**
 * Decodes a transaction receipt from RLP.
 *
 * <pre>
 * (eth/68): receipt = {legacy-receipt, typed-receipt} with typed-receipt = tx-type || rlp(legacy-receipt)
 *
 * legacy-receipt = [
 *   post-state-or-status: {B_32, {0, 1}},
 *   cumulative-gas: P,
 *   bloom: B_256,
 *   logs: [log₁, log₂, ...]
 * ]
 *
 * (eth/69): receipt = [tx-type, post-state-or-status, cumulative-gas, logs]
 * </pre>
 */
public class TransactionReceiptDecoder {

  /**
   * Creates a transaction receipt for the given RLP
   *
   * @param rlpInput the RLP-encoded transaction receipt
   * @param revertReasonAllowed whether the rlp input is allowed to have a revert reason
   * @return the transaction receipt
   */
  public static TransactionReceipt readFrom(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    // The first byte indicates whether the receipt is typed (eth/68) or flat (eth/69).
    if (!rlpInput.nextIsList()) {
      return decodeTypedReceipt(rlpInput, revertReasonAllowed);
    } else {
      return decodeFlatReceipt(rlpInput, revertReasonAllowed);
    }
  }

  private static TransactionReceipt decodeTypedReceipt(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    RLPInput input = rlpInput;
    final Bytes typedTransactionReceiptBytes = input.readBytes();
    TransactionType transactionType =
        TransactionType.fromOpaque(typedTransactionReceiptBytes.get(0))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Invalid transaction type %x"
                            .formatted(typedTransactionReceiptBytes.get(0))));
    input = new BytesValueRLPInput(typedTransactionReceiptBytes.slice(1), false);
    input.enterList();
    final RLPInput statusOrStateRoot = input.readAsRlp();
    final long cumulativeGas = input.readLongScalar();
    LogsBloomFilter bloomFilter = null;
    final boolean isCompacted = isNextNotBloomFilter(input);
    if (!isCompacted) {
      bloomFilter = LogsBloomFilter.readFrom(input);
    }
    final List<Log> logs = input.readList(logInput -> Log.readFrom(logInput, isCompacted));
    // if the receipt is compacted, we need to build the bloom filter from the logs
    if (isCompacted) {
      bloomFilter = LogsBloomFilter.builder().insertLogs(logs).build();
    }
    Optional<Bytes> revertReason = readMaybeRevertReason(input, revertReasonAllowed);
    input.leaveList();
    return createReceipt(
        transactionType, statusOrStateRoot, cumulativeGas, logs, bloomFilter, revertReason);
  }

  private static TransactionReceipt decodeFlatReceipt(
      final RLPInput rlpInput, final boolean revertReasonAllowed) {
    rlpInput.enterList();
    // Flat receipts can be either legacy or eth/69 receipts.
    // To determine the type, we need to examine the logs' position, as the bloom filter cannot be
    // used. This is because compacted legacy receipts also lack a bloom filter.
    // The first element can be either the transaction type (eth/69 or stateRootOrStatus (eth/68
    final RLPInput firstElement = rlpInput.readAsRlp();
    // The second element can be either the state root or status (eth/68) or cumulative gas (eth/69)
    final RLPInput secondElement = rlpInput.readAsRlp();
    final boolean isCompacted = isNextNotBloomFilter(rlpInput);
    LogsBloomFilter bloomFilter = null;
    if (!isCompacted) {
      bloomFilter = LogsBloomFilter.readFrom(rlpInput);
    }
    boolean isEth69Receipt = isCompacted && !rlpInput.nextIsList();
    TransactionReceipt receipt;
    if (isEth69Receipt) {
      receipt = decodeEth69Receipt(rlpInput, firstElement, secondElement);
    } else {
      receipt =
          decodeLegacyReceipt(
              rlpInput, firstElement, secondElement, bloomFilter, revertReasonAllowed);
    }
    rlpInput.leaveList();
    return receipt;
  }

  private static TransactionReceipt decodeEth69Receipt(
      final RLPInput input, final RLPInput transactionByteRlp, final RLPInput statusOrStateRoot) {
    final TransactionType transactionType;
    transactionType = getTransactionType(transactionByteRlp);
    final long cumulativeGas = input.readLongScalar();
    final List<Log> logs = input.readList(logInput -> Log.readFrom(logInput, false));
    final LogsBloomFilter bloomFilter = LogsBloomFilter.builder().insertLogs(logs).build();
    return createReceipt(
        transactionType, statusOrStateRoot, cumulativeGas, logs, bloomFilter, Optional.empty());
  }

  private static TransactionType getTransactionType(final RLPInput transactionByteRlp) {
    final TransactionType transactionType;
    Bytes transactionBytes = transactionByteRlp.readBytes();
    if (transactionBytes.isEmpty()) {
      transactionType = TransactionType.FRONTIER;
    } else if (transactionBytes.size() != 1) {
      throw new IllegalStateException("Invalid transaction type" + transactionBytes.toHexString());
    } else {
      final byte typeByte = transactionBytes.get(0);
      transactionType =
          TransactionType.fromEthSerializedType(typeByte)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Invalid transaction typeByte %x".formatted(typeByte)));
    }
    return transactionType;
  }

  private static TransactionReceipt decodeLegacyReceipt(
      final RLPInput input,
      final RLPInput statusOrStateRootRlpInput,
      final RLPInput cumulativeGasRlpInput,
      final LogsBloomFilter bloomFilter,
      final boolean revertReasonAllowed) {
    final long cumulativeGas = cumulativeGasRlpInput.readLongScalar();
    final boolean isCompacted = bloomFilter == null;
    final List<Log> logs = input.readList(logInput -> Log.readFrom(logInput, isCompacted));
    Optional<Bytes> revertReason = readMaybeRevertReason(input, revertReasonAllowed);
    return createReceipt(
        TransactionType.FRONTIER,
        statusOrStateRootRlpInput,
        cumulativeGas,
        logs,
        isCompacted ? LogsBloomFilter.builder().insertLogs(logs).build() : bloomFilter,
        revertReason);
  }

  private static TransactionReceipt createReceipt(
      final TransactionType transactionType,
      final RLPInput statusOrStateRoot,
      final long cumulativeGas,
      final List<Log> logs,
      final LogsBloomFilter bloomFilter,
      final Optional<Bytes> revertReason) {
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

  private static Optional<Bytes> readMaybeRevertReason(
      final RLPInput input, final boolean revertReasonAllowed) {
    final Optional<Bytes> revertReason;
    if (input.isEndOfCurrentList()) {
      revertReason = Optional.empty();
    } else {
      if (!revertReasonAllowed) {
        throw new RLPException("Unexpected value at end of TransactionReceipt");
      }
      revertReason = Optional.of(input.readBytes());
    }
    return revertReason;
  }

  private static boolean isNextNotBloomFilter(final RLPInput input) {
    return input.nextIsList() || input.nextSize() != LogsBloomFilter.BYTE_SIZE;
  }
}
