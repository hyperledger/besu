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

import com.google.common.annotations.VisibleForTesting;

public class TransactionReceiptEncoder {
  /**
   * Write an RLP representation.
   *
   * @param out The RLP output to write to
   */
  public static void writeToForNetwork(final TransactionReceipt receipt, final RLPOutput out) {
    writeTo(receipt, out, false, false);
  }

  public static void writeToForStorage(
      final TransactionReceipt receipt, final RLPOutput out, final boolean compacted) {
    writeTo(receipt, out, true, compacted);
  }

  @VisibleForTesting
  static void writeTo(
      final TransactionReceipt receipt,
      final RLPOutput rlpOutput,
      final boolean withRevertReason,
      final boolean compacted) {
    if (receipt.getTransactionType().equals(TransactionType.FRONTIER)) {
      writeToForReceiptTrie(receipt, rlpOutput, withRevertReason, compacted);
    } else {
      rlpOutput.writeBytes(
          RLP.encode(out -> writeToForReceiptTrie(receipt, out, withRevertReason, compacted)));
    }
  }

  public static void writeToForReceiptTrie(
      final TransactionReceipt receipt,
      final RLPOutput rlpOutput,
      final boolean withRevertReason,
      final boolean compacted) {
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
    if (!compacted) {
      rlpOutput.writeBytes(receipt.getBloomFilter());
    }
    rlpOutput.writeList(
        receipt.getLogsList(), (log, logOutput) -> log.writeTo(logOutput, compacted));
    if (withRevertReason && receipt.getRevertReason().isPresent()) {
      rlpOutput.writeBytes(receipt.getRevertReason().get());
    }
    rlpOutput.endList();
  }
}
