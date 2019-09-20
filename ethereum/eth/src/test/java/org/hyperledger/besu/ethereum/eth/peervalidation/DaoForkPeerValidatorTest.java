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
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

public class DaoForkPeerValidatorTest {

  @Test
  public void validatePeer_responsivePeerOnRightSideOfFork() {
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    BlockDataGenerator gen = new BlockDataGenerator(1);
    long daoBlockNumber = 500;
    Block daoBlock =
        gen.block(
            BlockOptions.create()
                .setBlockNumber(daoBlockNumber)
                .setExtraData(MainnetBlockHeaderValidator.DAO_EXTRA_DATA));

    PeerValidator validator =
        new DaoForkPeerValidator(
            MainnetProtocolSchedule.create(), new NoOpMetricsSystem(), daoBlockNumber, 0);

    RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, daoBlockNumber);

    CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), peer.getEthPeer());

    assertThat(result).isNotDone();

    // Send response for dao block
    AtomicBoolean daoBlockRequested = respondToDaoBlockRequest(peer, daoBlock);

    assertThat(daoBlockRequested).isTrue();
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(true);
  }

  @Test
  public void validatePeer_responsivePeerOnWrongSideOfFork() {
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    BlockDataGenerator gen = new BlockDataGenerator(1);
    long daoBlockNumber = 500;
    Block daoBlock =
        gen.block(
            BlockOptions.create().setBlockNumber(daoBlockNumber).setExtraData(BytesValue.EMPTY));

    PeerValidator validator =
        new DaoForkPeerValidator(
            MainnetProtocolSchedule.create(), new NoOpMetricsSystem(), daoBlockNumber, 0);

    RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, daoBlockNumber);

    CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), peer.getEthPeer());

    assertThat(result).isNotDone();

    // Send response for dao block
    AtomicBoolean daoBlockRequested = respondToDaoBlockRequest(peer, daoBlock);

    assertThat(daoBlockRequested).isTrue();
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  public void validatePeer_unresponsivePeer() {
    EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestUtil.create(DeterministicEthScheduler.TimeoutPolicy.ALWAYS);
    long daoBlockNumber = 500;

    PeerValidator validator =
        new DaoForkPeerValidator(
            MainnetProtocolSchedule.create(), new NoOpMetricsSystem(), daoBlockNumber, 0);

    RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, daoBlockNumber);

    CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), peer.getEthPeer());

    // Request should timeout immediately
    assertThat(result).isDone();
    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  public void validatePeer_requestBlockFromPeerBeingTested() {
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    BlockDataGenerator gen = new BlockDataGenerator(1);
    long daoBlockNumber = 500;
    Block daoBlock =
        gen.block(
            BlockOptions.create()
                .setBlockNumber(daoBlockNumber)
                .setExtraData(MainnetBlockHeaderValidator.DAO_EXTRA_DATA));

    PeerValidator validator =
        new DaoForkPeerValidator(
            MainnetProtocolSchedule.create(), new NoOpMetricsSystem(), daoBlockNumber, 0);

    int peerCount = 1000;
    List<RespondingEthPeer> otherPeers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, daoBlockNumber))
            .limit(peerCount)
            .collect(Collectors.toList());
    RespondingEthPeer targetPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, daoBlockNumber);

    CompletableFuture<Boolean> result =
        validator.validatePeer(ethProtocolManager.ethContext(), targetPeer.getEthPeer());

    assertThat(result).isNotDone();

    // Other peers should not receive request for dao block
    for (RespondingEthPeer otherPeer : otherPeers) {
      AtomicBoolean daoBlockRequestedForOtherPeer = respondToDaoBlockRequest(otherPeer, daoBlock);
      assertThat(daoBlockRequestedForOtherPeer).isFalse();
    }

    // Target peer should receive request for dao block
    final AtomicBoolean daoBlockRequested = respondToDaoBlockRequest(targetPeer, daoBlock);
    assertThat(daoBlockRequested).isTrue();
  }

  @Test
  public void canBeValidated() {
    BlockDataGenerator gen = new BlockDataGenerator(1);
    EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestUtil.create(DeterministicEthScheduler.TimeoutPolicy.ALWAYS);
    long daoBlockNumber = 500;
    long buffer = 10;

    PeerValidator validator =
        new DaoForkPeerValidator(
            MainnetProtocolSchedule.create(), new NoOpMetricsSystem(), daoBlockNumber, buffer);

    EthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager, 0).getEthPeer();

    peer.chainState().update(gen.hash(), daoBlockNumber - 10);
    assertThat(validator.canBeValidated(peer)).isFalse();

    peer.chainState().update(gen.hash(), daoBlockNumber);
    assertThat(validator.canBeValidated(peer)).isFalse();

    peer.chainState().update(gen.hash(), daoBlockNumber + buffer - 1);
    assertThat(validator.canBeValidated(peer)).isFalse();

    peer.chainState().update(gen.hash(), daoBlockNumber + buffer);
    assertThat(validator.canBeValidated(peer)).isTrue();

    peer.chainState().update(gen.hash(), daoBlockNumber + buffer + 10);
    assertThat(validator.canBeValidated(peer)).isTrue();
  }

  private AtomicBoolean respondToDaoBlockRequest(
      final RespondingEthPeer peer, final Block daoBlock) {
    AtomicBoolean daoBlockRequested = new AtomicBoolean(false);

    RespondingEthPeer.Responder responder =
        RespondingEthPeer.targetedResponder(
            (cap, msg) -> {
              if (msg.getCode() != EthPV62.GET_BLOCK_HEADERS) {
                return false;
              }
              GetBlockHeadersMessage headersRequest = GetBlockHeadersMessage.readFrom(msg);
              boolean isDaoBlockRequest =
                  headersRequest.blockNumber().isPresent()
                      && headersRequest.blockNumber().getAsLong()
                          == daoBlock.getHeader().getNumber();
              if (isDaoBlockRequest) {
                daoBlockRequested.set(true);
              }
              return isDaoBlockRequest;
            },
            (cap, msg) -> BlockHeadersMessage.create(daoBlock.getHeader()));

    // Respond
    peer.respond(responder);

    return daoBlockRequested;
  }
}
