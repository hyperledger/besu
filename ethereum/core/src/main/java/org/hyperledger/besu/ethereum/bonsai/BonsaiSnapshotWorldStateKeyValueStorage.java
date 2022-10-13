package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BonsaiSnapshotWorldStateKeyValueStorage extends BonsaiWorldStateKeyValueStorage {
  private final SnapshotUpdater updater;

  public BonsaiSnapshotWorldStateKeyValueStorage(
      final SnappedKeyValueStorage accountStorage,
      final SnappedKeyValueStorage codeStorage,
      final SnappedKeyValueStorage storageStorage,
      final SnappedKeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage) {
    super(accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage);
    this.updater =
        new SnapshotUpdater(
            accountStorage, codeStorage, storageStorage, trieBranchStorage, trieLogStorage);
  }

  @Override
  public BonsaiUpdater updater() {
    return updater;
  }

  public static class SnapshotUpdater implements BonsaiWorldStateKeyValueStorage.BonsaiUpdater {
    private final SnappedKeyValueStorage accountStorage;
    private final SnappedKeyValueStorage codeStorage;
    private final SnappedKeyValueStorage storageStorage;
    private final SnappedKeyValueStorage trieBranchStorage;
    private final KeyValueStorageTransaction trieLogStorage;

    public SnapshotUpdater(
        final SnappedKeyValueStorage accountStorage,
        final SnappedKeyValueStorage codeStorage,
        final SnappedKeyValueStorage storageStorage,
        final SnappedKeyValueStorage trieBranchStorage,
        final KeyValueStorage trieLogStorage) {
      this.accountStorage = accountStorage;
      this.codeStorage = codeStorage;
      this.storageStorage = storageStorage;
      this.trieBranchStorage = trieBranchStorage;
      this.trieLogStorage = trieLogStorage.startTransaction();
    }

    @Override
    public BonsaiUpdater removeCode(final Hash accountHash) {
      codeStorage.getSnapshotTransaction().remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater putCode(
        final Hash accountHash, final Bytes32 nodeHash, final Bytes code) {
      if (code.size() == 0) {
        // Don't save empty values
        return this;
      }
      codeStorage.getSnapshotTransaction().put(accountHash.toArrayUnsafe(), code.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater removeAccountInfoState(final Hash accountHash) {
      accountStorage.getSnapshotTransaction().remove(accountHash.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putAccountInfoState(final Hash accountHash, final Bytes accountValue) {
      if (accountValue.size() == 0) {
        // Don't save empty values
        return this;
      }
      accountStorage
          .getSnapshotTransaction()
          .put(accountHash.toArrayUnsafe(), accountValue.toArrayUnsafe());
      return this;
    }

    @Override
    public BonsaiUpdater putStorageValueBySlotHash(
        final Hash accountHash, final Hash slotHash, final Bytes storage) {
      storageStorage
          .getSnapshotTransaction()
          .put(Bytes.concatenate(accountHash, slotHash).toArrayUnsafe(), storage.toArrayUnsafe());
      return this;
    }

    @Override
    public void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
      storageStorage
          .getSnapshotTransaction()
          .remove(Bytes.concatenate(accountHash, slotHash).toArrayUnsafe());
    }

    @Override
    public KeyValueStorageTransaction getTrieBranchStorageTransaction() {
      return trieBranchStorage.getSnapshotTransaction();
    }

    @Override
    public KeyValueStorageTransaction getTrieLogStorageTransaction() {
      return trieLogStorage;
    }

    @Override
    public WorldStateStorage.Updater saveWorldState(
        final Bytes blockHash, final Bytes32 nodeHash, final Bytes node) {
      trieBranchStorage
          .getSnapshotTransaction()
          .put(Bytes.EMPTY.toArrayUnsafe(), node.toArrayUnsafe());
      trieBranchStorage.getSnapshotTransaction().put(WORLD_ROOT_HASH_KEY, nodeHash.toArrayUnsafe());
      trieBranchStorage
          .getSnapshotTransaction()
          .put(WORLD_BLOCK_HASH_KEY, blockHash.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater putAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorage
          .getSnapshotTransaction()
          .put(location.toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater removeAccountStateTrieNode(
        final Bytes location, final Bytes32 nodeHash) {
      trieBranchStorage.getSnapshotTransaction().remove(location.toArrayUnsafe());
      return this;
    }

    @Override
    public WorldStateStorage.Updater putAccountStorageTrieNode(
        final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes node) {
      if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
        // Don't save empty nodes
        return this;
      }
      trieBranchStorage
          .getSnapshotTransaction()
          .put(Bytes.concatenate(accountHash, location).toArrayUnsafe(), node.toArrayUnsafe());
      return this;
    }

    @Override
    public void commit() {
      // no-op, do not commit snapshot transactions
    }

    @Override
    public void rollback() {
      // no-op, do not commit snapshot transactions
    }
  }
}
