/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.permissioning;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.permissioning.node.NodePermissioningProvider;
import org.hyperledger.besu.ethereum.permissioning.node.NodeWhitelistUpdatedEvent;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.util.Subscribers;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodeLocalConfigPermissioningController implements NodePermissioningProvider {

  private static final Logger LOG = LogManager.getLogger();

  private LocalPermissioningConfiguration configuration;
  private final List<EnodeURL> fixedNodes;
  private final BytesValue localNodeId;
  private final List<EnodeURL> nodesWhitelist = new ArrayList<>();
  private final WhitelistPersistor whitelistPersistor;
  private final Subscribers<Consumer<NodeWhitelistUpdatedEvent>> nodeWhitelistUpdatedObservers =
      Subscribers.create();

  private final Counter checkCounter;
  private final Counter checkCounterPermitted;
  private final Counter checkCounterUnpermitted;

  public NodeLocalConfigPermissioningController(
      final LocalPermissioningConfiguration permissioningConfiguration,
      final List<EnodeURL> fixedNodes,
      final BytesValue localNodeId,
      final MetricsSystem metricsSystem) {
    this(
        permissioningConfiguration,
        fixedNodes,
        localNodeId,
        new WhitelistPersistor(permissioningConfiguration.getNodePermissioningConfigFilePath()),
        metricsSystem);
  }

  public NodeLocalConfigPermissioningController(
      final LocalPermissioningConfiguration configuration,
      final List<EnodeURL> fixedNodes,
      final BytesValue localNodeId,
      final WhitelistPersistor whitelistPersistor,
      final MetricsSystem metricsSystem) {
    this.configuration = configuration;
    this.fixedNodes = fixedNodes;
    this.localNodeId = localNodeId;
    this.whitelistPersistor = whitelistPersistor;
    readNodesFromConfig(configuration);

    this.checkCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_local_check_count",
            "Number of times the node local permissioning provider has been checked");
    this.checkCounterPermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_local_check_count_permitted",
            "Number of times the node local permissioning provider has been checked and returned permitted");
    this.checkCounterUnpermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_local_check_count_unpermitted",
            "Number of times the node local permissioning provider has been checked and returned unpermitted");
  }

  private void readNodesFromConfig(final LocalPermissioningConfiguration configuration) {
    if (configuration.isNodeWhitelistEnabled() && configuration.getNodeWhitelist() != null) {
      for (URI uri : configuration.getNodeWhitelist()) {
        addNode(EnodeURL.fromString(uri.toString()));
      }
    }
  }

  public NodesWhitelistResult addNodes(final List<String> enodeURLs) {
    final NodesWhitelistResult inputValidationResult = validInput(enodeURLs);
    if (inputValidationResult.result() != WhitelistOperationResult.SUCCESS) {
      return inputValidationResult;
    }
    final List<EnodeURL> peers =
        enodeURLs.stream().map(EnodeURL::fromString).collect(Collectors.toList());

    for (EnodeURL peer : peers) {
      if (nodesWhitelist.contains(peer)) {
        return new NodesWhitelistResult(
            WhitelistOperationResult.ERROR_EXISTING_ENTRY,
            String.format("Specified peer: %s already exists in whitelist.", peer.getNodeId()));
      }
    }

    final List<EnodeURL> oldWhitelist = new ArrayList<>(this.nodesWhitelist);
    peers.forEach(this::addNode);
    notifyListUpdatedSubscribers(new NodeWhitelistUpdatedEvent(peers, Collections.emptyList()));

    final NodesWhitelistResult updateConfigFileResult = updateWhitelistInConfigFile(oldWhitelist);
    if (updateConfigFileResult.result() != WhitelistOperationResult.SUCCESS) {
      return updateConfigFileResult;
    }

    return new NodesWhitelistResult(WhitelistOperationResult.SUCCESS);
  }

  public boolean addNode(final EnodeURL enodeURL) {
    return nodesWhitelist.add(enodeURL);
  }

  public NodesWhitelistResult removeNodes(final List<String> enodeURLs) {
    final NodesWhitelistResult inputValidationResult = validInput(enodeURLs);
    if (inputValidationResult.result() != WhitelistOperationResult.SUCCESS) {
      return inputValidationResult;
    }
    final List<EnodeURL> peers =
        enodeURLs.stream().map(EnodeURL::fromString).collect(Collectors.toList());

    boolean anyBootnode = peers.stream().anyMatch(fixedNodes::contains);
    if (anyBootnode) {
      return new NodesWhitelistResult(WhitelistOperationResult.ERROR_FIXED_NODE_CANNOT_BE_REMOVED);
    }

    for (EnodeURL peer : peers) {
      if (!(nodesWhitelist.contains(peer))) {
        return new NodesWhitelistResult(
            WhitelistOperationResult.ERROR_ABSENT_ENTRY,
            String.format("Specified peer: %s does not exist in whitelist.", peer.getNodeId()));
      }
    }

    final List<EnodeURL> oldWhitelist = new ArrayList<>(this.nodesWhitelist);
    peers.forEach(this::removeNode);
    notifyListUpdatedSubscribers(new NodeWhitelistUpdatedEvent(Collections.emptyList(), peers));

    final NodesWhitelistResult updateConfigFileResult = updateWhitelistInConfigFile(oldWhitelist);
    if (updateConfigFileResult.result() != WhitelistOperationResult.SUCCESS) {
      return updateConfigFileResult;
    }

    return new NodesWhitelistResult(WhitelistOperationResult.SUCCESS);
  }

  private boolean removeNode(final EnodeURL enodeURL) {
    return nodesWhitelist.remove(enodeURL);
  }

  private NodesWhitelistResult updateWhitelistInConfigFile(final List<EnodeURL> oldWhitelist) {
    try {
      verifyConfigurationFileState(peerToEnodeURI(oldWhitelist));
      updateConfigurationFile(peerToEnodeURI(nodesWhitelist));
      verifyConfigurationFileState(peerToEnodeURI(nodesWhitelist));
    } catch (IOException e) {
      revertState(oldWhitelist);
      return new NodesWhitelistResult(WhitelistOperationResult.ERROR_WHITELIST_PERSIST_FAIL);
    } catch (WhitelistFileSyncException e) {
      return new NodesWhitelistResult(WhitelistOperationResult.ERROR_WHITELIST_FILE_SYNC);
    }

    return new NodesWhitelistResult(WhitelistOperationResult.SUCCESS);
  }

  private NodesWhitelistResult validInput(final List<String> peers) {
    if (peers == null || peers.isEmpty()) {
      return new NodesWhitelistResult(
          WhitelistOperationResult.ERROR_EMPTY_ENTRY, String.format("Null/empty peers list"));
    }

    if (peerListHasDuplicates(peers)) {
      return new NodesWhitelistResult(
          WhitelistOperationResult.ERROR_DUPLICATED_ENTRY,
          String.format("Specified peer list contains duplicates"));
    }

    return new NodesWhitelistResult(WhitelistOperationResult.SUCCESS);
  }

  private boolean peerListHasDuplicates(final List<String> peers) {
    return !peers.stream().allMatch(new HashSet<>()::add);
  }

  private void verifyConfigurationFileState(final Collection<String> oldNodes)
      throws IOException, WhitelistFileSyncException {
    whitelistPersistor.verifyConfigFileMatchesState(
        WhitelistPersistor.WHITELIST_TYPE.NODES, oldNodes);
  }

  private void updateConfigurationFile(final Collection<String> nodes) throws IOException {
    whitelistPersistor.updateConfig(WhitelistPersistor.WHITELIST_TYPE.NODES, nodes);
  }

  private void revertState(final List<EnodeURL> nodesWhitelist) {
    this.nodesWhitelist.clear();
    this.nodesWhitelist.addAll(nodesWhitelist);
  }

  private Collection<String> peerToEnodeURI(final Collection<EnodeURL> peers) {
    return peers.parallelStream().map(EnodeURL::toString).collect(Collectors.toList());
  }

  public boolean isPermitted(final String enodeURL) {
    return isPermitted(EnodeURL.fromString(enodeURL));
  }

  public boolean isPermitted(final EnodeURL node) {
    if (Objects.equals(localNodeId, node.getNodeId())) {
      return true;
    }
    return nodesWhitelist.stream().anyMatch(p -> EnodeURL.sameListeningEndpoint(p, node));
  }

  public List<String> getNodesWhitelist() {
    return nodesWhitelist.stream().map(Object::toString).collect(Collectors.toList());
  }

  public synchronized void reload() throws RuntimeException {
    final List<EnodeURL> currentAccountsList = new ArrayList<>(nodesWhitelist);
    nodesWhitelist.clear();

    try {
      final LocalPermissioningConfiguration updatedConfig =
          PermissioningConfigurationBuilder.permissioningConfiguration(
              configuration.isNodeWhitelistEnabled(),
              configuration.getNodePermissioningConfigFilePath(),
              configuration.isAccountWhitelistEnabled(),
              configuration.getAccountPermissioningConfigFilePath());

      readNodesFromConfig(updatedConfig);
      configuration = updatedConfig;

      createNodeWhitelistModifiedEventAfterReload(currentAccountsList, nodesWhitelist);
    } catch (Exception e) {
      LOG.warn(
          "Error reloading permissions file. In-memory whitelisted nodes will be reverted to previous valid configuration. "
              + "Details: {}",
          e.getMessage());
      nodesWhitelist.clear();
      nodesWhitelist.addAll(currentAccountsList);
      throw new RuntimeException(e);
    }
  }

  private void createNodeWhitelistModifiedEventAfterReload(
      final List<EnodeURL> previousNodeWhitelist, final List<EnodeURL> currentNodesList) {
    final List<EnodeURL> removedNodes =
        previousNodeWhitelist.stream()
            .filter(n -> !currentNodesList.contains(n))
            .collect(Collectors.toList());

    final List<EnodeURL> addedNodes =
        currentNodesList.stream()
            .filter(n -> !previousNodeWhitelist.contains(n))
            .collect(Collectors.toList());

    if (!removedNodes.isEmpty() || !addedNodes.isEmpty()) {
      notifyListUpdatedSubscribers(new NodeWhitelistUpdatedEvent(addedNodes, removedNodes));
    }
  }

  public long subscribeToListUpdatedEvent(final Consumer<NodeWhitelistUpdatedEvent> subscriber) {
    return nodeWhitelistUpdatedObservers.subscribe(subscriber);
  }

  private void notifyListUpdatedSubscribers(final NodeWhitelistUpdatedEvent event) {
    LOG.trace(
        "Sending NodeWhitelistUpdatedEvent (added: {}, removed {})",
        event.getAddedNodes().size(),
        event.getRemovedNodes().size());

    nodeWhitelistUpdatedObservers.forEach(c -> c.accept(event));
  }

  public static class NodesWhitelistResult {
    private final WhitelistOperationResult result;
    private final Optional<String> message;

    NodesWhitelistResult(final WhitelistOperationResult result, final String message) {
      this.result = result;
      this.message = Optional.of(message);
    }

    @VisibleForTesting
    public NodesWhitelistResult(final WhitelistOperationResult result) {
      this.result = result;
      this.message = Optional.empty();
    }

    public WhitelistOperationResult result() {
      return result;
    }

    public Optional<String> message() {
      return message;
    }
  }

  @Override
  public boolean isPermitted(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    this.checkCounter.inc();
    if (isPermitted(sourceEnode) && isPermitted(destinationEnode)) {
      this.checkCounterPermitted.inc();
      return true;
    } else {
      this.checkCounterUnpermitted.inc();
      return false;
    }
  }
}
