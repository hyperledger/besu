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
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class TransactionReceiptEncoder {

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
      rlpOutput.writeIntScalar(receipt.getTransactionType().getSerializedType());
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
    output.writeIntScalar(receipt.getTransactionType().getEthSerializedType());
    writeStatusOrStateRoot(receipt, output);
    output.writeLongScalar(receipt.getCumulativeGasUsed());
    writeLogs(receipt, output, options);
    output.endList();
  }
}
