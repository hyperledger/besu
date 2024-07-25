package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
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
    implements WorldStateArchive, VerkleTransitionContext.VerkleTransitionSubscriber {

  private final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage;

  public VerkleTransitionWorldStateProvider(
      final DiffBasedWorldStateKeyValueStorage worldStateKeyValueStorage,
      final Blockchain blockchain,
      final Optional<Long> maxLayersToLoad,
      final BonsaiCachedMerkleTrieLoader bonsaiCachedMerkleTrieLoader,
      final BesuContext pluginContext,
      final EvmConfiguration evmConfiguration  ) {
    this.worldStateKeyValueStorage = worldStateKeyValueStorage;
    // TODO: finish constructor
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    // TODO: write me
    return Optional.empty();
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    // TODO: write me
    return false;
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final BlockHeader blockHeader, final boolean isPersistingState) {
    // TODO: write me
    return Optional.empty();
  }

  @Override
  public Optional<MutableWorldState> getMutable(final Hash rootHash, final Hash blockHash) {
    // TODO: write me
    return Optional.empty();
  }

  @Override
  public MutableWorldState getMutable() {
    // TODO: write me
    return null;
  }

  @Override
  public void resetArchiveStateTo(final BlockHeader blockHeader) {
    // TODO: write me
  }

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    // TODO: write me
    return Optional.empty();
  }

  @Override
  public <U> Optional<U> getAccountProof(
      final BlockHeader blockHeader,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys,
      final Function<Optional<WorldStateProof>, ? extends Optional<U>> mapper) {
    // TODO: write me
    return Optional.empty();
  }

  @Override
  public void close() throws IOException {
    try {
      worldStateKeyValueStorage.close();
    } catch (Exception e) {
      // no op
    }
  }

  @Override
  public void onTransitionStarted() {
    // TODO: write me
  }

  @Override
  public void onTransitionReverted() {
    // TODO: write me
  }

  @Override
  public void onTransitionFinalized() {
    // TODO: write me
  }
}
