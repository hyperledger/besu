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

import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.patricia.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
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
    // TODO: optimize to incrementally commit tx after a certain threshold

    final var wss = worldStateStorage.getComposedWorldStateStorage();
    final var accountTrie =
        new StoredMerklePatriciaTrie<>(
            new StoredNodeFactory<>(
                // this may be inefficient, and we can read through an incrementally committing tx
                // instead
                worldStateStorage::getAccountStateTrieNode,
                Function.identity(),
                Function.identity()),
            MerkleTrie.EMPTY_TRIE_NODE_HASH);

    final var flatdb = worldStateStorage.getFlatDbStrategy();
    flatdb
        .accountsToPairStream(wss, Bytes32.ZERO)
        .forEach(
            accountPair -> {
              final SegmentedKeyValueStorageTransaction perAccountTx =
                  worldStateStorage.getComposedWorldStateStorage().startTransaction();

              // if the account has storage, write the account storage trie values
              if (isNonEmptyStorage(accountPair.getSecond())) {
                // create account storage trie
                var accountStorageTrie =
                    new StoredMerklePatriciaTrie<>(
                        new StoredNodeFactory<>(
                            (location, hash) ->
                                worldStateStorage.getAccountStorageTrieNode(
                                    Hash.wrap(accountPair.getFirst()), location, hash),
                            Function.identity(),
                            Function.identity()),
                        MerkleTrie.EMPTY_TRIE_NODE_HASH);

                // put into account trie
                flatdb
                    .storageToPairStream(
                        wss,
                        Hash.wrap(accountPair.getFirst()),
                        Bytes32.ZERO,
                        HASH_LAST,
                        Function.identity())
                    .forEach(
                        storagePair -> {
                          accountStorageTrie.put(storagePair.getFirst(), storagePair.getSecond());
                        });

                // commit the account storage trie
                accountStorageTrie.commit(
                    (location, hash, value) ->
                        perAccountTx.put(
                            TRIE_BRANCH_STORAGE,
                            Bytes.concatenate(accountPair.getFirst(), location).toArrayUnsafe(),
                            value.toArrayUnsafe()));
              }

              // write the account info
              accountTrie.put(accountPair.getFirst(), accountPair.getSecond());

              // commit the account trie
              accountTrie.commit(
                  (location, hash, value) ->
                      perAccountTx.put(
                          TRIE_BRANCH_STORAGE, location.toArrayUnsafe(), value.toArrayUnsafe()));

              LOG.info("committing accountHash {}", accountPair.getFirst());
              perAccountTx.commit();
            });

    // perhaps not the safest cast
    return Hash.wrap(accountTrie.getRootHash());
  }

  void writeStateRootAndBlockHash(
      final BlockHeader header, final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    var tx = worldStateStorage.getComposedWorldStateStorage().startTransaction();
    tx.put(TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY, header.getStateRoot().toArrayUnsafe());
    tx.put(TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY, header.getBlockHash().toArrayUnsafe());
    LOG.info("committing blockhash and stateroot {}", header.toLogString());
    tx.commit();
  }

  private boolean isNonEmptyStorage(final Bytes accountRLP) {
    final RLPInput in = RLP.input(accountRLP);
    in.enterList();

    // nonce
    in.readLongScalar();
    // balance
    in.readUInt256Scalar();
    // storageRoot
    final Hash storageRoot = Hash.wrap(in.readBytes32());
    // final Hash codeHash = Hash.wrap(in.readBytes32());

    //    in.leaveList();
    return !storageRoot.equals(Hash.EMPTY_TRIE_HASH);
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
}
