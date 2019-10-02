/*
 *
 *  * Copyright ConsenSys AG.
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
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.DeterministicEthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public class RequiredBlocksPeerValidatorTest {

  private BytesValue goodExtraData =
      BytesValue.fromHexString("0x100f0e0d0c0b0a09080706050403020100");
  private BytesValue badExtraData = BytesValue.fromHexString("0x1009080706050403020100");

  @Test
  public void validatePeer_responsivePeerWithRequiredBlock() {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final long requiredBlockNumber = 500;
    final Block requiredBlock =
        gen.block(
            BlockOptions.create().setBlockNumber(requiredBlockNumber).setExtraData(goodExtraData));

    final PeerValidator validator =
        new RequiredBlocksPeerValidator(
            MainnetProtocolSchedule.create(),
            new NoOpMetricsSystem(),
            requiredBlockNumber,
            requiredBlock.getHash(),
            0);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, requiredBlockNumber);

    final CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), peer.getEthPeer());

    assertThat(result).isNotDone();

    // Send response for dao block
    final AtomicBoolean requiredBlockRequested = respondToRequiredBlockRequest(peer, requiredBlock);

    assertThat(requiredBlockRequested).isTrue();
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(true);
  }

  @Test
  public void validatePeer_responsivePeerWithBadRequiredBlock() {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final long requiredBlockNumber = 500;
    final Block requiredBlock =
        gen.block(
            BlockOptions.create().setBlockNumber(requiredBlockNumber).setExtraData(badExtraData));

    final PeerValidator validator =
        new RequiredBlocksPeerValidator(
            MainnetProtocolSchedule.create(),
            new NoOpMetricsSystem(),
            requiredBlockNumber,
            Hash.ZERO,
            0);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, requiredBlockNumber);

    final CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), peer.getEthPeer());

    assertThat(result).isNotDone();

    // Send response for dao block
    final AtomicBoolean requiredBlockRequested = respondToRequiredBlockRequest(peer, requiredBlock);

    assertThat(requiredBlockRequested).isTrue();
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  public void validatePeer_unresponsivePeer() {
    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestUtil.create(DeterministicEthScheduler.TimeoutPolicy.ALWAYS_TIMEOUT);
    final long requiredBlockNumber = 500;

    final PeerValidator validator =
        new RequiredBlocksPeerValidator(
            MainnetProtocolSchedule.create(),
            new NoOpMetricsSystem(),
            requiredBlockNumber,
            Hash.ZERO,
            0);

    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, requiredBlockNumber);

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
    final long requiredBlockNumber = 500;
    final Block requiredBlock =
        gen.block(
            BlockOptions.create().setBlockNumber(requiredBlockNumber).setExtraData(goodExtraData));

    final PeerValidator validator =
        new RequiredBlocksPeerValidator(
            MainnetProtocolSchedule.create(),
            new NoOpMetricsSystem(),
            requiredBlockNumber,
            Hash.ZERO,
            0);

    final int peerCount = 1000;
    final List<RespondingEthPeer> otherPeers =
        Stream.generate(
                () ->
                    EthProtocolManagerTestUtil.createPeer(ethProtocolManager, requiredBlockNumber))
            .limit(peerCount)
            .collect(Collectors.toList());
    final RespondingEthPeer targetPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, requiredBlockNumber);

    final CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), targetPeer.getEthPeer());

    assertThat(result).isNotDone();

    // Other peers should not receive request for dao block
    for (final RespondingEthPeer otherPeer : otherPeers) {
      final AtomicBoolean requiredBlockRequestedForOtherPeer =
          respondToRequiredBlockRequest(otherPeer, requiredBlock);
      assertThat(requiredBlockRequestedForOtherPeer).isFalse();
    }

    // Target peer should receive request for dao block
    final AtomicBoolean requiredBlockRequested =
        respondToRequiredBlockRequest(targetPeer, requiredBlock);
    assertThat(requiredBlockRequested).isTrue();
  }

  @Test
  public void canBeValidated() {
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestUtil.create(DeterministicEthScheduler.TimeoutPolicy.ALWAYS_TIMEOUT);
    final long requiredBlockNumber = 500;
    final long buffer = 10;

    final PeerValidator validator =
        new RequiredBlocksPeerValidator(
            MainnetProtocolSchedule.create(),
            new NoOpMetricsSystem(),
            requiredBlockNumber,
            Hash.ZERO,
            buffer);

    final EthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0).getEthPeer();

    peer.chainState().update(gen.hash(), requiredBlockNumber - 10);
    assertThat(validator.canBeValidated(peer)).isFalse();

    peer.chainState().update(gen.hash(), requiredBlockNumber);
    assertThat(validator.canBeValidated(peer)).isFalse();

    peer.chainState().update(gen.hash(), requiredBlockNumber + buffer - 1);
    assertThat(validator.canBeValidated(peer)).isFalse();

    peer.chainState().update(gen.hash(), requiredBlockNumber + buffer);
    assertThat(validator.canBeValidated(peer)).isTrue();

    peer.chainState().update(gen.hash(), requiredBlockNumber + buffer + 10);
    assertThat(validator.canBeValidated(peer)).isTrue();
  }

  private AtomicBoolean respondToRequiredBlockRequest(
      final RespondingEthPeer peer, final Block requiredBlock) {
    final AtomicBoolean requiredBlockRequested = new AtomicBoolean(false);

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.targetedResponder(
            (cap, msg) -> {
              if (msg.getCode() != EthPV62.GET_BLOCK_HEADERS) {
                return false;
              }
              final GetBlockHeadersMessage headersRequest = GetBlockHeadersMessage.readFrom(msg);
              final boolean isRequiredBlockRequest =
                  headersRequest.blockNumber().isPresent()
                      && headersRequest.blockNumber().getAsLong()
                          == requiredBlock.getHeader().getNumber();
              if (isRequiredBlockRequest) {
                requiredBlockRequested.set(true);
              }
              return isRequiredBlockRequest;
            },
            (cap, msg) -> BlockHeadersMessage.create(requiredBlock.getHeader()));

    // Respond
    peer.respond(responder);

    return requiredBlockRequested;
  }
}
