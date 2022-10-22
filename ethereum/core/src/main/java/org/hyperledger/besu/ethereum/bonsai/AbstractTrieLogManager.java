package org.hyperledger.besu.ethereum.bonsai;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTrieLogManager<T extends AbstractTrieLogManager.CachedLayer> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractTrieLogManager.class);
  public static final long RETAINED_LAYERS = 512; // at least 256 + typical rollbacks

  protected final Blockchain blockchain;
  protected final BonsaiWorldStateKeyValueStorage worldStateStorage;

  protected final Map<Bytes32, T> cachedWorldStatesByHash;
  protected final long maxLayersToLoad;

  public AbstractTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, T> cachedWorldStatesByHash) {
    this.blockchain = blockchain;
    this.worldStateStorage = worldStateStorage;
    this.cachedWorldStatesByHash = cachedWorldStatesByHash;
    this.maxLayersToLoad = maxLayersToLoad;
  }

  public synchronized void saveTrieLog(
      final BonsaiWorldStateArchive worldStateArchive,
      final BonsaiWorldStateUpdater localUpdater,
      final Hash worldStateRootHash,
      final BlockHeader blockHeader) {
    // do not overwrite a trielog layer that already exists in the database.
    // if it's only in memory we need to save it
    // for example, like that in case of reorg we don't replace a trielog layer
    if (worldStateStorage.getTrieLog(blockHeader.getHash()).isEmpty()) {
      final BonsaiWorldStateKeyValueStorage.BonsaiUpdater stateUpdater =
          worldStateStorage.updater();
      boolean success = false;
      try {
        final TrieLogLayer trieLog =
            prepareTrieLog(blockHeader, worldStateRootHash, localUpdater, worldStateArchive);
        persistTrieLog(blockHeader, worldStateRootHash, trieLog, stateUpdater);
        success = true;
      } finally {
        if (success) {
          stateUpdater.commit();
        } else {
          stateUpdater.rollback();
        }
      }
    }
  }

  private TrieLogLayer prepareTrieLog(
      final BlockHeader blockHeader,
      final Hash currentWorldStateRootHash,
      final BonsaiWorldStateUpdater localUpdater,
      final BonsaiWorldStateArchive worldStateArchive) {
    debugLambda(LOG, "Adding layered world state for {}", blockHeader::toLogString);
    final TrieLogLayer trieLog = localUpdater.generateTrieLog(blockHeader.getBlockHash());
    trieLog.freeze();
    addCachedLayer(blockHeader, currentWorldStateRootHash, trieLog, worldStateArchive);
    scrubCachedLayers(blockHeader.getNumber());
    return trieLog;
  }

  public synchronized void scrubCachedLayers(final long newMaxHeight) {
    final long waterline = newMaxHeight - RETAINED_LAYERS;
    cachedWorldStatesByHash.values().stream()
        .filter(layer -> layer.getHeight() < waterline)
        .collect(Collectors.toList())
        .stream()
        .forEach(
            layer -> {
              cachedWorldStatesByHash.remove(layer.getTrieLog().getBlockHash());
              try {
                layer.getMutableWorldState().close();
              } catch (Exception e) {
                LOG.warn("Error closing bonsai worldstate layer", e);
              }
            });
  }

  private void persistTrieLog(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateKeyValueStorage.BonsaiUpdater stateUpdater) {
    debugLambda(
        LOG,
        "Persisting trie log for block hash {} and world state root {}",
        blockHeader::toLogString,
        worldStateRootHash::toHexString);
    final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
    trieLog.writeTo(rlpLog);
    stateUpdater
        .getTrieLogStorageTransaction()
        .put(blockHeader.getHash().toArrayUnsafe(), rlpLog.encoded().toArrayUnsafe());
  }

  public Optional<MutableWorldState> getBonsaiLayeredWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      return Optional.of(cachedWorldStatesByHash.get(blockHash).getMutableWorldState());
    }
    return Optional.empty();
  }

  public long getMaxLayersToLoad() {
    return maxLayersToLoad;
  }

  public abstract void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateArchive worldStateArchive);

  public abstract void updateCachedLayers(final Hash blockParentHash, final Hash blockHash);

  public Optional<TrieLogLayer> getTrieLogLayer(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      return Optional.of(cachedWorldStatesByHash.get(blockHash).getTrieLog());
    } else {
      return worldStateStorage.getTrieLog(blockHash).map(TrieLogLayer::fromBytes);
    }
  }

  public interface CachedLayer {
    long getHeight();

    TrieLogLayer getTrieLog();

    MutableWorldState getMutableWorldState();
  }
}
