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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class NodeDeletionProcessor {
  private static final Logger LOG = getLogger(NodeDeletionProcessor.class);

  private final BonsaiWorldStateKeyValueStorage worldStateStorage;
  private final BonsaiWorldStateKeyValueStorage.Updater updater;

  public NodeDeletionProcessor(
      final WorldStateStorage worldStateStorage, final WorldStateStorage.Updater updater) {

    if (!(worldStateStorage instanceof BonsaiWorldStateKeyValueStorage)){
      throw new RuntimeException(
              "NodeDeletionManager only works with BonsaiWorldStateKeyValueStorage");
    }
    this.worldStateStorage =(BonsaiWorldStateKeyValueStorage) worldStateStorage;
    if (!(updater instanceof BonsaiWorldStateKeyValueStorage.Updater)) {
      throw new RuntimeException(
          "NodeDeletionManager only works with BonsaiWorldStateKeyValueStorage.Updater");
    }
    this.updater = (BonsaiWorldStateKeyValueStorage.Updater) updater;
  }

  public void startFromStorageNode(
          final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes data) {
    deletePotentialOldChildren(new BonsaiStorageInnerNode(accountHash, location, nodeHash, data), bytes -> retrieveStoredInnerStorageNode(accountHash, bytes));
  }

  public void startFromAccountNode(final Bytes location, final Bytes32 nodeHash, final Bytes data) {
    deletePotentialOldChildren(new BonsaiAccountInnerNode(location, nodeHash, data),this::retrieveStoredInnerAccountNode);
  }

  public int deletePotentialOldChildren(final BonsaiNode newNode,final Function<Bytes,Optional<? extends BonsaiNode>> nodeLocator) {
    return retrieveStoredInnerAccountNode(newNode.getLocation())
        .stream().mapToInt(
            oldNode -> compareChildrenAndDeleteOldOnes(oldNode, newNode, nodeLocator)).sum();
  }

  public int compareChildrenAndDeleteOldOnes(final BonsaiNode oldNode, final BonsaiNode newNode, final Function<Bytes,Optional<? extends BonsaiNode>> nodeLocator){
    int totalDeleted = 0;
    final List<Node<Bytes>> oldChildren = oldNode.decodeData();
    final List<Node<Bytes>> newChildren = newNode.decodeData();
    int oldChildIndex = 0;
    int newChildIndex = 0;
    while (oldChildIndex<oldChildren.size()){
      final Node<Bytes> oldChild = oldChildren.get(oldChildIndex);
      if (newChildIndex==newChildren.size()){
        if (!(oldChild instanceof NullNode)){
          totalDeleted += nodeLocator.apply(oldChild.getLocation().orElseThrow()).stream().mapToInt(root -> {
            LOG.warn("Deleting node {}:{}", root.getLocation(),root.getNodeHash());
            return deleteNode(root);
          }).sum();
        }
        oldChildIndex++;
        continue;
      }
      final Node<Bytes> newChild = newChildren.get(newChildIndex);
      if (oldChild.getLocation().equals(newChild.getLocation())){
        oldChildIndex++;
        newChildIndex++;
      } else if (oldChild instanceof NullNode){
        oldChildIndex++;
      } else if (newChild instanceof NullNode){
        newChildIndex++;
      } else{
        final Bytes oldChildLocation = oldChild.getLocation().orElseThrow();
        final Bytes newChildLocation = newChild.getLocation().orElseThrow();
        if (oldChildLocation.compareTo(newChildLocation)<0){
          //old child exists, but new child does not
          totalDeleted += nodeLocator.apply(oldChild.getLocation().orElseThrow())
                  .stream().mapToInt(
                          root -> {
                            LOG.warn("Deleting node {}:{}", root.getLocation(),root.getNodeHash());
                            return deleteNode(root);
                          }).sum();
          oldChildIndex++;
        } else {
          //new child exists, but old child does not
          newChildIndex++;
        }
      }
    }
    return totalDeleted;
  }

  private int deleteNode(final BonsaiNode root) {
    LOG.warn("Deleting children of node {}:{}", root.getLocation(),root.getNodeHash());
    final int deleted = root.getChildren().stream().mapToInt(this::deleteNode).sum();
    LOG.warn("Deleting node {}:{}", root.getLocation(),root.getNodeHash());
    return deleted + root.delete(updater);
  }

  public Optional<BonsaiNode> retrieveStoredInnerAccountNode(
      final Bytes location) {
    return worldStateStorage
        .getAccountStateTrieNode(location)
        .map(oldData -> new BonsaiAccountInnerNode(location, Hash.hash(oldData), oldData));
  }

  private Optional<BonsaiNode> retrieveStoredLeafAccountNode(
      final Bytes location) {
    return worldStateStorage
        .getAccountStateTrieNode(location)
        .map(oldData -> new BonsaiAccountLeafNode(location, Hash.hash(oldData), oldData));
  }

  private Optional<BonsaiStorageInnerNode> retrieveStoredRootStorageNode(
      final Hash accountHash) {
    return retrieveStoredInnerStorageNode(accountHash, Bytes.EMPTY);
  }

  public Optional<BonsaiStorageInnerNode> retrieveStoredInnerStorageNode(
      final Hash accountHash, final Bytes location) {
    return worldStateStorage
        .getAccountStorageTrieNode(accountHash, location)
        .map(oldData -> new BonsaiStorageInnerNode(accountHash, location,  Hash.hash(oldData), oldData));
  }

  private Optional<BonsaiStorageLeafNode> retrieveStoredLeafStorageNode(
      final Hash accountHash, final Bytes location) {
    return worldStateStorage
        .getAccountStorageTrieNode(accountHash, location)
        .map(oldData -> new BonsaiStorageLeafNode(accountHash, location, Hash.hash(oldData), oldData));
  }


  public abstract static class BonsaiNode {
    private final Bytes location;
    private final Bytes32 nodeHash;
    private final Bytes data;

    protected BonsaiNode(final Bytes location, final Bytes32 nodeHash, final Bytes data) {
      this.location = location;
      this.nodeHash = nodeHash;
      this.data = data;
    }

    public Bytes getLocation() {
      return location;
    }

    public Bytes32 getNodeHash() {
      return nodeHash;
    }

    public Bytes getData() {
      return data;
    }

    abstract List<BonsaiNode> getChildren();

    abstract int delete(BonsaiWorldStateKeyValueStorage.Updater updater);

    @NotNull
    public List<Node<Bytes>> decodeData() {
      return TrieNodeDecoder.decodeNodes(getLocation(), getData());
    }

    public boolean nodeIsHashReferencedDescendant(final Node<Bytes> node) {
      return !Objects.equals(node.getHash(), getNodeHash()) && node.isReferencedByHash();
    }
  }

  public abstract static class BonsaiStorageNode extends BonsaiNode {
    private final Hash accountHash;

    protected BonsaiStorageNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes data) {
      super(location, nodeHash, data);
      this.accountHash = accountHash;
    }

    public Hash getAccountHash() {
      return accountHash;
    }
  }

  public class BonsaiAccountInnerNode extends BonsaiNode {

    protected BonsaiAccountInnerNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes data) {
      super(location, nodeHash, data);
    }

    @Override
    List<BonsaiNode> getChildren() {
      return decodeData().stream()
          .flatMap(
              node -> {
                if (nodeIsHashReferencedDescendant(node)) {
                  return retrieveStoredInnerAccountNode(
                      node.getLocation().orElseThrow())
                      .stream();
                } else {
                  return retrieveStoredLeafAccountNode(
                      node.getLocation().orElseThrow())
                      .stream();
                }
              })
          .collect(Collectors.toList());
    }

    @Override
    int delete(final BonsaiWorldStateKeyValueStorage.Updater updater) {
      updater.removeAccountStateTrieNode(getLocation(), getNodeHash());
      return 1;
    }
  }

  public class BonsaiAccountLeafNode extends BonsaiNode {

    protected BonsaiAccountLeafNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes data) {
      super(location, nodeHash, data);
    }

    @Override
    List<BonsaiNode> getChildren() {
      final StateTrieAccountValue stateTrieAccountValue =
          StateTrieAccountValue.readFrom(RLP.input(getData()));
      if (!stateTrieAccountValue.getStorageRoot().equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        final Hash accountHash =
            Hash.wrap(Bytes32.wrap(CompactEncoding.pathToBytes(getLocation())));

        return Collections.singletonList(
            retrieveStoredRootStorageNode(accountHash)
                .orElseThrow());
      }
      return Collections.emptyList();
    }

    @Override
    int delete(final BonsaiWorldStateKeyValueStorage.Updater updater) {
      // todo: also remove the code
      updater.removeAccountStateTrieNode(getLocation(), getNodeHash());
      updater.removeAccountInfoState(Hash.wrap(getNodeHash()));
      return 1;
    }
  }

  public class BonsaiStorageInnerNode extends BonsaiStorageNode {
    public BonsaiStorageInnerNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes data) {
      super(accountHash, location, nodeHash, data);
    }

    @Override
    public List<BonsaiNode> getChildren() {
      return decodeData().stream()
          .flatMap(
              node -> {
                if (nodeIsHashReferencedDescendant(node)) {
                  return node.getLocation().stream().flatMap(location -> retrieveStoredInnerStorageNode(
                      getAccountHash(), location).stream());
                } else {
                  return node.getLocation().stream().flatMap(location->retrieveStoredLeafStorageNode(
                      getAccountHash(), location).stream());
                }
              })
          .collect(Collectors.toList());
    }

    @Override
    public int delete(final BonsaiWorldStateKeyValueStorage.Updater updater) {
      updater.removeAccountStateTrieNode(getLocation(), getNodeHash());
      return 1;
    }
  }

  public static class BonsaiStorageLeafNode extends BonsaiStorageNode {

    public BonsaiStorageLeafNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes data) {
      super(accountHash, location, nodeHash, data);
    }

    @Override
    public List<BonsaiNode> getChildren() {
      return Collections.emptyList();
    }


    private Hash getSlotHash() {
      return Hash.wrap(Bytes32.wrap(CompactEncoding.pathToBytes(Bytes.concatenate(getLocation(), decodeData().get(0).getPath()))));
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
