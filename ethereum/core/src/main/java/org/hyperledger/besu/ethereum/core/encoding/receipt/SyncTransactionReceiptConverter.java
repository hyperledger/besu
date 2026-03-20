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
package org.hyperledger.besu.ethereum.core.encoding.receipt;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.datatypes.LogTopic;
import org.hyperledger.besu.datatypes.LogsBloomFilter;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Converts a {@link SyncTransactionReceipt} (holding raw wire-format bytes) into a canonical {@link
 * TransactionReceipt} that can be stored and later decoded by {@link TransactionReceiptDecoder}.
 *
 * <p>This is necessary because {@code putSyncTransactionReceipts} previously stored raw wire bytes
 * directly, while {@code rlpDecodeTransactionReceipts} uses {@link TransactionReceiptDecoder} which
 * expects the storage format produced by {@link TransactionReceiptEncoder}. Compact receipts
 * (eth/69/70, no bloom filter) encode log topics as raw bytes32 on the wire but the storage decoder
 * expects trimmed [leadingZeros, shortData] lists, causing an RLPException.
 */
public class SyncTransactionReceiptConverter {

  private static final SyncTransactionReceiptDecoder DECODER = new SyncTransactionReceiptDecoder();

  private SyncTransactionReceiptConverter() {}

  /**
   * Converts a {@link SyncTransactionReceipt} to a {@link TransactionReceipt}.
   *
   * <p>For non-compact receipts that include a bloom filter ({@code
   * isFormattedForRootCalculation=true}), the raw bytes are adapted to the storage format expected
   * by {@link TransactionReceiptDecoder}: legacy receipts are passed directly (they start with an
   * RLP list prefix), while typed receipts are wrapped in an RLP byte string (the wire format is
   * {@code type_byte || rlp_list}, but the storage decoder expects it wrapped as a byte string).
   *
   * <p>For compact receipts (no bloom filter), the raw bytes are re-decoded via {@link
   * SyncTransactionReceiptDecoder} to recover the structured fields, then converted to a {@link
   * TransactionReceipt}.
   *
   * @param receipt the sync receipt to convert
   * @return a canonical {@link TransactionReceipt} suitable for storage
   */
  public static TransactionReceipt toTransactionReceipt(final SyncTransactionReceipt receipt) {
    if (receipt.isFormattedForRootCalculation()) {
      // Non-compact receipt includes a bloom filter — TransactionReceiptDecoder can read it.
      return TransactionReceiptDecoder.readFrom(storageFormatRlpInput(receipt.getRlpBytes()), true);
    }

    // Compact receipt (no bloom filter). Sub-variables may have been cleared via
    // clearSubVariables(); re-decode from raw bytes to recover them.
    SyncTransactionReceipt decoded =
        receipt.getLogs() != null ? receipt : DECODER.decode(receipt.getRlpBytes());

    if (decoded.isFormattedForRootCalculation()) {
      // Re-decoded result still has a bloom filter; use TransactionReceiptDecoder.
      return TransactionReceiptDecoder.readFrom(storageFormatRlpInput(decoded.getRlpBytes()), true);
    }

    return convertFromSubVariables(decoded);
  }

  /**
   * Returns an {@link RLPInput} suitable for {@link TransactionReceiptDecoder#readFrom}.
   *
   * <p>There are three cases for {@code rawRlp}:
   *
   * <ul>
   *   <li>Legacy receipt: starts with a list prefix ({@code >= 0xC0}). Passed directly.
   *   <li>Storage-format typed receipt: starts with a byte-string prefix ({@code 0x80-0xBF}), i.e.
   *       an RLP byte string whose content is {@code type || rlp_list}. Passed directly — {@link
   *       TransactionReceiptDecoder} calls {@code readBytes()} which unwraps it correctly.
   *   <li>Wire-format typed receipt: starts with the raw type byte ({@code 0x01-0x7F}), which
   *       appears as a SHORT_ELEMENT in RLP. {@link TransactionReceiptDecoder} expects a byte
   *       string, so we wrap the raw bytes in one first.
   * </ul>
   */
  private static RLPInput storageFormatRlpInput(final Bytes rawRlp) {
    if ((rawRlp.get(0) & 0xFF) < 0x80) {
      // Wire-format typed receipt: first byte is the raw type byte (0x01-0x7F).
      // Wrap as a byte string so TransactionReceiptDecoder's readBytes() gets type || rlp_list.
      return RLP.input(RLP.encode(out -> out.writeBytes(rawRlp)));
    }
    return RLP.input(rawRlp);
  }

  private static TransactionReceipt convertFromSubVariables(final SyncTransactionReceipt decoded) {
    final TransactionType transactionType =
        TransactionType.fromEthSerializedType(decoded.getTransactionTypeCode().get(0))
            .orElse(TransactionType.FRONTIER);

    final long cumulativeGasUsed = parseLong(decoded.getCumulativeGasUsed());
    final List<Log> logs = convertLogs(decoded.getLogs());
    final LogsBloomFilter bloomFilter = decoded.getBloomFilter();
    final Bytes statusOrStateRoot = decoded.getStatusOrStateRoot();

    if (statusOrStateRoot.size() == 32) {
      return new TransactionReceipt(
          transactionType,
          Hash.wrap(Bytes32.wrap(statusOrStateRoot)),
          cumulativeGasUsed,
          logs,
          bloomFilter,
          Optional.empty());
    } else {
      final int status = statusOrStateRoot.isEmpty() ? 0 : statusOrStateRoot.get(0) & 0xFF;
      return new TransactionReceipt(
          transactionType, status, cumulativeGasUsed, logs, bloomFilter, Optional.empty());
    }
  }

  /** Interprets the bytes as a big-endian unsigned long (minimal encoding, no leading zeros). */
  private static long parseLong(final Bytes bytes) {
    long result = 0;
    for (int i = 0; i < bytes.size(); i++) {
      result = (result << 8) | (bytes.get(i) & 0xFF);
    }
    return result;
  }

  /**
   * Converts raw log entries from {@link SyncTransactionReceipt#getLogs()} to {@link Log} objects.
   * Each entry is [address, topic0, ..., topicN, data].
   */
  private static List<Log> convertLogs(final List<List<Bytes>> rawLogs) {
    return rawLogs.stream()
        .map(
            rawLog -> {
              final Address logger = Address.wrap(rawLog.get(0));
              final Bytes data = rawLog.get(rawLog.size() - 1);
              final List<LogTopic> topics =
                  rawLog.subList(1, rawLog.size() - 1).stream()
                      .map(t -> LogTopic.wrap(Bytes32.wrap(t)))
                      .toList();
              return new Log(logger, data, topics);
            })
        .toList();
  }
}
