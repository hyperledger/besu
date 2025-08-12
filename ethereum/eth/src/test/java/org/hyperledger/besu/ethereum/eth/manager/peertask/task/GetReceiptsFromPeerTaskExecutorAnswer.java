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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class GetReceiptsFromPeerTaskExecutorAnswer
    implements Answer<PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>>> {
  private final Blockchain otherBlockchain;
  private final EthPeers ethPeers;

  public GetReceiptsFromPeerTaskExecutorAnswer(
      final Blockchain otherBlockchain, final EthPeers ethPeers) {
    this.otherBlockchain = otherBlockchain;
    this.ethPeers = ethPeers;
  }

  @Override
  public PeerTaskExecutorResult<Map<BlockHeader, List<TransactionReceipt>>> answer(
      final InvocationOnMock invocationOnMock) throws Throwable {
    GetReceiptsFromPeerTask task = invocationOnMock.getArgument(0, GetReceiptsFromPeerTask.class);
    EthPeer ethPeer =
        invocationOnMock.getArguments().length == 2
            ? invocationOnMock.getArgument(1, EthPeer.class)
            : ethPeers.bestPeer().get();
    Map<BlockHeader, List<TransactionReceipt>> transactionReceiptsByHeader = new HashMap<>();
    for (BlockHeader blockHeader : task.getBlockHeaders()) {
      transactionReceiptsByHeader.put(
          blockHeader,
          otherBlockchain
              .getTxReceipts(blockHeader.getBlockHash())
              .orElse(Collections.emptyList()));
    }
    return new PeerTaskExecutorResult<>(
        Optional.of(transactionReceiptsByHeader),
        PeerTaskExecutorResponseCode.SUCCESS,
        List.of(ethPeer));
  }
}
