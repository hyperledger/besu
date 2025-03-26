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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

public class TransactionReceiptEncoder {

  public static final TransactionReceiptEncodingOptions NETWORK =
      new TransactionReceiptEncodingOptions(false, false, true, true);

  public static final TransactionReceiptEncodingOptions NETWORK_ETH69 =
      new TransactionReceiptEncodingOptions(false, false, false, false);

  public static final TransactionReceiptEncodingOptions STORAGE_COMPACTED =
      new TransactionReceiptEncodingOptions(true, true, true, false);

  public static final TransactionReceiptEncodingOptions STORAGE_UNCOMPACTED =
      new TransactionReceiptEncodingOptions(true, false, true, true);

  public static final TransactionReceiptEncodingOptions TRIE =
      new TransactionReceiptEncodingOptions(false, false, false, true);

  public static void writeTo(
      final TransactionReceipt receipt,
      final RLPOutput rlpOutput,
      final TransactionReceiptEncodingOptions options) {

    if (options.withOpaqueBytes) {
      if (receipt.getTransactionType().equals(TransactionType.FRONTIER)) {
        write(receipt, rlpOutput, options);
      } else {
        rlpOutput.writeBytes(RLP.encode(out -> write(receipt, out, options)));
      }
    } else {
      write(receipt, rlpOutput, options);
    }
  }

  private static void write(
      final TransactionReceipt receipt,
      final RLPOutput rlpOutput,
      final TransactionReceiptEncodingOptions options) {
    if (!receipt.getTransactionType().equals(TransactionType.FRONTIER)) {
      rlpOutput.writeIntScalar(receipt.getTransactionType().getSerializedType());
    }

    rlpOutput.startList();

    // Determine whether it's a state root-encoded transaction receipt
    // or is a status code-encoded transaction receipt.
    if (receipt.getStateRoot() != null) {
      rlpOutput.writeBytes(receipt.getStateRoot());
    } else {
      rlpOutput.writeLongScalar(receipt.getStatus());
    }
    rlpOutput.writeLongScalar(receipt.getCumulativeGasUsed());
    if (!options.withBloomFilter) {
      rlpOutput.writeBytes(receipt.getBloomFilter());
    }
    rlpOutput.writeList(
        receipt.getLogsList(),
        (log, logOutput) -> log.writeTo(logOutput, options.withCompactedLogs));
    if (options.withRevertReason && receipt.getRevertReason().isPresent()) {
      rlpOutput.writeBytes(receipt.getRevertReason().get());
    }
    rlpOutput.endList();
  }

  public static class TransactionReceiptEncodingOptions {
    private final boolean withRevertReason;
    private final boolean withCompactedLogs;
    private final boolean withOpaqueBytes;
    private final boolean withBloomFilter;

    public TransactionReceiptEncodingOptions(
        final boolean withRevertReason,
        final boolean withCompactedLogs,
        final boolean withOpaqueBytes,
        final boolean withBloomFilter) {
      this.withRevertReason = withRevertReason;
      this.withCompactedLogs = withCompactedLogs;
      this.withOpaqueBytes = withOpaqueBytes;
      this.withBloomFilter = withBloomFilter;
    }
  }
}
