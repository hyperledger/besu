/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.messages;

import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MessageData;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;

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

  private ReceiptsMessage(final BytesValue data) {
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
