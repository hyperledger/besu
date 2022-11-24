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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.util.List;
import java.util.stream.Collectors;

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
    return createMessageForEth66(hashes);
  }

  public static NewPooledTransactionHashesMessage create(
      final List<Transaction> pendingTransactions, final boolean isEth68MessageData) {
    if (isEth68MessageData) {
      return createMessageForEth68(pendingTransactions);
    } else {
      final List<Hash> hashes =
          pendingTransactions.stream().map(Transaction::getHash).collect(Collectors.toList());
      return createMessageForEth66(hashes);
    }
  }

  public static NewPooledTransactionHashesMessage readFrom(final MessageData message) {
    return NewPooledTransactionHashesMessage.readFrom(message, false);
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
      pendingTransactions = isEth68MessageData ? decodeForEth68() : decodeForEth66();
    }
    return pendingTransactions;
  }

  private static NewPooledTransactionHashesMessage createMessageForEth66(
      final List<Hash> pendingTransactionsHashes) {
    return new NewPooledTransactionHashesMessage(
        TransactionAnnouncement.encodeForEth66(pendingTransactionsHashes), false);
  }

  private static NewPooledTransactionHashesMessage createMessageForEth68(
      final List<Transaction> pendingTransactions) {
    return new NewPooledTransactionHashesMessage(
        TransactionAnnouncement.encodeForEth68(pendingTransactions), true);
  }

  private List<TransactionAnnouncement> decodeForEth68() {
    final RLPInput input = new BytesValueRLPInput(data, false);
    input.enterList();
    final List<TransactionType> types =
        input.readList(rlp -> TransactionType.of(rlp.readByte() & 0xff));
    final List<Integer> sizes = input.readList(RLPInput::readInt);
    final List<Hash> hashes = input.readList(rlp -> Hash.wrap(rlp.readBytes32()));
    input.leaveList();
    return TransactionAnnouncement.create(types, sizes, hashes);
  }

  private List<TransactionAnnouncement> decodeForEth66() {
    final RLPInput input = new BytesValueRLPInput(data, false);
    final List<Hash> hashes = input.readList(rlp -> Hash.wrap(rlp.readBytes32()));
    return hashes.stream().map(TransactionAnnouncement::new).collect(Collectors.toList());
  }
}
