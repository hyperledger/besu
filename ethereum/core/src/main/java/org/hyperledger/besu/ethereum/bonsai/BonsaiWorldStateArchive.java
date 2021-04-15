/*
 * Copyright ConsenSys AG.
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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiWorldStateArchive implements WorldStateArchive {

  private static final Logger LOG = LogManager.getLogger();

  static final long RETAINED_LAYERS = 512; // at least 256 + typical rollbacks

  private final Blockchain blockchain;

  private final BonsaiPersistedWorldState persistedState;
  private final Map<Bytes32, BonsaiLayeredWorldState> layeredWorldStatesByHash;
  private final BonsaiWorldStateKeyValueStorage worldStateStorage;

  public BonsaiWorldStateArchive(final StorageProvider provider, final Blockchain blockchain) {
    this.blockchain = blockchain;

    worldStateStorage = new BonsaiWorldStateKeyValueStorage(provider);
    persistedState = new BonsaiPersistedWorldState(this, worldStateStorage);
    layeredWorldStatesByHash = new HashMap<>();
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    if (layeredWorldStatesByHash.containsKey(rootHash)) {
      return Optional.of(layeredWorldStatesByHash.get(blockHash));
    } else if (rootHash.equals(persistedState.blockHash())) {
      return Optional.of(persistedState);
    } else {
      return Optional.empty();
    }
  }

  public void addLayeredWorldState(
      final BonsaiWorldView persistedWorldState,
      final long blockNumber,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog) {
    final BonsaiLayeredWorldState bonsaiLayeredWorldState =
        new BonsaiLayeredWorldState(
            blockchain,
            this,
            Optional.of(persistedWorldState),
            blockNumber,
            worldStateRootHash,
            trieLog);
    layeredWorldStatesByHash.put(bonsaiLayeredWorldState.blockHash(), bonsaiLayeredWorldState);
    if (blockNumber > 0) {
      final Optional<Hash> blockHashByNumber = blockchain.getBlockHashByNumber(blockNumber - 1);
      if (blockHashByNumber.isPresent()
          && layeredWorldStatesByHash.containsKey(blockHashByNumber.get())) {
        layeredWorldStatesByHash
            .get(blockHashByNumber.get())
            .setNextWorldView(Optional.of(bonsaiLayeredWorldState));
      }
    }
  }

  public Optional<TrieLogLayer> getTrieLogLayer(final Hash blockHash) {
    if (layeredWorldStatesByHash.containsKey(blockHash)) {
      return Optional.of(layeredWorldStatesByHash.get(blockHash).getTrieLog());
    } else {
      return worldStateStorage.getTrieLog(blockHash).map(TrieLogLayer::fromBytes);
    }
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {

    LOG.info("Read trie log");
    Optional<byte[]> trieLog =
        worldStateStorage.getTrieLog(
            Hash.fromHexString(
                "0x5f4ba732ab24b4c9328712d77533b3846416231859061016408c35a78d534569"));
    LOG.info("End Read trie log " + trieLog.isPresent());
    if (trieLog.isPresent()) {
      LOG.debug("Start test");
      final Optional<TrieLogLayer> trieLogLayer = trieLog.map(TrieLogLayer::fromBytes);
      if (trieLogLayer.isPresent()) {
        LOG.info("Start write test");
        final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
        trieLogLayer.get().writeTo(rlpLog);
        LOG.info("End write test");
      }
      LOG.debug("End start test");
    }

    return layeredWorldStatesByHash.containsKey(blockHash)
        || persistedState.blockHash().equals(blockHash)
        || worldStateStorage.isWorldStateAvailable(rootHash, blockHash);
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final long blockNumber, final boolean isPersistingState) {
    final Optional<Hash> blockHashByNumber = blockchain.getBlockHashByNumber(blockNumber);
    if (blockHashByNumber.isPresent()) {
      return getMutable(null, blockHashByNumber.get(), isPersistingState);
    }
    return Optional.empty();
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final Hash rootHash, final Hash blockHash, final boolean isPersistingState) {
    if (!isPersistingState) {
      if (layeredWorldStatesByHash.containsKey(blockHash)) {
        return Optional.of(layeredWorldStatesByHash.get(blockHash));
      } else {
        final Optional<TrieLogLayer> trieLogLayer = getTrieLogLayer(blockHash);
        if (trieLogLayer.isPresent()) {
          final BlockHeader header = blockchain.getBlockHeader(blockHash).get();
          return Optional.of(
              new BonsaiLayeredWorldState(
                  blockchain,
                  this,
                  Optional.empty(),
                  header.getNumber(),
                  blockHash,
                  trieLogLayer.get()));
        }
      }
    } else {
      return getMutable(rootHash, blockHash);
    }
    return Optional.empty();
  }

  @Override
  public Optional<MutableWorldState> getMutable(final Hash rootHash, final Hash blockHash) {
    if (blockHash.equals(persistedState.blockHash())) {
      return Optional.of(persistedState);
    } else {
      try {
        BlockHeader persistedHeader = blockchain.getBlockHeader(persistedState.blockHash()).get();
        BlockHeader targetHeader = blockchain.getBlockHeader(blockHash).get();

        final List<TrieLogLayer> rollBacks = new ArrayList<>();
        final List<TrieLogLayer> rollForwards = new ArrayList<>();

        // roll back from persisted to even with target
        while (persistedHeader.getNumber() > targetHeader.getNumber()) {
          LOG.debug("Rollback {}", persistedHeader.getHash());
          rollBacks.add(getTrieLogLayer(persistedHeader.getHash()).get());
          persistedHeader = blockchain.getBlockHeader(persistedHeader.getParentHash()).get();
        }
        // roll forward to target
        while (persistedHeader.getNumber() < targetHeader.getNumber()) {
          LOG.debug("Rollforward {}", targetHeader.getHash());
          rollForwards.add(getTrieLogLayer(targetHeader.getHash()).get());
          targetHeader = blockchain.getBlockHeader(targetHeader.getParentHash()).get();
        }

        // roll back in tandem until we hit a shared state
        while (!persistedHeader.getHash().equals(targetHeader.getHash())) {
          LOG.debug("Paired Rollback {}", persistedHeader.getHash());
          LOG.debug("Paired Rollforward {}", targetHeader.getHash());
          rollForwards.add(getTrieLogLayer(targetHeader.getHash()).get());
          targetHeader = blockchain.getBlockHeader(targetHeader.getParentHash()).get();

          rollBacks.add(getTrieLogLayer(persistedHeader.getHash()).get());
          persistedHeader = blockchain.getBlockHeader(persistedHeader.getParentHash()).get();
        }

        // attempt the state rolling
        final BonsaiWorldStateUpdater bonsaiUpdater =
            (BonsaiWorldStateUpdater) persistedState.updater();
        try {
          for (final TrieLogLayer rollBack : rollBacks) {
            LOG.debug("Attempting Rollback of {}", rollBack.getBlockHash());
            bonsaiUpdater.rollBack(rollBack);
          }
          for (int i = rollForwards.size() - 1; i >= 0; i--) {
            LOG.debug("Attempting Rollforward of {}", rollForwards.get(i).getBlockHash());
            bonsaiUpdater.rollForward(rollForwards.get(i));
          }
          bonsaiUpdater.commit();
          persistedState.persist(blockchain.getBlockHeader(blockHash).get());
          LOG.debug("Archive rolling finished, now at {}", blockHash);
          return Optional.of(persistedState);
        } catch (final Exception e) {
          // if we fail we must clean up the updater
          bonsaiUpdater.reset();
          throw new RuntimeException(e);
        }
      } catch (final RuntimeException re) {
        re.printStackTrace(System.out);
        return Optional.empty();
      }
    }
  }

  @Override
  public MutableWorldState getMutable() {
    return persistedState;
  }

  @Override
  public void setArchiveStateUnSafe(final BlockHeader blockHeader) {
    persistedState.setArchiveStateUnSafe(blockHeader);
  }

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    return Optional.empty();
  }

  @Override
  public Optional<WorldStateProof> getAccountProof(
      final Hash worldStateRoot,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys) {
    // FIXME we can do proofs for layered tries and the persisted trie
    return Optional.empty();
  }

  void scrubLayeredCache(final long newMaxHeight) {
    final long waterline = newMaxHeight - RETAINED_LAYERS;
    layeredWorldStatesByHash.entrySet().removeIf(entry -> entry.getValue().getHeight() < waterline);
  }
}
