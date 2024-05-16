package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class VerkleTransitionWorldStateProvider
    implements WorldStateArchive, VerkleTransitionContext.VerkleTransitionSubscriber {
  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    return Optional.empty();
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    return false;
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final BlockHeader blockHeader, final boolean isPersistingState) {
    return Optional.empty();
  }

  @Override
  public Optional<MutableWorldState> getMutable(final Hash rootHash, final Hash blockHash) {
    return Optional.empty();
  }

  @Override
  public MutableWorldState getMutable() {
    return null;
  }

  @Override
  public void resetArchiveStateTo(final BlockHeader blockHeader) {}

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    return Optional.empty();
  }

  @Override
  public <U> Optional<U> getAccountProof(
      final BlockHeader blockHeader,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys,
      final Function<Optional<WorldStateProof>, ? extends Optional<U>> mapper) {
    return Optional.empty();
  }

  @Override
  public void close() throws IOException {}

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
