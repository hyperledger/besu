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
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.PeerRequest;
import org.hyperledger.besu.ethereum.eth.manager.RequestManager;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.task.SnapProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.NodeDataMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.SnapV1;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodesMessage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
@RunWith(MockitoJUnitRunner.class)
public class WorldStatePeerTrieNodeFinderTest {

  WorldStatePeerTrieNodeFinder worldStatePeerTrieNodeFinder;

  private final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();

  @Mock private Blockchain blockchain;
  private EthProtocolManager ethProtocolManager;
  private SnapProtocolManager snapProtocolManager;
  private EthPeers ethPeers;
  private final PeerRequest peerRequest = mock(PeerRequest.class);
  private final RequestManager.ResponseStream responseStream =
      mock(RequestManager.ResponseStream.class);

  @Before
  public void setup() throws Exception {
    ethProtocolManager = EthProtocolManagerTestUtil.create();
    ethPeers = ethProtocolManager.ethContext().getEthPeers();
    snapProtocolManager = SnapProtocolManagerTestUtil.create(ethPeers);
    worldStatePeerTrieNodeFinder =
        new WorldStatePeerTrieNodeFinder(
            ethProtocolManager.ethContext(), blockchain, new NoOpMetricsSystem());
  }

  private RespondingEthPeer.Responder respondToGetNodeDataRequest(
      final RespondingEthPeer peer, final Bytes32 nodeValue) {
    return RespondingEthPeer.targetedResponder(
        (cap, msg) -> {
          if (msg.getCode() != EthPV63.GET_NODE_DATA) {
            return false;
          }
          return true;
        },
        (cap, msg) -> NodeDataMessage.create(List.of(nodeValue)));
  }

  private RespondingEthPeer.Responder respondToGetTrieNodeRequest(
      final RespondingEthPeer peer, final Bytes32 nodeValue) {
    return RespondingEthPeer.targetedResponder(
        (cap, msg) -> {
          if (msg.getCode() != SnapV1.GET_TRIE_NODES) {
            return false;
          }
          return true;
        },
        (cap, msg) -> TrieNodesMessage.create(Optional.of(BigInteger.ZERO), List.of(nodeValue)));
  }

  @Test
  public void getAccountStateTrieNodeShouldReturnValueFromGetNodeDataRequest() {

    BlockHeader blockHeader = blockHeaderBuilder.number(1000).buildHeader();
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader);

    final Bytes32 nodeValue = Bytes32.random();
    final Bytes32 nodeHash = Hash.hash(nodeValue);

