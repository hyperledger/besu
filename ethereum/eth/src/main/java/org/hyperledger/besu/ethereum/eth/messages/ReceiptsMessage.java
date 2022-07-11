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

import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public final class ReceiptsMessage extends AbstractMessageData {

  public static ReceiptsMessage readFrom(final MessageData message) {
    if (message instanceof ReceiptsMessage) {
      return (ReceiptsMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV63.RECEIPTS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a ReceiptsMessage.", code));
    }
    return new ReceiptsMessage(message.getData());
  }

  public static ReceiptsMessage create(final List<List<TransactionReceipt>> receipts) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    receipts.forEach(
        (receiptSet) -> {
          tmp.startList();
          receiptSet.forEach(r -> r.writeTo(tmp));
          tmp.endList();
        });
    tmp.endList();
    return new ReceiptsMessage(tmp.encoded());
  }

  /**
   * Create a message with raw, already encoded data. No checks are performed to validate the
   * rlp-encoded data.
   *
   * @param data An rlp-encoded list of sets of receipts
   * @return A new ReceiptsMessage
   */
  public static ReceiptsMessage createUnsafe(final Bytes data) {
    return new ReceiptsMessage(data);
  }

  private ReceiptsMessage(final Bytes data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV63.RECEIPTS;
  }

  public List<List<TransactionReceipt>> receipts() {
    final RLPInput input = new BytesValueRLPInput(data, false);
    input.enterList();
    final List<List<TransactionReceipt>> receipts = new ArrayList<>();
    while (input.nextIsList()) {
      final int setSize = input.enterList();
      final List<TransactionReceipt> receiptSet = new ArrayList<>(setSize);
      for (int i = 0; i < setSize; i++) {
        receiptSet.add(TransactionReceipt.readFrom(input, false));
      }
      input.leaveList();
      receipts.add(receiptSet);
    }
    input.leaveList();
    return receipts;
  }
}
