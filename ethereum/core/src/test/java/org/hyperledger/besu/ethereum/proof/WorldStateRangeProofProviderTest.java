package org.hyperledger.besu.ethereum.proof;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.InnerNodeDiscoveryManager;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.RangedStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Test;

public class WorldStateRangeProofProviderTest {

  private static final Hash MAX_RANGE =
      Hash.fromHexString("0x0fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  private static final WorldStateStorage worldStateStorage =
      new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

  private static WorldStateProofProvider worldStateProofProvider;

  @Before
  public void setup() {
    worldStateProofProvider = new WorldStateProofProvider(worldStateStorage);
  }

  @Test
  public void rangeProofValidationNominalCase() {
    MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie = generateTrie();
    // collect accounts in range
    final RangedStorageEntriesCollector collector =
        RangedStorageEntriesCollector.createCollector(Hash.ZERO, MAX_RANGE, 10, Integer.MAX_VALUE);
    final TrieIterator<Bytes> visitor = RangedStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangedStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Hash.ZERO));
    // generate the proof
    final List<Bytes> proofs =
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), Hash.ZERO);
    proofs.addAll(
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

    // validate the range proof

    // reconstruct the trie with the proof
    Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
    for (Bytes proof : proofs) {
      proofsEntries.put(Hash.hash(proof), proof);
    }
    final InnerNodeDiscoveryManager<Bytes> snapStoredNodeFactory =
        new InnerNodeDiscoveryManager<>(
            (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
            Function.identity(),
            Function.identity(),
            Bytes32.ZERO,
            accounts.lastKey());

    // search inner nodes
    MerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, accountStateTrie.getRootHash());
    trie.visitAll(node -> {});

    final Bytes[] innerNodes = snapStoredNodeFactory.getInnerNodes().toArray(new Bytes[0]);
    for (Bytes innerNode : innerNodes) {
      trie.removePath(innerNode);
    }

    assertThat(accountStateTrie.getRootHash()).isNotEqualByComparingTo(trie.getRootHash());

    Bytes32[] accountsToSave = accounts.keySet().toArray(new Bytes32[0]);
    for (int i = 0; i < 10; i++) {
      trie.put(accountsToSave[i], accounts.get(accountsToSave[i]));
    }

    assertThat(accountStateTrie.getRootHash()).isEqualByComparingTo(trie.getRootHash());
  }

  @Test
  public void rangeProofValidationMissingAccount() {
    MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie = generateTrie();
    // collect accounts in range
    final RangedStorageEntriesCollector collector =
        RangedStorageEntriesCollector.createCollector(Hash.ZERO, MAX_RANGE, 10, Integer.MAX_VALUE);
    final TrieIterator<Bytes> visitor = RangedStorageEntriesCollector.createVisitor(collector);
    final TreeMap<Bytes32, Bytes> accounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangedStorageEntriesCollector.collectEntries(
                        collector, visitor, root, Hash.ZERO));
    // generate the proof
    final List<Bytes> proofs =
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), Hash.ZERO);
    proofs.addAll(
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

    // validate the range proof

    // reconstruct the trie with the proof
    Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
    for (Bytes proof : proofs) {
      proofsEntries.put(Hash.hash(proof), proof);
    }
    final InnerNodeDiscoveryManager<Bytes> snapStoredNodeFactory =
        new InnerNodeDiscoveryManager<>(
            (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
            Function.identity(),
            Function.identity(),
            Bytes32.ZERO,
            accounts.lastKey());

    // search inner nodes
    MerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, accountStateTrie.getRootHash());
    trie.visitAll(node -> {});

    final Bytes[] innerNodes = snapStoredNodeFactory.getInnerNodes().toArray(new Bytes[0]);
    for (Bytes innerNode : innerNodes) {
      trie.removePath(innerNode);
    }

    assertThat(accountStateTrie.getRootHash()).isNotEqualByComparingTo(trie.getRootHash());

    // missing account 7
    Bytes32[] accountsToSave = accounts.keySet().toArray(new Bytes32[0]);
    for (int i = 0; i < 10; i++) {
      if (i != 7) trie.put(accountsToSave[i], accounts.get(accountsToSave[i]));
    }

    assertThat(accountStateTrie.getRootHash()).isNotEqualByComparingTo(trie.getRootHash());
  }

  @Test
  public void rangeProofValidationInvalidRightProof() {
    MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie = generateTrie();

    // generate the invalid proof
    final RangedStorageEntriesCollector invalidCollector =
        RangedStorageEntriesCollector.createCollector(Hash.ZERO, MAX_RANGE, 12, Integer.MAX_VALUE);
    final TrieIterator<Bytes> invalidVisitor =
        RangedStorageEntriesCollector.createVisitor(invalidCollector);
    final TreeMap<Bytes32, Bytes> invalidAccounts =
        (TreeMap<Bytes32, Bytes>)
            accountStateTrie.entriesFrom(
                root ->
                    RangedStorageEntriesCollector.collectEntries(
                        invalidCollector, invalidVisitor, root, Hash.ZERO));
    final List<Bytes> invalidProofs =
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), Hash.ZERO);
    invalidProofs.addAll(
        worldStateProofProvider.getAccountProofRelatedNodes(
            Hash.wrap(accountStateTrie.getRootHash()), invalidAccounts.lastKey()));

    // validate the range proof

    // reconstruct the trie with the proof
    Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
    for (Bytes proof : invalidProofs) {
      proofsEntries.put(Hash.hash(proof), proof);
    }
    final InnerNodeDiscoveryManager<Bytes> snapStoredNodeFactory =
        new InnerNodeDiscoveryManager<>(
            (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
            Function.identity(),
            Function.identity(),
            Bytes32.ZERO,
            invalidAccounts.lastKey());

    // search inner nodes
    MerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, accountStateTrie.getRootHash());
    trie.visitAll(node -> {});

    final Bytes[] innerNodes = snapStoredNodeFactory.getInnerNodes().toArray(new Bytes[0]);
    for (Bytes innerNode : innerNodes) {
      trie.removePath(innerNode);
    }

    assertThat(accountStateTrie.getRootHash()).isNotEqualByComparingTo(trie.getRootHash());

    // missing right accounts
    Bytes32[] accountsToSave = invalidAccounts.keySet().toArray(new Bytes32[0]);
    for (int i = 0; i < 10; i++) {
      trie.put(accountsToSave[i], invalidAccounts.get(accountsToSave[i]));
    }

    assertThat(accountStateTrie.getRootHash()).isNotEqualByComparingTo(trie.getRootHash());
  }

  private MerklePatriciaTrie<Bytes32, Bytes> generateTrie() {
    final List<Hash> accountHash = new ArrayList<>();
    final MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie = emptyAccountStateTrie();
    // Add some storage values
    for (int i = 0; i < 100; i++) {
      final WorldStateStorage.Updater updater = worldStateStorage.updater();

      accountHash.add(Hash.wrap(Bytes32.leftPad(Bytes.of(i + 1))));
      final MerklePatriciaTrie<Bytes32, Bytes> storageTrie = emptyStorageTrie(accountHash.get(i));
      writeStorageValue(storageTrie, UInt256.ONE, UInt256.valueOf(2L));
      writeStorageValue(storageTrie, UInt256.valueOf(2L), UInt256.valueOf(4L));
      writeStorageValue(storageTrie, UInt256.valueOf(3L), UInt256.valueOf(6L));
      int accountIndex = i;
      storageTrie.commit(
          (location, hash, value) ->
              updater.putAccountStorageTrieNode(
                  accountHash.get(accountIndex), location, hash, value));
      final Hash codeHash = Hash.hash(Bytes32.leftPad(Bytes.of(i + 10)));
      final StateTrieAccountValue accountValue =
          new StateTrieAccountValue(1L, Wei.of(2L), Hash.wrap(storageTrie.getRootHash()), codeHash);
      accountStateTrie.put(accountHash.get(i), RLP.encode(accountValue::writeTo));
      accountStateTrie.commit(updater::putAccountStateTrieNode);

      // Persist updates
      updater.commit();
    }
    return accountStateTrie;
  }

  private void writeStorageValue(
      final MerklePatriciaTrie<Bytes32, Bytes> storageTrie,
      final UInt256 key,
      final UInt256 value) {
    storageTrie.put(storageKeyHash(key), encodeStorageValue(value));
  }

  private Bytes32 storageKeyHash(final UInt256 storageKey) {
    return Hash.hash(storageKey);
  }

  private Bytes encodeStorageValue(final UInt256 storageValue) {
    return RLP.encode(out -> out.writeBytes(storageValue.toMinimalBytes()));
  }

  private MerklePatriciaTrie<Bytes32, Bytes> emptyStorageTrie(final Hash accountHash) {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) ->
            worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
        b -> b,
        b -> b);
  }

  private static MerklePatriciaTrie<Bytes32, Bytes> emptyAccountStateTrie() {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode, b -> b, b -> b);
  }
}
