package org.hyperledger.besu.ethereum.bonsai;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;

import java.util.Map;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotTrieLogManager
    extends AbstractTrieLogManager<SnapshotTrieLogManager.CachedSnapshotWorldState> {
  private static final Logger LOG = LoggerFactory.getLogger(SnapshotTrieLogManager.class);

  public SnapshotTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, CachedSnapshotWorldState> cachedWorldStatesByHash) {
    super(blockchain, worldStateStorage, maxLayersToLoad, cachedWorldStatesByHash);
  }

  @Override
  public void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateArchive worldStateArchive) {

    debugLambda(
        LOG,
        "adding snapshot world state for block {}, state root hash {}",
        blockHeader::toLogString,
        worldStateRootHash::toHexString);
    worldStateArchive
        .getMutableSnapshot(worldStateRootHash)
        .map(BonsaiSnapshotWorldState.class::cast)
        .ifPresent(
            snapshot ->
                cachedWorldStatesByHash.put(
                    blockHeader.getHash(),
                    new CachedSnapshotWorldState(snapshot, trieLog, blockHeader.getNumber())));
  }

  @Override
  public void updateCachedLayers(final Hash blockParentHash, final Hash blockHash) {
    // no-op.  Snapshots are independent and do not need to update 'next' worldstates
  }

  public static class CachedSnapshotWorldState implements CachedLayer {

    final BonsaiSnapshotWorldState snapshot;
    final TrieLogLayer trieLog;
    final long height;

    public CachedSnapshotWorldState(
        final BonsaiSnapshotWorldState snapshot, final TrieLogLayer trieLog, final long height) {
      this.snapshot = snapshot;
      this.trieLog = trieLog;
      this.height = height;
    }

    @Override
    public long getHeight() {
      return height;
    }

    @Override
    public TrieLogLayer getTrieLog() {
      return trieLog;
    }

    @Override
    public MutableWorldState getMutableWorldState() {
      return snapshot;
    }
  }
}
