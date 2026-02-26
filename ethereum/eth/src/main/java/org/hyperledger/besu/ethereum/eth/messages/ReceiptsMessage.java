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
package org.hyperledger.besu.ethereum.eth.messages;

import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.encoding.receipt.SyncTransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptDecoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder;
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.concurrent.NotThreadSafe;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;

@NotThreadSafe
public final class ReceiptsMessage extends AbstractMessageData {
  /**
   * This default decoder instance is used for performance reasons to avoid creating a new decoder
   * for every ReceiptsMessage
   */
  private static final SyncTransactionReceiptDecoder DEFAULT_SYNC_TRANSACTION_RECEIPT_DECODER =
      new SyncTransactionReceiptDecoder();

  private final SyncTransactionReceiptDecoder syncTransactionReceiptDecoder;
  private List<List<TransactionReceipt>> receiptsByBlock;
  private List<List<SyncTransactionReceipt>> syncReceiptsByBlock;
  private Optional<Boolean> lastBlockIncomplete;

  private ReceiptsMessage(
      final Bytes data,
      final SyncTransactionReceiptDecoder syncTransactionReceiptDecoder,
      final List<List<TransactionReceipt>> receipts,
      final Boolean lastBlockIncomplete) {
    super(data);
    this.syncTransactionReceiptDecoder = syncTransactionReceiptDecoder;
    this.receiptsByBlock = receipts;
    this.lastBlockIncomplete =
        (lastBlockIncomplete != null) ? Optional.of(lastBlockIncomplete) : null;
  }

  public static ReceiptsMessage readFrom(final MessageData message) {
    if (message instanceof ReceiptsMessage) {
      return (ReceiptsMessage) message;
    }
    final int code = message.getCode();
    if (code != EthProtocolMessages.RECEIPTS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a ReceiptsMessage.", code));
    }
    return new ReceiptsMessage(
        message.getData(), DEFAULT_SYNC_TRANSACTION_RECEIPT_DECODER, null, null);
  }

  @VisibleForTesting
  public static ReceiptsMessage create(
      final List<List<TransactionReceipt>> receipts,
      final TransactionReceiptEncodingConfiguration encodingConfiguration) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    receipts.forEach(
        (receiptSet) -> {
          tmp.startList();
          receiptSet.forEach(r -> TransactionReceiptEncoder.writeTo(r, tmp, encodingConfiguration));
          tmp.endList();
        });
    tmp.endList();
    return new ReceiptsMessage(
        tmp.encoded(), DEFAULT_SYNC_TRANSACTION_RECEIPT_DECODER, receipts, null);
  }

  /**
   * Create a message with raw, already encoded data. No checks are performed to validate the
   * rlp-encoded data.
   *
   * @param data An rlp-encoded list of sets of receipts
   * @return A new ReceiptsMessage
   */
  public static ReceiptsMessage createUnsafe(final Bytes data) {
    return new ReceiptsMessage(data, DEFAULT_SYNC_TRANSACTION_RECEIPT_DECODER, null, null);
  }

  public static ReceiptsMessage createUnsafe(final Bytes data, final boolean lastBlockIncomplete) {
    return new ReceiptsMessage(
        data, DEFAULT_SYNC_TRANSACTION_RECEIPT_DECODER, null, lastBlockIncomplete);
  }

  @Override
  public int getCode() {
    return EthProtocolMessages.RECEIPTS;
  }

  public List<List<TransactionReceipt>> receipts() {
    if (receiptsByBlock == null) {
      receiptsByBlock =
          deserialize(
              getData(), rlpInput -> TransactionReceiptDecoder.readFrom(rlpInput, false), false);
    }
    return receiptsByBlock;
  }

  public List<List<SyncTransactionReceipt>> syncReceipts() {
    if (syncReceiptsByBlock == null) {
      syncReceiptsByBlock =
          deserialize(
              getData(),
              rlpInput ->
                  syncTransactionReceiptDecoder.decode(
                      rlpInput.nextIsList() ? rlpInput.currentListAsBytes() : rlpInput.readBytes()),
              false);
    }
    return syncReceiptsByBlock;
  }

  public Optional<Boolean> lastBlockIncomplete() {
    if (lastBlockIncomplete == null) {
      deserialize(getData(), null, true);
    }
    return lastBlockIncomplete;
  }

  private <TR> List<List<TR>> deserialize(
      final Bytes data,
      final Function<RLPInput, TR> deserializer,
      final boolean onlyLastBlockIncomplete) {
    final RLPInput input = new BytesValueRLPInput(data, false);
    this.lastBlockIncomplete =
        input.nextIsList() ? Optional.empty() : Optional.of(input.readLongScalar() == 1);
    if (onlyLastBlockIncomplete) {
      return null;
    }

    final List<List<TR>> receiptsByBlock = new ArrayList<>(input.enterList());
    while (input.nextIsList()) {
      final int setSize = input.enterList();
      final List<TR> receiptSet = new ArrayList<>(setSize);
      for (int i = 0; i < setSize; i++) {
        receiptSet.add(deserializer.apply(input));
      }
      input.leaveList();
      receiptsByBlock.add(receiptSet);
    }
    input.leaveList();
    return receiptsByBlock;
  }
}
