package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.plugin.BesuContext;

public class VerkleTransitionWorldStateProvider
    implements WorldStateArchive/*, VerkleTransitionContext.VerkleTransitionSubscriber */ {

  private final BonsaiWorldStateProvider bonsaiWorldStateProvider;
  private final VerkleWorldStateProvider verkleWorldStateProvider;
  private final long verkleTimestamp;
  public VerkleTransitionWorldStateProvider(
      // TODO: clean this up
      final BonsaiWorldStateProvider bonsaiWorldStateProvider,
      final VerkleWorldStateProvider verkleWorldStateProvider,
      final long verkleTransitionTimestamp) {
    this.bonsaiWorldStateProvider = bonsaiWorldStateProvider;
    this.verkleWorldStateProvider = verkleWorldStateProvider;
    this.verkleTimestamp = verkleTransitionTimestamp;
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    // TODO: use verkle context to determine which to use
    return bonsaiWorldStateProvider.get(rootHash, blockHash);
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    // TODO: use verkle context to determine which to use
    return bonsaiWorldStateProvider.isWorldStateAvailable(rootHash, blockHash);
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final BlockHeader blockHeader, final boolean isPersistingState) {
    // TODO: use verkle context to determine which to use
    return bonsaiWorldStateProvider.getMutable(blockHeader, isPersistingState);
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      BlockHeader blockHeader, boolean isPersistingState, long timestamp) {
    //TODO: use verkle transition context here
    if (timestamp < verkleTimestamp) {
      return bonsaiWorldStateProvider.getMutable(blockHeader, isPersistingState);
    } else {

      //TODO immediately: plumb a fallback flatdbreader

      return verkleWorldStateProvider.getMutable(blockHeader, isPersistingState);



      if (isPersistingState) {
        return getMutable(blockHeader.getStateRoot(), blockHeader.getHash());
      } else {
        final BlockHeader chainHeadBlockHeader = blockchain.getChainHeadHeader();
        if (chainHeadBlockHeader.getNumber() - blockHeader.getNumber()
            >= trieLogManager.getMaxLayersToLoad()) {
          LOG.warn(
              "Exceeded the limit of historical blocks that can be loaded ({}). If you need to make older historical queries, configure your `--bonsai-historical-block-limit`.",
              trieLogManager.getMaxLayersToLoad());
          return Optional.empty();
        }
        return verkleWorldStateProvider.getCachedWorldStorageManager()
            .getWorldState(blockHeader.getHash())
            .or(() -> verkleWorldStateProvider.getCachedWorldStorageManager().getNearestWorldState(blockHeader))
            .or(() -> verkleWorldStateProvider.getCachedWorldStorageManager().getHeadWorldState(blockchain::getBlockHeader))
            .flatMap(worldState -> verkleWorldStateProvider.rollMutableStateToBlockHash(worldState, blockHeader.getHash()))
            .map(MutableWorldState::freeze);
      }





    }
  }


  @Override
  public Optional<MutableWorldState> getMutable(final Hash rootHash, final Hash blockHash) {
    // TODO: use verkle context to determine which to use
    return bonsaiWorldStateProvider.getMutable(rootHash, blockHash);
  }

  @Override
  public MutableWorldState getMutable() {
    // TODO: use verkle context to determine which to use
    return bonsaiWorldStateProvider.getMutable();
  }

  @Override
  public void resetArchiveStateTo(final BlockHeader blockHeader) {
    // TODO: write me
  }

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    // TODO: will we serve eth/63 GET_NODE_DATA in verkle?
    return Optional.empty();
  }

  @Override
  public <U> Optional<U> getAccountProof(
      final BlockHeader blockHeader,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys,
      final Function<Optional<WorldStateProof>, ? extends Optional<U>> mapper) {
    // TODO: will we ever serve eth_getProof from verkle?  that is the only use for this
    return Optional.empty();
  }

  @Override
  public void close() throws IOException {
    try {
      bonsaiWorldStateProvider.close();
      verkleWorldStateProvider.close();
    } catch (Exception e) {
      // no op
    }
  }

//  @Override
//  public void onTransitionStarted() {
//    // TODO: write me
//  }
//
//  @Override
//  public void onTransitionReverted() {
//    // TODO: write me
//  }
//
//  @Override
//  public void onTransitionFinalized() {
//    // TODO: write me
//  }
}
