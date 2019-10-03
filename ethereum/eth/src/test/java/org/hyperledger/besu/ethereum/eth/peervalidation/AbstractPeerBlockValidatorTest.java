/*
 *
 *  * Copyright 2019 ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.peervalidation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.eth.manager.DeterministicEthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public abstract class AbstractPeerBlockValidatorTest {

  abstract AbstractPeerBlockValidator createValidator(long blockNumber, long buffer);

  @Test
  public void validatePeer_unresponsivePeer() {
    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestUtil.create(DeterministicEthScheduler.TimeoutPolicy.ALWAYS_TIMEOUT);
    final long blockNumber = 500;

    final PeerValidator validator = createValidator(blockNumber, 0);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, blockNumber);

    final CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), peer.getEthPeer());

    // Request should timeout immediately
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  public void validatePeer_requestBlockFromPeerBeingTested() {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final long blockNumber = 500;
    final Block block = gen.block(BlockOptions.create().setBlockNumber(blockNumber));

    final PeerValidator validator = createValidator(blockNumber, 0);

    final int peerCount = 1000;
    final List<RespondingEthPeer> otherPeers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, blockNumber))
            .limit(peerCount)
            .collect(Collectors.toList());
    final RespondingEthPeer targetPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, blockNumber);

    final CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), targetPeer.getEthPeer());

    assertThat(result).isNotDone();

    // Other peers should not receive request for target block
    for (final RespondingEthPeer otherPeer : otherPeers) {
      final AtomicBoolean blockRequestedForOtherPeer = respondToBlockRequest(otherPeer, block);
      assertThat(blockRequestedForOtherPeer).isFalse();
    }

    // Target peer should receive request for target block
    final AtomicBoolean blockRequested = respondToBlockRequest(targetPeer, block);
    assertThat(blockRequested).isTrue();
  }

  @Test
  public void canBeValidated() {
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestUtil.create(DeterministicEthScheduler.TimeoutPolicy.ALWAYS_TIMEOUT);
    final long blockNumber = 500;
    final long buffer = 10;

    final PeerValidator validator = createValidator(blockNumber, buffer);
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

  AtomicBoolean respondToBlockRequest(final RespondingEthPeer peer, final Block block) {
    final AtomicBoolean blockRequested = new AtomicBoolean(false);

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.targetedResponder(
            (cap, msg) -> {
              if (msg.getCode() != EthPV62.GET_BLOCK_HEADERS) {
                return false;
              }
              final GetBlockHeadersMessage headersRequest = GetBlockHeadersMessage.readFrom(msg);
              final boolean isTargetedBlockRequest =
                  headersRequest.blockNumber().isPresent()
                      && headersRequest.blockNumber().getAsLong() == block.getHeader().getNumber();
              if (isTargetedBlockRequest) {
                blockRequested.set(true);
              }
              return isTargetedBlockRequest;
            },
            (cap, msg) -> BlockHeadersMessage.create(block.getHeader()));

    // Respond
    peer.respond(responder);

    return blockRequested;
  }
}
