/*
 *
 *  * Copyright Hyperledger Besu Contributors.
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

package org.hyperledger.besu.ethereum.eth.sync.snapsync.request;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.TrieNodeDecoder;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class NodeDeletionProcessor {
  private static final Logger LOG = getLogger(NodeDeletionProcessor.class);

  private final BonsaiWorldStateKeyValueStorage worldStateStorage;
  private final BonsaiWorldStateKeyValueStorage.Updater updater;

  private final static AtomicInteger totalDeleted = new AtomicInteger(0);

  public NodeDeletionProcessor(
          final WorldStateStorage worldStateStorage, final WorldStateStorage.Updater updater) {

    if (!(worldStateStorage instanceof BonsaiWorldStateKeyValueStorage)) {
      throw new RuntimeException(
              "NodeDeletionManager only works with BonsaiWorldStateKeyValueStorage");
    }
    this.worldStateStorage = (BonsaiWorldStateKeyValueStorage) worldStateStorage;
    if (!(updater instanceof BonsaiWorldStateKeyValueStorage.Updater)) {
      throw new RuntimeException(
              "NodeDeletionManager only works with BonsaiWorldStateKeyValueStorage.Updater");
    }
    this.updater = (BonsaiWorldStateKeyValueStorage.Updater) updater;
  }

  public void startFromStorageNode(
          final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes data) {

    final Node<Bytes> node = TrieNodeDecoder.decode(location, data);
    BonsaiStorageNode bonsaiNode;
    if (node.isReferencedByHash()){
      bonsaiNode = new BonsaiStorageInnerNode(accountHash, location, nodeHash, node.getChildren());
    } else {
      bonsaiNode = new BonsaiStorageLeafNode(accountHash, location, nodeHash, node);
    }
    final int deleted = deletePotentialOldChildren(bonsaiNode);
    if (deleted > 0) {
      LOG.info("Deleted {} nodes (total {}) for account {} on location {}",deleted, totalDeleted.addAndGet(deleted),accountHash, location);
    }
  }

  public void startFromAccountNode(final Bytes location, final Bytes32 nodeHash, final Bytes data) {
    final Node<Bytes> node = TrieNodeDecoder.decode(location, data);
    BonsaiNode bonsaiNode;
    if (node.isReferencedByHash()){
      bonsaiNode = new BonsaiAccountInnerNode( location, nodeHash, node.getChildren());
    } else {
      bonsaiNode = new BonsaiAccountLeafNode(location, nodeHash, node);
    }
    final int deleted = deletePotentialOldChildren(bonsaiNode);
    if (deleted > 0) {
      LOG.info("Deleted {} nodes (total {}) on location {}",deleted, totalDeleted.addAndGet(deleted), location);
    }
  }

  public int deletePotentialOldChildren(final BonsaiNode newNode) {
    return retrieveStoredAccountNode(newNode.getLocation())
            .stream().mapToInt(
                    oldNode -> compareChildrenAndDeleteOldOnes(oldNode, newNode)).sum();
  }

  public int compareChildrenAndDeleteOldOnes(final BonsaiNode oldNode, final BonsaiNode newNode) {
    return oldNode.findChildrenToDelete(newNode).stream().mapToInt(node -> node.delete(updater)).sum();
  }

  public Optional<BonsaiNode> retrieveStoredAccountNode(
          final Bytes location) {
    return worldStateStorage
            .getAccountStateTrieNode(location)
            .map(oldData -> {
              final Node<Bytes> node = TrieNodeDecoder.decode(location, oldData);
              if (node.isReferencedByHash()){
                return new BonsaiAccountInnerNode(location, Hash.hash(oldData), node.getChildren());
              } else {
                return new BonsaiAccountLeafNode(location,Hash.hash(oldData),node);
              }
            });
  }

  private Optional<BonsaiStorageNode> retrieveStoredRootStorageNode(
          final Hash accountHash) {
    return retrieveStoredStorageNode(accountHash, Bytes.EMPTY);
  }

  public Optional<BonsaiStorageNode> retrieveStoredStorageNode(
          final Hash accountHash, final Bytes location) {
    return worldStateStorage
            .getAccountStorageTrieNode(accountHash, location)
            .map(oldData -> {
              final Node<Bytes> node = TrieNodeDecoder.decode(location, oldData);
              if (node.isReferencedByHash()){
                return new BonsaiStorageInnerNode(accountHash, location, Hash.hash(oldData), node.getChildren());

              } else {
                return new BonsaiStorageLeafNode(accountHash, location, Hash.hash(oldData), node);
              }
            });
  }

  public interface BonsaiNode {
    List<Node<Bytes>> getNodes();

    Bytes getLocation();

    Bytes32 getNodeHash();

    default List<BonsaiNode> getChildren(){
      return Collections.emptyList();
    }

    int delete(BonsaiWorldStateKeyValueStorage.Updater updater);

    default boolean nodeIsHashReferencedDescendant(final Node<Bytes> node) {
      return !Objects.equals(node.getHash(), getNodeHash()) && node.isReferencedByHash();
    }
    default List<BonsaiNode> findChildrenToDelete(final BonsaiNode newNode){
      return Collections.emptyList();
    }
  }

  public abstract static class AbstractBonsaiNode implements BonsaiNode {
    private final Bytes location;
    private final Bytes32 nodeHash;
    private final List<Node<Bytes>> nodes;

    protected AbstractBonsaiNode(final Bytes location, final Bytes32 nodeHash, final List<Node<Bytes>> nodes) {
      this.location = location;
      this.nodeHash = nodeHash;
      this.nodes = nodes;
    }

    @Override
    public Bytes getLocation() {
      return location;
    }

    @Override
    public Bytes32 getNodeHash() {
      return nodeHash;
    }

    @NotNull
    @Override
    public List<Node<Bytes>> getNodes() {
      return nodes;
    }
  }

  public interface BonsaiInnerNode extends BonsaiNode {
    Function<Bytes, Optional<? extends BonsaiNode>> getNodeLocator();
    @Override
    default List<BonsaiNode> findChildrenToDelete(final BonsaiNode newNode ) {
      List<BonsaiNode> childrenToDelete = new ArrayList<>();
      final List<Node<Bytes>> oldChildren = getNodes();
      final List<Node<Bytes>> newChildren = newNode.getNodes();
      int oldChildIndex = 0;
      int newChildIndex = 0;
      while (oldChildIndex < oldChildren.size()) {
        final Node<Bytes> oldChild = oldChildren.get(oldChildIndex);
        if (newChildIndex == newChildren.size()) {
          if (!(oldChild instanceof NullNode)) {
            getNodeLocator().apply(oldChild.getLocation().orElseThrow()).ifPresent(childrenToDelete::add);
          }
          oldChildIndex++;
          continue;
        }
        final Node<Bytes> newChild = newChildren.get(newChildIndex);
        if (oldChild.getLocation().equals(newChild.getLocation())) {
          oldChildIndex++;
          newChildIndex++;
        } else if (oldChild instanceof NullNode) {
          oldChildIndex++;
        } else if (newChild instanceof NullNode) {
          newChildIndex++;
        } else {
          final Bytes oldChildLocation = oldChild.getLocation().orElseThrow();
          final Bytes newChildLocation = newChild.getLocation().orElseThrow();
          if (oldChildLocation.compareTo(newChildLocation) < 0) {
            //old child exists, but new child does not
            getNodeLocator().apply(oldChild.getLocation().orElseThrow()).ifPresent(childrenToDelete::add);

            oldChildIndex++;
          } else {
            //new child exists, but old child does not
            newChildIndex++;
          }
        }
      }
      return childrenToDelete;
    }
  }

  public abstract static class BonsaiStorageNode extends AbstractBonsaiNode {
    private final Hash accountHash;

    protected BonsaiStorageNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final List<Node<Bytes>> nodes) {
      super(location, nodeHash, nodes);
      this.accountHash = accountHash;
    }

    public Hash getAccountHash() {
      return accountHash;
    }
  }

  public class BonsaiAccountInnerNode extends AbstractBonsaiNode implements BonsaiInnerNode{

    protected BonsaiAccountInnerNode(
        final Bytes location, final Bytes32 nodeHash, final List<Node<Bytes>> nodes) {
      super(location, nodeHash, nodes);
    }

    @Override
    public List<BonsaiNode> getChildren() {
      return getNodes().stream()
          .flatMap(
              node -> {
                if (node instanceof NullNode){
                  return Stream.empty();
                }
                return retrieveStoredAccountNode(
                    node.getLocation().orElseThrow())
                    .stream();
              })
          .collect(Collectors.toList());
    }

    @Override
    public int delete(final BonsaiWorldStateKeyValueStorage.Updater updater) {
      int total = getChildren().stream().mapToInt(bonsaiNode -> bonsaiNode.delete(updater)).sum();
      updater.removeAccountStateTrieNode(getLocation(), getNodeHash());
      return total+1;
    }

    @Override
    public Function<Bytes, Optional<? extends BonsaiNode>> getNodeLocator() {
      return NodeDeletionProcessor.this::retrieveStoredAccountNode;
    }
  }

  public class BonsaiAccountLeafNode extends AbstractBonsaiNode {
    final Node<Bytes> node;

    protected BonsaiAccountLeafNode(
        final Bytes location, final Bytes32 nodeHash, final Node<Bytes> node) {
      super(location, nodeHash, Collections.emptyList());
       this.node = node;
    }

    @Override
    public int delete(final BonsaiWorldStateKeyValueStorage.Updater updater) {
      // todo: also remove the code
      int total = getStorageNode().stream().mapToInt(bonsaiNode -> bonsaiNode.delete(updater)).sum();
      updater.removeAccountStateTrieNode(getLocation(), getNodeHash());
      updater.removeAccountInfoState(Hash.wrap(getNodeHash()));
      return total+1;
    }

    @Override
    public List<BonsaiNode> findChildrenToDelete(final BonsaiNode newNode) {
      final Optional<BonsaiStorageNode> oldStorage = getStorageNode();
      final Optional<BonsaiStorageNode> newStorage = ((BonsaiAccountLeafNode) newNode).getStorageNode();
      if (oldStorage.isPresent() && newStorage.isEmpty()) {
        return List.of(oldStorage.get());
      }
      return Collections.emptyList();
    }

    private Optional<BonsaiStorageNode> getStorageNode() {
      return node.getValue()
              .map(bytes -> StateTrieAccountValue.readFrom(RLP.input(bytes)))
              .flatMap(stateTrieAccountValue -> {
                if (!stateTrieAccountValue.getStorageRoot().equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
                  final Hash accountHash =
                          Hash.wrap(Bytes32.wrap(CompactEncoding.pathToBytes(Bytes.concatenate(getLocation(), node.getPath()))));
                  return
                          retrieveStoredRootStorageNode(accountHash);
                }
                return Optional.empty();
              });
    }
  }

  public class BonsaiStorageInnerNode extends BonsaiStorageNode implements BonsaiInnerNode {
    public BonsaiStorageInnerNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final List<Node<Bytes>> nodes) {
      super(accountHash, location, nodeHash, nodes);
    }

    @Override
    public List<BonsaiNode> getChildren() {
      return getNodes().stream()
          .flatMap(
              node -> {
                if (node instanceof NullNode){
                  return Stream.empty();
                }
                  return node.getLocation().stream().flatMap(location -> retrieveStoredStorageNode(
                      getAccountHash(), location).stream());
              })
          .collect(Collectors.toList());
    }

    @Override
    public int delete(final BonsaiWorldStateKeyValueStorage.Updater updater) {
      int total = getChildren().stream().mapToInt(bonsaiNode -> bonsaiNode.delete(updater)).sum();
      updater.removeAccountStateTrieNode(getLocation(), getNodeHash());
      return total+1;
    }

    @Override
    public Function<Bytes, Optional<? extends BonsaiNode>> getNodeLocator() {
      return bytes -> retrieveStoredStorageNode(getAccountHash(), bytes);
    }
  }

  public static class BonsaiStorageLeafNode extends BonsaiStorageNode {

    private final Node<Bytes> data;

    public BonsaiStorageLeafNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Node<Bytes> data) {
      super(accountHash, location, nodeHash, Collections.emptyList());
      this.data = data;
    }


    private Hash getSlotHash() {
      return Hash.wrap(Bytes32.wrap(CompactEncoding.pathToBytes(Bytes.concatenate(getLocation(), data.getPath()))));
    }

    @Override
    public int delete(final BonsaiWorldStateKeyValueStorage.Updater updater) {
      updater.removeStorageValueBySlotHash(
          getAccountHash(), getSlotHash());
      updater.removeAccountStateTrieNode(getLocation(), getNodeHash());
      return 1;
    }
  }
}
