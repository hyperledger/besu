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

import static org.hyperledger.besu.ethereum.core.Transaction.toHashList;
import static org.hyperledger.besu.ethereum.eth.messages.TransactionAnnouncement.decodeForEth66;
import static org.hyperledger.besu.ethereum.eth.messages.TransactionAnnouncement.decodeForEth68;
import static org.hyperledger.besu.ethereum.eth.messages.TransactionAnnouncement.encodeForEth66;
import static org.hyperledger.besu.ethereum.eth.messages.TransactionAnnouncement.encodeForEth68;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;

public class NewPooledTransactionHashesMessage extends AbstractMessageData {

  private static final int MESSAGE_CODE = EthPV65.NEW_POOLED_TRANSACTION_HASHES;
  private final boolean isEth68MessageData;
  private List<TransactionAnnouncement> pendingTransactions;

  @VisibleForTesting
  public NewPooledTransactionHashesMessage(final Bytes rlp, final boolean isEth68MessageData) {
    super(rlp);
    this.isEth68MessageData = isEth68MessageData;
  }

  @Override
  public int getCode() {
    return MESSAGE_CODE;
  }

  public static NewPooledTransactionHashesMessage create(final List<Hash> hashes) {
    return new NewPooledTransactionHashesMessage(encodeForEth66(hashes), false);
  }

  public static NewPooledTransactionHashesMessage readFrom(final MessageData message) {
    return NewPooledTransactionHashesMessage.readFrom(message, false);
  }

  public static NewPooledTransactionHashesMessage create(
      final List<Transaction> pendingTransactions, final boolean isEth68MessageData) {
    if (isEth68MessageData) {
      return new NewPooledTransactionHashesMessage(encodeForEth68(pendingTransactions), true);
    } else {
      return new NewPooledTransactionHashesMessage(
          encodeForEth66(toHashList(pendingTransactions)), false);
    }
  }

  public static NewPooledTransactionHashesMessage readFrom(
      final MessageData message, final boolean isEth68MessageData) {

    if (message instanceof NewPooledTransactionHashesMessage) {
      return (NewPooledTransactionHashesMessage) message;
    }
    final int code = message.getCode();
    if (code != MESSAGE_CODE) {
      throw new IllegalArgumentException(
          String.format(
              "Message has code %d and thus is not a NewPooledTransactionHashesMessage.", code));
    }
    return new NewPooledTransactionHashesMessage(message.getData(), isEth68MessageData);
  }

  public List<TransactionAnnouncement> pendingTransactions() {
    if (pendingTransactions == null) {
      pendingTransactions =
          isEth68MessageData
              ? decodeForEth68(new BytesValueRLPInput(data, false))
              : decodeForEth66(new BytesValueRLPInput(data, false));
    }
    return pendingTransactions;
  }
}
