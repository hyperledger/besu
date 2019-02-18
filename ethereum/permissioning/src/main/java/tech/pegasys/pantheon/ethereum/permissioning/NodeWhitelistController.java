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
package tech.pegasys.pantheon.ethereum.permissioning;

import tech.pegasys.pantheon.ethereum.permissioning.node.NodeWhitelistUpdatedEvent;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodeWhitelistController {

  private static final Logger LOG = LogManager.getLogger();

  private PermissioningConfiguration configuration;
  private List<EnodeURL> nodesWhitelist = new ArrayList<>();
  private final WhitelistPersistor whitelistPersistor;
  private final Subscribers<Consumer<NodeWhitelistUpdatedEvent>> nodeWhitelistUpdatedObservers =
      new Subscribers<>();

  public NodeWhitelistController(final PermissioningConfiguration permissioningConfiguration) {
    this(
        permissioningConfiguration,
        new WhitelistPersistor(permissioningConfiguration.getConfigurationFilePath()));
  }

  public NodeWhitelistController(
      final PermissioningConfiguration configuration, final WhitelistPersistor whitelistPersistor) {
    this.configuration = configuration;
    this.whitelistPersistor = whitelistPersistor;
    readNodesFromConfig(configuration);
  }

  private void readNodesFromConfig(final PermissioningConfiguration configuration) {
    if (configuration.isNodeWhitelistEnabled() && configuration.getNodeWhitelist() != null) {
      for (URI uri : configuration.getNodeWhitelist()) {
        nodesWhitelist.add(new EnodeURL(uri.toString()));
      }
    }
  }

  public NodesWhitelistResult addNodes(final List<String> enodeURLs) {
    final NodesWhitelistResult inputValidationResult = validInput(enodeURLs);
    if (inputValidationResult.result() != WhitelistOperationResult.SUCCESS) {
      return inputValidationResult;
    }
    final List<EnodeURL> peers = enodeURLs.stream().map(EnodeURL::new).collect(Collectors.toList());

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

  private boolean addNode(final EnodeURL enodeURL) {
    return nodesWhitelist.add(enodeURL);
  }

  public NodesWhitelistResult removeNodes(final List<String> enodeURLs) {
    final NodesWhitelistResult inputValidationResult = validInput(enodeURLs);
    if (inputValidationResult.result() != WhitelistOperationResult.SUCCESS) {
      return inputValidationResult;
    }
    final List<EnodeURL> peers = enodeURLs.stream().map(EnodeURL::new).collect(Collectors.toList());

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
    this.nodesWhitelist = nodesWhitelist;
  }

  private Collection<String> peerToEnodeURI(final Collection<EnodeURL> peers) {
    return peers.parallelStream().map(EnodeURL::toString).collect(Collectors.toList());
  }

  public boolean isPermitted(final String enodeURL) {
    return isPermitted(new EnodeURL(enodeURL));
  }

  private boolean isPermitted(final EnodeURL node) {
    return nodesWhitelist.stream()
        .anyMatch(
            p -> {
              boolean idsMatch = node.getNodeId().equals(p.getNodeId());
              boolean hostsMatch = node.getIp().equals(p.getIp());
              boolean udpPortsMatch = node.getListeningPort().equals(p.getListeningPort());
              boolean tcpPortsMatchIfPresent = true;
              if (node.getDiscoveryPort().isPresent() && p.getDiscoveryPort().isPresent()) {
                tcpPortsMatchIfPresent =
                    node.getDiscoveryPort().getAsInt() == p.getDiscoveryPort().getAsInt();
              }

              return idsMatch && hostsMatch && udpPortsMatch && tcpPortsMatchIfPresent;
            });
  }

  public List<String> getNodesWhitelist() {
    return nodesWhitelist.stream().map(Object::toString).collect(Collectors.toList());
  }

  public synchronized void reload() throws RuntimeException {
    final List<EnodeURL> currentAccountsList = new ArrayList<>(nodesWhitelist);
    nodesWhitelist.clear();

    try {
      final PermissioningConfiguration updatedConfig =
          PermissioningConfigurationBuilder.permissioningConfigurationFromToml(
              configuration.getConfigurationFilePath(),
              configuration.isNodeWhitelistEnabled(),
              configuration.isAccountWhitelistEnabled());

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

    NodesWhitelistResult(final WhitelistOperationResult fail, final String message) {
      this.result = fail;
      this.message = Optional.of(message);
    }

    @VisibleForTesting
    public NodesWhitelistResult(final WhitelistOperationResult success) {
      this.result = success;
      this.message = Optional.empty();
    }

    public WhitelistOperationResult result() {
      return result;
    }

    public Optional<String> message() {
      return message;
    }
  }
}
