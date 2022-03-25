package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.CommitVisitor;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.SnapPutVisitor;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class StackTrie {

  private final Bytes32 rootHash;
  private final AtomicInteger nbSegments;
  private final int maxSegments;
  private final Bytes32 startKeyHash;
  private final List<Bytes> proofs;
  private final TreeMap<Bytes32, Bytes> keys;

  public StackTrie(final Hash rootHash, final int nbSegments, final int maxSegments, final Bytes32 startKeyHash) {
    this.rootHash = rootHash;
    this.nbSegments = new AtomicInteger(nbSegments);
    this.maxSegments = maxSegments;
    this.startKeyHash = startKeyHash;
    this.proofs = new ArrayList<>();
    this.keys = new TreeMap<>();
  }

  public void addKeys(final TreeMap<Bytes32, Bytes> keys) {
    this.keys.putAll(keys);
  }

  public void addProofs(final List<Bytes> proofs) {
    this.proofs.addAll(proofs);
  }

  public void commit(final Bytes32 accountHash, final NodeUpdater nodeUpdater, final Consumer<Node<Bytes>> tmpNode) {
    if (nbSegments.decrementAndGet()<=0 && (!proofs.isEmpty() || !keys.isEmpty())) {
      final Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
      for (Bytes proof : proofs) {
        proofsEntries.put(Hash.hash(proof), proof);
      }
      final InnerNodeDiscoveryManager<Bytes> snapStoredNodeFactory =
          new InnerNodeDiscoveryManager<>(
              (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
              Function.identity(),
              Function.identity(),
              startKeyHash,
              keys.lastKey(),
              true);

      final MerklePatriciaTrie<Bytes, Bytes> trie =
          new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, rootHash);

      for (Map.Entry<Bytes32, Bytes> account : keys.entrySet()) {
        trie.put(account.getKey(), new SnapPutVisitor<>(snapStoredNodeFactory, account.getValue()));
      }
      trie.commit(
          nodeUpdater,
          (new CommitVisitor<>(nodeUpdater) {
            @Override
            public void maybeStoreNode(final Bytes location, final Node<Bytes> node) {
              if (!node.isNeedHeal()) {
                super.maybeStoreNode(location, node);
              }else {
                final Bytes nodeRLP = node.getRlp();
                if (nodeRLP.size() >= 32) {
                  tmpNode.accept(node);
                }
              }
            }
          }));
    }
  }

  public boolean addSegment(){
    if(nbSegments.get()>maxSegments){
      return false;
    } else {
      nbSegments.incrementAndGet();
      return true;
    }
  }
}
