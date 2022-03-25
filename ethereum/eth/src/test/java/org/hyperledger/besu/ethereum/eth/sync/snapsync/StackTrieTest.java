package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.RangedStorageEntriesCollector;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class StackTrieTest  {

    Bytes32 lastAccount = RangeManager.MIN_RANGE;
    @Test
    public void testGenerateAllRangesWithSize1() {
        final WorldStateStorage worldStateStorage =
                new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());

        final MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie =
                generateTrie(worldStateStorage, 150000);

        StackTrie stackTrie = new StackTrie(Hash.wrap(accountStateTrie.getRootHash()), 0, 1000000,RangeManager.MIN_RANGE);

        for (int i = 0 ; i<150000; i+=100) {
            stackTrie.addSegment();
            final RangedStorageEntriesCollector collector =
                    RangedStorageEntriesCollector.createCollector(
                            lastAccount, RangeManager.MAX_RANGE, 100, Integer.MAX_VALUE);
            final TrieIterator<Bytes> visitor = RangedStorageEntriesCollector.createVisitor(collector);
            final TreeMap<Bytes32, Bytes> accounts =
                    (TreeMap<Bytes32, Bytes>)
                            accountStateTrie.entriesFrom(
                                    root ->
                                            RangedStorageEntriesCollector.collectEntries(
                                                    collector, visitor, root, lastAccount));

            final WorldStateProofProvider worldStateProofProvider =
                    new WorldStateProofProvider(worldStateStorage);

            // generate the proof
            final List<Bytes> proofs =
                    worldStateProofProvider.getAccountProofRelatedNodes(
                            Hash.wrap(accountStateTrie.getRootHash()), lastAccount);
            proofs.addAll(
                    worldStateProofProvider.getAccountProofRelatedNodes(
                            Hash.wrap(accountStateTrie.getRootHash()), accounts.lastKey()));

            stackTrie.addKeys(accounts);
            System.out.println(accounts.size());
            stackTrie.addProofs(proofs);

            RangeManager.findNewBeginElementInRange(accountStateTrie.getRootHash(), proofs, accounts, RangeManager.MAX_RANGE).ifPresent(bytes32 -> {
                lastAccount = bytes32;
                System.out.println(lastAccount);
            });

            stackTrie.commit((location, hash, value) -> {
                if(location.isEmpty()){
                    System.out.println("youhou");
                }
            }, bytesNode -> {});
        }



    }

    public static MerklePatriciaTrie<Bytes32, Bytes> generateTrie(
            final WorldStateStorage worldStateStorage, final int nbAccounts) {
        final List<Hash> accountHash = new ArrayList<>();
        final MerklePatriciaTrie<Bytes32, Bytes> accountStateTrie =
                emptyAccountStateTrie(worldStateStorage);
        // Add some storage values
        for (int i = 0; i < nbAccounts; i++) {
            final WorldStateStorage.Updater updater = worldStateStorage.updater();

            accountHash.add(Hash.wrap(Bytes32.random()));
            final MerklePatriciaTrie<Bytes32, Bytes> storageTrie =
                    emptyStorageTrie(worldStateStorage, accountHash.get(i));
            writeStorageValue(storageTrie, UInt256.ONE, UInt256.valueOf(2L));
            writeStorageValue(storageTrie, UInt256.valueOf(2L), UInt256.valueOf(4L));
            writeStorageValue(storageTrie, UInt256.valueOf(3L), UInt256.valueOf(6L));
            int accountIndex = i;
            storageTrie.commit(
                    (location, hash, value) ->
                            updater.putAccountStorageTrieNode(
                                    accountHash.get(accountIndex), location, hash, value));
            final Hash codeHash = Hash.wrap(Bytes32.random());
            final StateTrieAccountValue accountValue =
                    new StateTrieAccountValue(1L, Wei.of(2L), Hash.wrap(storageTrie.getRootHash()), codeHash);
            accountStateTrie.put(accountHash.get(i), RLP.encode(accountValue::writeTo));
            accountStateTrie.commit(updater::putAccountStateTrieNode);

            // Persist updates
            updater.commit();
        }
        return accountStateTrie;
    }

    private static void writeStorageValue(
            final MerklePatriciaTrie<Bytes32, Bytes> storageTrie,
            final UInt256 key,
            final UInt256 value) {
        storageTrie.put(storageKeyHash(key), encodeStorageValue(value));
    }

    private static Bytes32 storageKeyHash(final UInt256 storageKey) {
        return Hash.hash(storageKey);
    }

    private static Bytes encodeStorageValue(final UInt256 storageValue) {
        return RLP.encode(out -> out.writeBytes(storageValue.toMinimalBytes()));
    }

    public static MerklePatriciaTrie<Bytes32, Bytes> emptyStorageTrie(
            final WorldStateStorage worldStateStorage, final Hash accountHash) {
        return new StoredMerklePatriciaTrie<>(
                (location, hash) ->
                        worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
                b -> b,
                b -> b);
    }

    public static MerklePatriciaTrie<Bytes32, Bytes> emptyAccountStateTrie(
            final WorldStateStorage worldStateStorage) {
        return new StoredMerklePatriciaTrie<>(
                worldStateStorage::getAccountStateTrieNode, b -> b, b -> b);
    }
}