/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.subcommands.storage;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;
import static org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldView.encodeTrieValue;

import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.flat.BonsaiFlatDbStrategy;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.patricia.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/** The revert metadata to v1 subcommand. */
@Command(
    name = "rebuild-bonsai-state-trie",
    description = "Rebuilds bonsai state trie from flat database",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class RebuildBonsaiStateTrieSubCommand implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(RebuildBonsaiStateTrieSubCommand.class);
  private static final Hash HASH_LAST =
      Hash.wrap(Bytes32.leftPad(Bytes.fromHexString("FF"), (byte) 0xFF));
  static final long FORCED_COMMIT_INTERVAL = 50_000L;

  @SuppressWarnings("unused")
  @ParentCommand
  private StorageSubCommand parentCommand;

  @SuppressWarnings("unused")
  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec;

  /** Default Constructor. */
  public RebuildBonsaiStateTrieSubCommand() {}

  @Override
  public void run() {
    // spec.commandLine().usage(System.out);
    try (final BesuController controller = createController()) {

      var storageConfig = controller.getDataStorageConfiguration();

      if (!storageConfig.getDataStorageFormat().equals(DataStorageFormat.BONSAI)) {
        LOG.error("Data Storage format is not BONSAI.  Refusing to rebuild state trie.");
        System.exit(-1);
      }

      var worldStateStorage =
          (BonsaiWorldStateKeyValueStorage)
              controller
                  .getStorageProvider()
                  .createWorldStateStorage(controller.getDataStorageConfiguration());

      if (!worldStateStorage.getFlatDbMode().equals(FlatDbMode.FULL)) {
        LOG.error("Database is not fully flattened. Refusing to rebuild state trie.");
        System.exit(-1);
      }

      final BlockHeader header =
          controller.getProtocolContext().getBlockchain().getChainHeadHeader();

      worldStateStorage
          .getWorldStateRootHash()
          // we want state root hash to either be empty or same the same as chain head
          .filter(root -> !root.equals(header.getStateRoot()))
          .ifPresent(
              foundRoot -> {
                LOG.error(
                    "Chain head {} does not match state root {}.  Refusing to rebuild state trie.",
                    header.getStateRoot(),
                    foundRoot);
                System.exit(-1);
              });

      // rebuild trie:
      var newHash = rebuildTrie(worldStateStorage);

      // write state root and block hash from the header:
      if (!header.getStateRoot().equals(newHash)) {
        LOG.error(
            "Catastrophic: calculated state root {} after state rebuild, was expecting {}.",
            newHash,
            header.getStateRoot());
        System.exit(-1);
      }
      writeStateRootAndBlockHash(header, worldStateStorage);
    }
  }

  Hash rebuildTrie(final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    // truncate the TRIE_BRANCH_STORAGE column family,
    //   subsequently rewrite blockhash and state root after we rebuild the trie
    worldStateStorage.clearTrie();

    // rebuild the trie by inserting everything into a StoredMerklePatriciaTrie
    // and incrementally (naively) commit after each account while streaming

    final var wss = worldStateStorage.getComposedWorldStateStorage();
    var accountTrie =
        new StoredMerklePatriciaTrie<>(
            new StoredNodeFactory<>(
                // this may be inefficient, and we can read through an incrementally committing tx
                // instead
                worldStateStorage::getAccountStateTrieNode,
                Function.identity(),
                Function.identity()),
            MerkleTrie.EMPTY_TRIE_NODE_HASH);

    final var accountsTx = new WrappedTransaction(worldStateStorage.getComposedWorldStateStorage());
    final Consumer<StoredMerklePatriciaTrie<Bytes, Bytes>> accountTrieCommit =
        (trie) ->
            trie.commit(
                (loc, hash, value) ->
                    accountsTx.put(
                        TRIE_BRANCH_STORAGE, loc.toArrayUnsafe(), value.toArrayUnsafe()));

    final var flatdb = worldStateStorage.getFlatDbStrategy();
    final var accountsIterator = flatdb.accountsToPairStream(wss, Bytes32.ZERO).iterator();

    long accountsCount = 0L;
    while (accountsIterator.hasNext()) {
      var accountPair = accountsIterator.next();

      // if the account has non-empty storage, write the account storage trie values
      var acctState = extractStateRootHash(accountPair.getSecond());
      if (!Hash.EMPTY_TRIE_HASH.equals(acctState.storageRoot)) {
        var newStateTrieHash =
            rebuildAccountTrie(flatdb, worldStateStorage, Hash.wrap(accountPair.getFirst()));
        if (!newStateTrieHash.equals(acctState.storageRoot)) {
          throw new RuntimeException(
              String.format(
                  "accountHash %s calculated state root %s does not match account state root %s",
                  accountPair.getFirst(), newStateTrieHash, acctState.storageRoot));
        }
      }

      // write the account info
      accountTrie.put(accountPair.getFirst(), accountPair.getSecond());

      if (accountsCount++ % FORCED_COMMIT_INTERVAL == 0) {
        LOG.info("committing account trie at account {}", accountPair.getFirst());

        // commit the account trie if we have exceeded the forced commit interval
        accountTrieCommit.accept(accountTrie);
        accountsTx.commitAndReopen();

        // new trie with new root, GC trie nodes
        accountTrie =
            new StoredMerklePatriciaTrie<>(
                new StoredNodeFactory<>(
                    // this may be inefficient, and we can read through an incrementally committing
                    // tx
                    // instead
                    worldStateStorage::getAccountStateTrieNode,
                    Function.identity(),
                    Function.identity()),
                accountTrie.getRootHash());
      }
    }

    // final commit
    accountTrieCommit.accept(accountTrie);
    accountsTx.commit();

    // return the new state trie root hash
    return Hash.wrap(accountTrie.getRootHash());
  }

  /**
   * Rebuild the account storage trie for a single account hash
   *
   * @param flatdb reference to the flat db
   * @param worldStateStorage reference to the worldstate storage
   * @param accountHash the hash of the account we need to rebuild contract storage for
   */
  Hash rebuildAccountTrie(
      final BonsaiFlatDbStrategy flatdb,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Hash accountHash) {

    var accountStorageTx = new WrappedTransaction(worldStateStorage.getComposedWorldStateStorage());
    var wss = worldStateStorage.getComposedWorldStateStorage();

    Function<Bytes32, StoredMerklePatriciaTrie<Bytes, Bytes>> newAccountStorageTrie =
        (rootHash) ->
            new StoredMerklePatriciaTrie<>(
                new StoredNodeFactory<>(
                    (location, hash) ->
                        worldStateStorage.getAccountStorageTrieNode(accountHash, location, hash),
                    Function.identity(),
                    Function.identity()),
                rootHash);

    // create account storage trie
    var accountStorageTrie = newAccountStorageTrie.apply(MerkleTrie.EMPTY_TRIE_NODE_HASH);

    Consumer<StoredMerklePatriciaTrie<Bytes, Bytes>> accountStorageCommit =
        (trie) ->
            trie.commit(
                (location, hash, value) ->
                    accountStorageTx.put(
                        TRIE_BRANCH_STORAGE,
                        Bytes.concatenate(accountHash, location).toArrayUnsafe(),
                        value.toArrayUnsafe()));

    // put into account trie
    var accountStorageIterator =
        flatdb
            .storageToPairStream(
                wss, Hash.wrap(accountHash), Bytes32.ZERO, HASH_LAST, Function.identity())
            .iterator();
    long accountStorageCount = 0L;
    while (accountStorageIterator.hasNext()) {
      var storagePair = accountStorageIterator.next();
      accountStorageTrie.put(storagePair.getFirst(), encodeTrieValue(storagePair.getSecond()));

      // commit the account storage trie
      if (accountStorageCount++ % FORCED_COMMIT_INTERVAL == 0) {
        accountStorageCommit.accept(accountStorageTrie);
        accountStorageTx.commitAndReopen();
        // new trie with new root, GC trie nodes
        accountStorageTrie = newAccountStorageTrie.apply(accountStorageTrie.getRootHash());
      }
    }
    accountStorageCommit.accept(accountStorageTrie);
    accountStorageTx.commit();

    return Hash.wrap(accountStorageTrie.getRootHash());
  }

  void writeStateRootAndBlockHash(
      final BlockHeader header, final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    var tx = worldStateStorage.getComposedWorldStateStorage().startTransaction();
    tx.put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, header.getStateRoot().toArrayUnsafe());
    tx.put(TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, header.getBlockHash().toArrayUnsafe());
    LOG.info("committing blockhash and stateroot {}", header.toLogString());
    tx.commit();
  }

  record SimpleAccountState(long nonce, UInt256 balance, Hash storageRoot, Hash codeHash) {}

  private SimpleAccountState extractStateRootHash(final Bytes accountRLP) {
    final RLPInput in = RLP.input(accountRLP);
    in.enterList();

    // nonce
    final long nonce = in.readLongScalar();
    // balance
    final UInt256 balance = in.readUInt256Scalar();
    // storageRoot
    final Hash storageRoot = Hash.wrap(in.readBytes32());
    // code hash
    final Hash codeHash = Hash.wrap(in.readBytes32());

    //    in.leaveList();
    return new SimpleAccountState(nonce, balance, storageRoot, codeHash);
  }

  private BesuController createController() {
    try {
      // Set some defaults
      return parentCommand
          .besuCommand
          .setupControllerBuilder()
          .miningParameters(MiningConfiguration.MINING_DISABLED)
          .build();
    } catch (final Exception e) {
      throw new CommandLine.ExecutionException(spec.commandLine(), e.getMessage(), e);
    }
  }

  static class WrappedTransaction implements SegmentedKeyValueStorageTransaction {

    private final SegmentedKeyValueStorage storage;
    private SegmentedKeyValueStorageTransaction intervalTx;

    WrappedTransaction(final SegmentedKeyValueStorage storage) {
      this.storage = storage;
      this.intervalTx = storage.startTransaction();
    }

    @Override
    public void put(
        final SegmentIdentifier segmentIdentifier, final byte[] key, final byte[] value) {
      intervalTx.put(segmentIdentifier, key, value);
    }

    @Override
    public void remove(final SegmentIdentifier segmentIdentifier, final byte[] key) {
      intervalTx.remove(segmentIdentifier, key);
    }

    @Override
    public void commit() throws StorageException {
      intervalTx.commit();
    }

    public void commitAndReopen() throws StorageException {
      commit();
      intervalTx = storage.startTransaction();
    }

    @Override
    public void rollback() {
      throw new RuntimeException("WrappedTransaction can not completely rollback.");
    }
  }
}
