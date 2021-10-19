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
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

public final class NewPooledTransactionHashesMessage extends AbstractMessageData {

  private static final int MESSAGE_CODE = EthPV65.NEW_POOLED_TRANSACTION_HASHES;
  private List<Hash> pendingTransactions;

  NewPooledTransactionHashesMessage(final Bytes rlp) {
    super(rlp);
  }

  @Override
  public int getCode() {
    return MESSAGE_CODE;
  }

  public static NewPooledTransactionHashesMessage create(final List<Hash> pendingTransactions) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeList(pendingTransactions, (h, w) -> w.writeBytes(h));
    return new NewPooledTransactionHashesMessage(out.encoded());
  }

  public static NewPooledTransactionHashesMessage readFrom(final MessageData message) {
    if (message instanceof NewPooledTransactionHashesMessage) {
      return (NewPooledTransactionHashesMessage) message;
    }
    final int code = message.getCode();
    if (code != MESSAGE_CODE) {
      throw new IllegalArgumentException(
          String.format(
              "Message has code %d and thus is not a NewPooledTransactionHashesMessage.", code));
    }

    return new NewPooledTransactionHashesMessage(message.getData());
  }

  public List<Hash> pendingTransactions() {
    if (pendingTransactions == null) {
      final BytesValueRLPInput in = new BytesValueRLPInput(getData(), false);
      pendingTransactions = in.readList(rlp -> Hash.wrap(rlp.readBytes32()));
    }
    return pendingTransactions;
  }
}
