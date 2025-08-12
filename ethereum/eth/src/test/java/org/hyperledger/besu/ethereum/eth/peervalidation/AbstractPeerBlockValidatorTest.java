/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.peervalidation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.hyperledger.besu.ethereum.eth.manager.peertask.task.GetHeadersFromPeerTask;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public abstract class AbstractPeerBlockValidatorTest {

  abstract AbstractPeerBlockValidator createValidator(
      long blockNumber, long buffer, PeerTaskExecutor peerTaskExecutor);

  @Test
  public void validatePeer_unresponsivePeer() {
    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setEthScheduler(new DeterministicEthScheduler())
            .build();
    final long blockNumber = 500;

    final PeerTaskExecutor peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);

    final PeerValidator validator = createValidator(blockNumber, 0, peerTaskExecutor);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, blockNumber);

    Mockito.when(
            peerTaskExecutor.executeAgainstPeer(
                Mockito.any(GetHeadersFromPeerTask.class), Mockito.eq(peer.getEthPeer())))
        .thenReturn(
            new PeerTaskExecutorResult<>(
                Optional.empty(),
                PeerTaskExecutorResponseCode.TIMEOUT,
                List.of(peer.getEthPeer())));

    final CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), peer.getEthPeer());

    // Request should timeout immediately
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  public void canBeValidated() {
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setEthScheduler(
                new DeterministicEthScheduler(
                    DeterministicEthScheduler.TimeoutPolicy.ALWAYS_TIMEOUT))
            .build();
    final long blockNumber = 500;
    final long buffer = 10;

    final PeerTaskExecutor peerTaskExecutor = Mockito.mock(PeerTaskExecutor.class);

    final PeerValidator validator = createValidator(blockNumber, buffer, peerTaskExecutor);
    final EthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0).getEthPeer();

    peer.chainState().update(gen.hash(), blockNumber - 10);
    assertThat(validator.canBeValidated(peer)).isFalse();

    peer.chainState().update(gen.hash(), blockNumber);
    assertThat(validator.canBeValidated(peer)).isFalse();

    peer.chainState().update(gen.hash(), blockNumber + buffer - 1);
    assertThat(validator.canBeValidated(peer)).isFalse();

    peer.chainState().update(gen.hash(), blockNumber + buffer);
    assertThat(validator.canBeValidated(peer)).isTrue();

    peer.chainState().update(gen.hash(), blockNumber + buffer + 10);
    assertThat(validator.canBeValidated(peer)).isTrue();
  }
}
