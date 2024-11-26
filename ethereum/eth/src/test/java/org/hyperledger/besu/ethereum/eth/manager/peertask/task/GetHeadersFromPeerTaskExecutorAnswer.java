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
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class GetHeadersFromPeerTaskExecutorAnswer
    implements Answer<PeerTaskExecutorResult<List<BlockHeader>>> {
  private final Blockchain otherBlockchain;
  private final EthPeers ethPeers;

  public GetHeadersFromPeerTaskExecutorAnswer(
      final Blockchain otherBlockchain, final EthPeers ethPeers) {
    this.otherBlockchain = otherBlockchain;
    this.ethPeers = ethPeers;
  }

  @Override
  public PeerTaskExecutorResult<List<BlockHeader>> answer(final InvocationOnMock invocationOnMock)
      throws Throwable {
    GetHeadersFromPeerTask task = invocationOnMock.getArgument(0, GetHeadersFromPeerTask.class);
    List<BlockHeader> getHeadersFromPeerTaskResult = new ArrayList<>();
    BlockHeader initialHeader;
    if (task.getBlockHash() != null) {
      initialHeader = otherBlockchain.getBlockHeader(task.getBlockHash()).orElse(null);
    } else {
      initialHeader = otherBlockchain.getBlockHeader(task.getBlockNumber()).orElse(null);
    }
    getHeadersFromPeerTaskResult.add(initialHeader);

    if (initialHeader != null && task.getMaxHeaders() > 1) {
      if (task.getDirection() == GetHeadersFromPeerTask.Direction.FORWARD) {
        int skip = task.getSkip() + 1;
        long nextHeaderNumber = initialHeader.getNumber() + skip;
        long getLimit = nextHeaderNumber + ((task.getMaxHeaders() - 1) * skip);
        for (long i = nextHeaderNumber; i < getLimit; i += skip) {
          Optional<BlockHeader> header = otherBlockchain.getBlockHeader(i);
          if (header.isPresent()) {
            getHeadersFromPeerTaskResult.add(header.get());
          } else {
            break;
          }
        }

      } else {
        int skip = task.getSkip() + 1;
        long nextHeaderNumber = initialHeader.getNumber() - skip;
        long getLimit = nextHeaderNumber - ((task.getMaxHeaders() - 1) * skip);
        for (long i = initialHeader.getNumber() - 1; i > getLimit; i -= skip) {
          Optional<BlockHeader> header = otherBlockchain.getBlockHeader(i);
          if (header.isPresent()) {
            getHeadersFromPeerTaskResult.add(header.get());
          } else {
            break;
          }
        }
      }
    }

    return new PeerTaskExecutorResult<>(
        Optional.of(getHeadersFromPeerTaskResult),
        PeerTaskExecutorResponseCode.SUCCESS,
        ethPeers.bestPeer());
  }
}
