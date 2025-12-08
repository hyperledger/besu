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

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public class TransactionReceiptEncoder {
  public Bytes encode(
      final List<TransactionReceipt> transactionReceipts,
      final TransactionReceiptEncodingConfiguration options) {
    return RLP.encode(
        (rlpOutput) -> {
          if (transactionReceipts.isEmpty()) {
            rlpOutput.writeEmptyList();
          } else {
            transactionReceipts.forEach((tr) -> writeTo(tr, rlpOutput, options));
          }
        });
  }

  public static void writeTo(
      final TransactionReceipt receipt,
      final RLPOutput rlpOutput,
      final TransactionReceiptEncodingConfiguration options) {

    // Check if the encoding options require Eth69 receipt format
    if (options.isWithEth69Receipt()) {
      writeEth69Receipt(receipt, rlpOutput, options);
      return;
    }

    if (shouldEncodeOpaqueBytes(receipt, options)) {
      rlpOutput.writeBytes(RLP.encode(out -> writeLegacyReceipt(receipt, out, options)));
      return;
    }
    writeLegacyReceipt(receipt, rlpOutput, options);
  }

  private static boolean shouldEncodeOpaqueBytes(
      final TransactionReceipt receipt, final TransactionReceiptEncodingConfiguration options) {
    // Check if the transaction type is not FRONTIER and if the encoding options require opaque
    // bytes
    return options.isWithOpaqueBytes()
        && !receipt.getTransactionType().equals(TransactionType.FRONTIER);
  }

  /**
   * Writes the transaction receipt to the output.
   *
   * @param receipt the transaction receipt
   * @param rlpOutput the RLP output
   * @param options the encoding options
   */
  private static void writeLegacyReceipt(
      final TransactionReceipt receipt,
      final RLPOutput rlpOutput,
      final TransactionReceiptEncodingConfiguration options) {
    if (!receipt.getTransactionType().equals(TransactionType.FRONTIER)) {
      rlpOutput.writeByte(receipt.getTransactionType().getSerializedType());
    }
    rlpOutput.startList();
    writeStatusOrStateRoot(receipt, rlpOutput);
    rlpOutput.writeLongScalar(receipt.getCumulativeGasUsed());
    if (options.isWithBloomFilter()) {
      rlpOutput.writeBytes(receipt.getBloomFilter());
    }
    writeLogs(receipt, rlpOutput, options);
    if (options.isWithRevertReason() && receipt.getRevertReason().isPresent()) {
      rlpOutput.writeBytes(receipt.getRevertReason().get());
    }
    rlpOutput.endList();
  }

  public static Bytes writeSyncReceiptForRootCalc(final SyncTransactionReceipt receipt) {
    final boolean isFrontier =
        !receipt.getTransactionTypeCode().isEmpty()
            && receipt.getTransactionTypeCode().get(0)
                == TransactionType.FRONTIER.getSerializedType();

    List<Bytes> encodedLogs =
        receipt.getLogs().stream()
            .map(
                (List<Bytes> log) -> {
                  Bytes encodedLogAddress = rlpEncodeNoCopy(log.getFirst());
                  List<Bytes> encodedLogTopics = new ArrayList<>();
                  for (int i = 1; i < log.size() - 1; i++) {
                    encodedLogTopics.add(rlpEncodeNoCopy(log.get(i)));
                  }
                  Bytes encodedLogData = rlpEncodeNoCopy(log.getLast());
                  return rlpEncodeListNoCopy(
                      List.of(
                          Bytes.concatenate(
                              encodedLogAddress,
                              rlpEncodeListNoCopy(encodedLogTopics),
                              encodedLogData)));
                })
            .toList();
    List<Bytes> mainList =
        List.of(
            rlpEncodeNoCopy(receipt.getStatusOrStateRoot()),
            rlpEncodeNoCopy(receipt.getCumulativeGasUsed()),
            rlpEncodeNoCopy(receipt.getBloomFilter()),
            rlpEncodeListNoCopy(encodedLogs));

    return !isFrontier
        ? Bytes.concatenate(
            rlpEncodeNoCopy(receipt.getTransactionTypeCode()), rlpEncodeListNoCopy(mainList))
        : rlpEncodeListNoCopy(mainList);
  }

  private static Bytes rlpEncodeNoCopy(final Bytes bytes) {
    if (bytes.size() == 1 && Byte.toUnsignedInt(bytes.get(0)) <= 127) {
      return bytes;
    } else if (bytes.size() <= 55) {
      return Bytes.concatenate(Bytes.of((byte) (0x80 + bytes.size())), bytes);
    } else { // bytes.size > 55
      Bytes length = Bytes.minimalBytes(bytes.size());
      return Bytes.concatenate(Bytes.of((byte) (0xb7 + length.size())), length, bytes);
    }
  }

  private static Bytes rlpEncodeListNoCopy(final List<Bytes> encodedBytesList) {
    int totalLength = encodedBytesList.stream().mapToInt(Bytes::size).sum();

    if (totalLength <= 55) {
      return Bytes.concatenate(
          Bytes.of((byte) (0xc0 + totalLength)), Bytes.concatenate(encodedBytesList));
    } else { // totalLength > 55
      Bytes length = Bytes.minimalBytes(totalLength);
      return Bytes.concatenate(
          Bytes.of((byte) (0xf7 + length.size())), length, Bytes.concatenate(encodedBytesList));
    }
  }

  /**
   * Writes the logs of the transaction receipt to the output.
   *
   * @param receipt the transaction receipt
   * @param rlpOutput the RLP output
   * @param options the encoding options
   */
  private static void writeLogs(
      final TransactionReceipt receipt,
      final RLPOutput rlpOutput,
      final TransactionReceiptEncodingConfiguration options) {
    rlpOutput.writeList(
        receipt.getLogsList(),
        (log, logOutput) -> log.writeTo(logOutput, options.isWithCompactedLogs()));
  }

  /**
   * Writes the status or state root of the transaction receipt to the output.
   *
   * @param receipt the transaction receipt
   * @param output the RLP output
   */
  private static void writeStatusOrStateRoot(
      final TransactionReceipt receipt, final RLPOutput output) {
    // Determine whether it's a state root-encoded transaction receipt
    // or is a status code-encoded transaction receipt.
    if (receipt.getStateRoot() != null) {
      output.writeBytes(receipt.getStateRoot());
    } else {
      output.writeLongScalar(receipt.getStatus());
    }
  }

  /**
   * Writes a flat receipt to the output. Eth69 uses a flat receipt format. See EIP-7642
   *
   * @param receipt the transaction receipt
   * @param output the RLP output
   * @param options the encoding options
   */
  private static void writeEth69Receipt(
      final TransactionReceipt receipt,
      final RLPOutput output,
      final TransactionReceiptEncodingConfiguration options) {
    output.startList();
    output.writeByte(receipt.getTransactionType().getEthSerializedType());
    writeStatusOrStateRoot(receipt, output);
    output.writeLongScalar(receipt.getCumulativeGasUsed());
    writeLogs(receipt, output, options);
    output.endList();
  }
}
