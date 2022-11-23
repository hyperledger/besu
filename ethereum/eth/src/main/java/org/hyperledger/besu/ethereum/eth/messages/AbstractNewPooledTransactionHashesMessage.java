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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

public abstract class AbstractNewPooledTransactionHashesMessage extends AbstractMessageData {

  private static final int MESSAGE_CODE = EthPV65.NEW_POOLED_TRANSACTION_HASHES;

  AbstractNewPooledTransactionHashesMessage(final Bytes rlp) {
    super(rlp);
  }

  @Override
  public int getCode() {
    return MESSAGE_CODE;
  }

  public abstract List<TransactionAnnouncement> pendingTransactions();

  public static AbstractNewPooledTransactionHashesMessage create(
      final List<Hash> pendingTransactions, final Capability capability) {
    return (EthProtocol.ETH68.equals(capability))
        ? NewPooledTransactionHashesMessage68.create(pendingTransactions)
        : NewPooledTransactionHashesMessage66.create(pendingTransactions);
  }

  public static AbstractNewPooledTransactionHashesMessage readFrom(
      final MessageData message, final Capability capability) {
    return (EthProtocol.ETH68.equals(capability))
        ? NewPooledTransactionHashesMessage68.readFrom(message)
        : NewPooledTransactionHashesMessage66.readFrom(message);
  }
}
