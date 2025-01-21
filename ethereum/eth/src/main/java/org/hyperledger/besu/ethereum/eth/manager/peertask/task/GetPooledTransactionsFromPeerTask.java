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
package org.hyperledger.besu.ethereum.eth.manager.peertask.task;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.InvalidPeerTaskResponseException;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTask;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskValidationResponse;
import org.hyperledger.besu.ethereum.eth.messages.GetPooledTransactionsMessage;
import org.hyperledger.besu.ethereum.eth.messages.PooledTransactionsMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.List;
import java.util.function.Predicate;

public class GetPooledTransactionsFromPeerTask implements PeerTask<List<Transaction>> {

  private final List<Hash> hashes;

  public GetPooledTransactionsFromPeerTask(final List<Hash> hashes) {
    this.hashes = hashes.stream().distinct().toList();
  }

  @Override
  public SubProtocol getSubProtocol() {
    return EthProtocol.get();
  }

  @Override
  public MessageData getRequestMessage() {
    return GetPooledTransactionsMessage.create(hashes);
  }

  @Override
  public List<Transaction> processResponse(final MessageData messageData)
      throws InvalidPeerTaskResponseException {
    final PooledTransactionsMessage pooledTransactionsMessage =
        PooledTransactionsMessage.readFrom(messageData);
    final List<Transaction> responseTransactions = pooledTransactionsMessage.transactions();
    if (responseTransactions.size() > hashes.size()) {
      throw new InvalidPeerTaskResponseException(
          "Response transaction count does not match request hash count");
    }
    return responseTransactions;
  }

  @Override
  public Predicate<EthPeer> getPeerRequirementFilter() {
    return (peer) -> true;
  }

  @Override
  public PeerTaskValidationResponse validateResult(final List<Transaction> result) {
    if (!result.stream().allMatch((t) -> hashes.contains(t.getHash()))) {
      return PeerTaskValidationResponse.RESULTS_DO_NOT_MATCH_QUERY;
    }
    return PeerTaskValidationResponse.RESULTS_VALID_AND_GOOD;
  }

  public List<Hash> getHashes() {
    return hashes;
  }
}
