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
package org.hyperledger.besu.ethereum.eth.core;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.SyncTransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.mainnet.DefaultProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.SimpleNoCopyRlpEncoder;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public class Utils {
  private static final SyncTransactionReceiptEncoder SYNC_RECEIPT_ENCODER =
      new SyncTransactionReceiptEncoder(new SimpleNoCopyRlpEncoder());

  public static SyncTransactionReceipt receiptToSyncReceipt(
      final TransactionReceipt receipt,
      final TransactionReceiptEncodingConfiguration receiptEncodingConfiguration) {
    BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    TransactionReceiptEncoder.writeTo(receipt, rlpOutput, receiptEncodingConfiguration);
    return new SyncTransactionReceipt(rlpOutput.encoded());
  }

  public static List<SyncTransactionReceipt> receiptsToSyncReceipts(
      final List<TransactionReceipt> receipts,
      final TransactionReceiptEncodingConfiguration receiptEncodingConfiguration) {

    return receipts.stream()
        .map(receipt -> Utils.receiptToSyncReceipt(receipt, receiptEncodingConfiguration))
        .toList();
  }

  public static SyncBlock blockToSyncBlock(final Block block) {
    BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    block.getBody().writeWrappedBodyTo(rlpOutput);
    final BytesValueRLPInput input = new BytesValueRLPInput(rlpOutput.encoded(), false);
    final SyncBlockBody syncBlockBody =
        SyncBlockBody.readWrappedBodyFrom(
            input, false, new DefaultProtocolSchedule(Optional.of(BigInteger.ONE)));
    return new SyncBlock(block.getHeader(), syncBlockBody);
  }

  public static List<SyncBlock> blocksToSyncBlocks(final List<Block> blocks) {
    return blocks.stream().map(Utils::blockToSyncBlock).toList();
  }

  public static int compareSyncReceipts(
      final SyncTransactionReceipt receipt1, final SyncTransactionReceipt receipt2) {
    if (receipt1.isFormattedForRootCalculation()) {
      if (receipt2.isFormattedForRootCalculation()) {
        return receipt1.getRlpBytes().compareTo(receipt2.getRlpBytes());
      }
      return receipt1
          .getRlpBytes()
          .compareTo(SYNC_RECEIPT_ENCODER.encodeForRootCalculation(receipt2));
    } else {
      if (receipt2.isFormattedForRootCalculation()) {
        return SYNC_RECEIPT_ENCODER
            .encodeForRootCalculation(receipt1)
            .compareTo(receipt2.getRlpBytes());
      }
      return SYNC_RECEIPT_ENCODER
          .encodeForRootCalculation(receipt1)
          .compareTo(SYNC_RECEIPT_ENCODER.encodeForRootCalculation(receipt2));
    }
  }
}