    final RespondingEthPeer targetPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, blockHeader.getNumber());

    var response =
        new Object() {
          Optional<Bytes> accountStateTrieNode = Optional.empty();
        };

    new Thread(
            () ->
                targetPeer.respondWhileOtherThreadsWork(
                    respondToGetNodeDataRequest(targetPeer, nodeValue),
                    () -> response.accountStateTrieNode.isEmpty()))
        .start();

    response.accountStateTrieNode =
        worldStatePeerTrieNodeFinder.getAccountStateTrieNode(Bytes.EMPTY, nodeHash);

    Assertions.assertThat(response.accountStateTrieNode).contains(nodeValue);
  }

  @Test
  public void getAccountStateTrieNodeShouldReturnValueFromGetTrieNodeRequest() {

    final BlockHeader blockHeader = blockHeaderBuilder.number(1000).buildHeader();
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader);

    final Bytes32 nodeValue = Bytes32.random();
    final Bytes32 nodeHash = Hash.hash(nodeValue);

    final RespondingEthPeer targetPeer =
        SnapProtocolManagerTestUtil.createPeer(
            ethProtocolManager, snapProtocolManager, blockHeader.getNumber());

    var response =
        new Object() {
          Optional<Bytes> accountStateTrieNode = Optional.empty();
        };

    new Thread(
            () ->
                targetPeer.respondWhileOtherThreadsWork(
                    respondToGetTrieNodeRequest(targetPeer, nodeValue),
                    () -> response.accountStateTrieNode.isEmpty()))
        .start();

    response.accountStateTrieNode =
        worldStatePeerTrieNodeFinder.getAccountStateTrieNode(Bytes.EMPTY, nodeHash);
    Assertions.assertThat(response.accountStateTrieNode).contains(nodeValue);
  }

  @Test
  public void getAccountStateTrieNodeShouldReturnEmptyWhenFoundNothing() {

    final BlockHeader blockHeader = blockHeaderBuilder.number(1000).buildHeader();
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader);

    final Bytes32 nodeValue = Bytes32.random();
    final Bytes32 nodeHash = Hash.hash(nodeValue);

    var response =
        new Object() {
          Optional<Bytes> accountStateTrieNode = Optional.empty();
        };

    response.accountStateTrieNode =
        worldStatePeerTrieNodeFinder.getAccountStateTrieNode(Bytes.EMPTY, nodeHash);
    Assertions.assertThat(response.accountStateTrieNode).isEmpty();
  }

  @Test
  public void getAccountStorageTrieNodeShouldReturnValueFromGetNodeDataRequest() {

    BlockHeader blockHeader = blockHeaderBuilder.number(1000).buildHeader();
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader);

    final Hash accountHash = Hash.wrap(Bytes32.random());
    final Bytes32 nodeValue = Bytes32.random();
    final Bytes32 nodeHash = Hash.hash(nodeValue);

    final RespondingEthPeer targetPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, blockHeader.getNumber());

    var response =
        new Object() {
          Optional<Bytes> accountStateTrieNode = Optional.empty();
        };

    new Thread(
            () ->
                targetPeer.respondWhileOtherThreadsWork(
                    respondToGetNodeDataRequest(targetPeer, nodeValue),
                    () -> response.accountStateTrieNode.isEmpty()))
        .start();

    response.accountStateTrieNode =
        worldStatePeerTrieNodeFinder.getAccountStorageTrieNode(accountHash, Bytes.EMPTY, nodeHash);

    Assertions.assertThat(response.accountStateTrieNode).contains(nodeValue);
  }

  @Test
  public void getAccountStorageTrieNodeShouldReturnValueFromGetTrieNodeRequest() {

    final BlockHeader blockHeader = blockHeaderBuilder.number(1000).buildHeader();
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader);

    final Hash accountHash = Hash.wrap(Bytes32.random());
    final Bytes32 nodeValue = Bytes32.random();
    final Bytes32 nodeHash = Hash.hash(nodeValue);

    final RespondingEthPeer targetPeer =
        SnapProtocolManagerTestUtil.createPeer(
            ethProtocolManager, snapProtocolManager, blockHeader.getNumber());

    var response =
        new Object() {
          Optional<Bytes> accountStateTrieNode = Optional.empty();
        };

    new Thread(
            () ->
                targetPeer.respondWhileOtherThreadsWork(
                    respondToGetTrieNodeRequest(targetPeer, nodeValue),
                    () -> response.accountStateTrieNode.isEmpty()))
        .start();

    response.accountStateTrieNode =
        worldStatePeerTrieNodeFinder.getAccountStorageTrieNode(accountHash, Bytes.EMPTY, nodeHash);
    Assertions.assertThat(response.accountStateTrieNode).contains(nodeValue);
  }

  @Test
  public void getAccountStorageTrieNodeShouldReturnEmptyWhenFoundNothing() {

    final BlockHeader blockHeader = blockHeaderBuilder.number(1000).buildHeader();
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader);

    final Hash accountHash = Hash.wrap(Bytes32.random());
    final Bytes32 nodeValue = Bytes32.random();
    final Bytes32 nodeHash = Hash.hash(nodeValue);

    var response =
        new Object() {
          Optional<Bytes> accountStateTrieNode = Optional.empty();
        };

    response.accountStateTrieNode =
        worldStatePeerTrieNodeFinder.getAccountStorageTrieNode(accountHash, Bytes.EMPTY, nodeHash);
    Assertions.assertThat(response.accountStateTrieNode).isEmpty();
  }
}
