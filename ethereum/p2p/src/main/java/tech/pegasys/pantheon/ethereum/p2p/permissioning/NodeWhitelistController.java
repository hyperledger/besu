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
package tech.pegasys.pantheon.ethereum.p2p.permissioning;

import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.WhitelistFileSyncException;
import tech.pegasys.pantheon.ethereum.permissioning.WhitelistOperationResult;
import tech.pegasys.pantheon.ethereum.permissioning.WhitelistPersistor;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

public class NodeWhitelistController {

  private List<Peer> nodesWhitelist = new ArrayList<>();
  private final WhitelistPersistor whitelistPersistor;

  public NodeWhitelistController(final PermissioningConfiguration permissioningConfiguration) {
    this(
        permissioningConfiguration,
        new WhitelistPersistor(permissioningConfiguration.getConfigurationFilePath()));
  }

  public NodeWhitelistController(
      final PermissioningConfiguration configuration, final WhitelistPersistor whitelistPersistor) {
    this.whitelistPersistor = whitelistPersistor;
    if (configuration.isNodeWhitelistEnabled() && configuration.getNodeWhitelist() != null) {
      for (URI uri : configuration.getNodeWhitelist()) {
        nodesWhitelist.add(DefaultPeer.fromURI(uri));
      }
    }
  }

  public boolean addNode(final Peer node) {
    return nodesWhitelist.add(node);
  }

  private boolean removeNode(final Peer node) {
    return nodesWhitelist.remove(node);
  }

  public NodesWhitelistResult addNodes(final List<Peer> peers) {
    final NodesWhitelistResult inputValidationResult = validInput(peers);
    if (inputValidationResult.result() != WhitelistOperationResult.SUCCESS) {
      return inputValidationResult;
    }

    for (Peer peer : peers) {
      if (nodesWhitelist.contains(peer)) {
        return new NodesWhitelistResult(
            WhitelistOperationResult.ERROR_EXISTING_ENTRY,
            String.format("Specified peer: %s already exists in whitelist.", peer.getId()));
      }
    }

    final List<Peer> oldWhitelist = new ArrayList<>(this.nodesWhitelist);

    peers.forEach(this::addNode);
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

  private boolean peerListHasDuplicates(final List<Peer> peers) {
    return !peers.stream().allMatch(new HashSet<>()::add);
  }

  public NodesWhitelistResult removeNodes(final List<Peer> peers) {
    final NodesWhitelistResult inputValidationResult = validInput(peers);
    if (inputValidationResult.result() != WhitelistOperationResult.SUCCESS) {
      return inputValidationResult;
    }

    for (Peer peer : peers) {
      if (!(nodesWhitelist.contains(peer))) {
        return new NodesWhitelistResult(
            WhitelistOperationResult.ERROR_ABSENT_ENTRY,
            String.format("Specified peer: %s does not exist in whitelist.", peer.getId()));
      }
    }

    final List<Peer> oldWhitelist = new ArrayList<>(this.nodesWhitelist);

    peers.forEach(this::removeNode);
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

  private NodesWhitelistResult validInput(final List<Peer> peers) {
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

  private void verifyConfigurationFileState(final Collection<String> oldNodes)
      throws IOException, WhitelistFileSyncException {
    whitelistPersistor.verifyConfigFileMatchesState(
        WhitelistPersistor.WHITELIST_TYPE.NODES, oldNodes);
  }

  private void updateConfigurationFile(final Collection<String> nodes) throws IOException {
    whitelistPersistor.updateConfig(WhitelistPersistor.WHITELIST_TYPE.NODES, nodes);
  }

  private void revertState(final List<Peer> nodesWhitelist) {
    this.nodesWhitelist = nodesWhitelist;
  }

  private Collection<String> peerToEnodeURI(final Collection<Peer> peers) {
    return peers.parallelStream().map(Peer::getEnodeURI).collect(Collectors.toList());
  }

  public boolean isPermitted(final Peer node) {
    return nodesWhitelist.stream()
        .anyMatch(
            p -> {
              boolean idsMatch = node.getId().equals(p.getId());
              boolean hostsMatch = node.getEndpoint().getHost().equals(p.getEndpoint().getHost());
              boolean udpPortsMatch =
                  node.getEndpoint().getUdpPort() == p.getEndpoint().getUdpPort();
              boolean tcpPortsMatchIfPresent = true;
              if (node.getEndpoint().getTcpPort().isPresent()
                  && p.getEndpoint().getTcpPort().isPresent()) {
                tcpPortsMatchIfPresent =
                    node.getEndpoint().getTcpPort().getAsInt()
                        == p.getEndpoint().getTcpPort().getAsInt();
              }

              return idsMatch && hostsMatch && udpPortsMatch && tcpPortsMatchIfPresent;
            });
  }

  public List<Peer> getNodesWhitelist() {
    return nodesWhitelist;
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
