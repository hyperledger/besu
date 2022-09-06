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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.snap.RetryingGetTrieNodeFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.EthTask;
import org.hyperledger.besu.ethereum.eth.manager.task.RetryingGetNodeDataFromPeerTask;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.worldstate.PeerTrieNodeFinder;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is used to retrieve missing nodes in the trie by querying the peers */
public class WorldStatePeerTrieNodeFinder implements PeerTrieNodeFinder {

  private static final Logger LOG = LoggerFactory.getLogger(WorldStatePeerTrieNodeFinder.class);

  private final Cache<Bytes32, Bytes> foundNodes =
      CacheBuilder.newBuilder().maximumSize(10_000).expireAfterWrite(5, TimeUnit.MINUTES).build();

  private static final long TIMEOUT_SECONDS = 1;

  final EthContext ethContext;
  final Blockchain blockchain;
  final MetricsSystem metricsSystem;

  public WorldStatePeerTrieNodeFinder(
      final EthContext ethContext, final Blockchain blockchain, final MetricsSystem metricsSystem) {
    this.ethContext = ethContext;
    this.blockchain = blockchain;
    this.metricsSystem = metricsSystem;
  }

  @Override
  public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    Optional<Bytes> cachedValue = Optional.ofNullable(foundNodes.getIfPresent(nodeHash));
    if (cachedValue.isPresent()) {
      return cachedValue;
    }
    final Optional<Bytes> response =
        findByGetNodeData(Hash.wrap(nodeHash))
            .or(() -> findByGetTrieNodeData(Hash.wrap(nodeHash), Optional.empty(), location));
    response.ifPresent(
        bytes -> {
          LOG.debug(
              "Fixed missing account state trie node for location {} and hash {}",
              location,
              nodeHash);
          foundNodes.put(nodeHash, bytes);
        });
    return response;
  }

  @Override
  public Optional<Bytes> getAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
    Optional<Bytes> cachedValue = Optional.ofNullable(foundNodes.getIfPresent(nodeHash));
    if (cachedValue.isPresent()) {
      return cachedValue;
    }
    final Optional<Bytes> response =
        findByGetNodeData(Hash.wrap(nodeHash))
            .or(
                () ->
                    findByGetTrieNodeData(Hash.wrap(nodeHash), Optional.of(accountHash), location));
    response.ifPresent(
        bytes -> {
          LOG.debug(
              "Fixed missing storage state trie node for location {} and hash {}",
              location,
              nodeHash);
          foundNodes.put(nodeHash, bytes);
        });
    return response;
  }

  public Optional<Bytes> findByGetNodeData(final Hash nodeHash) {
    final BlockHeader chainHead = blockchain.getChainHeadHeader();
    final RetryingGetNodeDataFromPeerTask retryingGetNodeDataFromPeerTask =
        RetryingGetNodeDataFromPeerTask.forHashes(
            ethContext, List.of(nodeHash), chainHead.getNumber(), metricsSystem);
    try {
      final Map<Hash, Bytes> response =
          retryingGetNodeDataFromPeerTask.run().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (response.containsKey(nodeHash)) {
        LOG.debug("Found node {} with getNodeData request", nodeHash);
        return Optional.of(response.get(nodeHash));
      } else {
        LOG.debug("Found invalid node {} with getNodeData request", nodeHash);
      }
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      LOG.debug("Error when trying to find node {} with getNodeData request", nodeHash);
    }
    return Optional.empty();
  }

  public Optional<Bytes> findByGetTrieNodeData(
      final Hash nodeHash, final Optional<Bytes32> accountHash, final Bytes location) {
    final BlockHeader chainHead = blockchain.getChainHeadHeader();
    final Map<Bytes, List<Bytes>> request = new HashMap<>();
    if (accountHash.isPresent()) {
      request.put(accountHash.get(), List.of(CompactEncoding.encode(location)));
    } else {
      request.put(CompactEncoding.encode(location), new ArrayList<>());
    }
    final Bytes path = CompactEncoding.encode(location);
    final EthTask<Map<Bytes, Bytes>> getTrieNodeFromPeerTask =
        RetryingGetTrieNodeFromPeerTask.forTrieNodes(ethContext, request, chainHead, metricsSystem);
    try {
      final Map<Bytes, Bytes> response =
          getTrieNodeFromPeerTask.run().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      final Bytes nodeValue =
          response.get(Bytes.concatenate(accountHash.map(Bytes::wrap).orElse(Bytes.EMPTY), path));
      if (nodeValue != null && Hash.hash(nodeValue).equals(nodeHash)) {
        LOG.debug("Found node {} with getTrieNode request", nodeHash);
        return Optional.of(nodeValue);
      } else {
        LOG.debug("Found invalid node {} with getTrieNode request", nodeHash);
      }
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      LOG.debug("Error when trying to find node {} with getTrieNode request", nodeHash);
    }
    return Optional.empty();
  }
}
