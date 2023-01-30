/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.manager.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.BlockHeader.GENESIS_BLOCK_NUMBER;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.ethtaskutils.RetryingSwitchingPeerMessageTaskTest;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask.PeerTaskResult;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.junit.Ignore;
import org.junit.Test;

public class RetryingGetBlockFromPeersTaskTest
    extends RetryingSwitchingPeerMessageTaskTest<PeerTaskResult<Block>> {

  @Override
  protected void assertResultMatchesExpectation(
      final PeerTaskResult<Block> requestedData,
      final PeerTaskResult<Block> response,
      final EthPeer respondingPeer) {
    assertThat(response.getResult()).isEqualTo(requestedData.getResult());
  }

  @Override
  protected PeerTaskResult<Block> generateDataToBeRequested() {
    final Block block = blockchain.getBlockByNumber(10).get();
    return new PeerTaskResult<>(mock(EthPeer.class), block);
  }

  @Override
  protected RetryingGetBlockFromPeersTask createTask(final PeerTaskResult<Block> requestedData) {
    return RetryingGetBlockFromPeersTask.create(
        protocolSchedule,
        ethContext,
        metricsSystem,
        maxRetries,
        Optional.of(requestedData.getResult().getHash()),
        GENESIS_BLOCK_NUMBER);
  }

  @Test
  @Override
  @Ignore("GetBlock could not return partial response")
  public void failsWhenPeerReturnsPartialResultThenStops() {}

  @Override
  @Test
  @Ignore("GetBlock could not return partial response")
  public void completesWhenPeerReturnsPartialResult()
      throws ExecutionException, InterruptedException {
    super.completesWhenPeerReturnsPartialResult();
  }
}
