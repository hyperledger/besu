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

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.SyncBlock;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class GetSyncBlockBodiesFromPeerTaskExecutorAnswer
    implements Answer<PeerTaskExecutorResult<List<SyncBlock>>> {
  private final Blockchain otherBlockchain;
  private final EthPeers ethPeers;
  private final ProtocolSchedule protocolSchedule;

  public GetSyncBlockBodiesFromPeerTaskExecutorAnswer(
      final Blockchain otherBlockchain,
      final EthPeers ethPeers,
      final ProtocolSchedule protocolSchedule) {
    this.otherBlockchain = otherBlockchain;
    this.ethPeers = ethPeers;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public PeerTaskExecutorResult<List<SyncBlock>> answer(final InvocationOnMock invocationOnMock)
      throws Throwable {
    GetSyncBlockBodiesFromPeerTask task =
        invocationOnMock.getArgument(0, GetSyncBlockBodiesFromPeerTask.class);
    EthPeer ethPeer =
        invocationOnMock.getArguments().length == 2
            ? invocationOnMock.getArgument(1, EthPeer.class)
            : ethPeers.bestPeer().get();
    List<SyncBlock> blocks =
        task.getBlockHeaders().stream()
            .map((bh) -> otherBlockchain.getBlockByHash(bh.getBlockHash()).get())
            .map(
                (b) ->
                    new SyncBlock(
                        b.getHeader(),
                        new SyncBlockBody(
                            b.toRlp(),
                            b.getBody().getTransactions().stream()
                                .map((t) -> t.getRawRlp().get())
                                .toList(),
                            RLP.encode(
                                (rlpOut) -> {
                                  rlpOut.startList();
                                  b.getBody().getOmmers().forEach((bh) -> bh.writeTo(rlpOut));
                                  rlpOut.endList();
                                }),
                            b.getBody()
                                .getWithdrawals()
                                .map(
                                    (withdrawals) ->
                                        withdrawals.stream()
                                            .map((w) -> RLP.encode((rlpOut) -> w.writeTo(rlpOut)))
                                            .toList())
                                .orElse(Collections.emptyList()),
                            protocolSchedule)))
            .toList();
    return new PeerTaskExecutorResult<>(
        Optional.of(blocks), PeerTaskExecutorResponseCode.SUCCESS, List.of(ethPeer));
  }
}
