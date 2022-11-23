/*
 * Copyright contributors to Hyperledger Besu.
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

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

public class NewPooledTransactionHashesMessage66 extends AbstractNewPooledTransactionHashesMessage {

  private List<TransactionAnnouncement> pendingTransactions;

  NewPooledTransactionHashesMessage66(final Bytes rlp) {
    super(rlp);
  }

  public static AbstractNewPooledTransactionHashesMessage create(
      final List<Hash> pendingTransactionsHashes) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeList(pendingTransactionsHashes, (h, w) -> w.writeBytes(h));
    return new NewPooledTransactionHashesMessage66(out.encoded());
  }

  public static AbstractNewPooledTransactionHashesMessage readFrom(final MessageData message) {
    if (message instanceof NewPooledTransactionHashesMessage66) {
      return (NewPooledTransactionHashesMessage66) message;
    }
    final int code = message.getCode();
    if (code != EthPV65.NEW_POOLED_TRANSACTION_HASHES) {
      throw new IllegalArgumentException(
          String.format(
              "Message has code %d and thus is not a NewPooledTransactionHashesMessage.", code));
    }
    return new NewPooledTransactionHashesMessage66(message.getData());
  }

  @Override
  public List<TransactionAnnouncement> pendingTransactions() {
    if (pendingTransactions == null) {
      final BytesValueRLPInput in = new BytesValueRLPInput(getData(), false);
      final List<Hash> hashes = in.readList(rlp -> Hash.wrap(rlp.readBytes32()));
      pendingTransactions =
          hashes.stream().map(TransactionAnnouncement::new).collect(Collectors.toList());
    }
    return pendingTransactions;
  }
}
