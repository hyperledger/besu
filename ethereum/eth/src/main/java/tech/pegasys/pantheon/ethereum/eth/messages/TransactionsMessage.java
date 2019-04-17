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

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Iterator;
import java.util.function.Function;

public class TransactionsMessage extends AbstractMessageData {

  public static TransactionsMessage readFrom(final MessageData message) {
    if (message instanceof TransactionsMessage) {
      return (TransactionsMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.TRANSACTIONS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a TransactionsMessage.", code));
    }
    return new TransactionsMessage(message.getData());
  }

  public static TransactionsMessage create(final Iterable<Transaction> transactions) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    for (final Transaction transaction : transactions) {
      transaction.writeTo(tmp);
    }
    tmp.endList();
    return new TransactionsMessage(tmp.encoded());
  }

  TransactionsMessage(final BytesValue data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV62.TRANSACTIONS;
  }

  public Iterator<Transaction> transactions(
      final Function<RLPInput, Transaction> transactionReader) {
    return new BytesValueRLPInput(data, false).readList(transactionReader).iterator();
  }
}
