package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes32;

public class Tracer {

  public static <TRACE> Optional<TRACE> processTracing(
      final BlockchainQueries blockchainQueries,
      final Hash blockHash,
      final Function<TraceableState, ? extends Optional<TRACE>> mapper) {
    return processTracing(
        blockchainQueries, blockchainQueries.getBlockHeaderByHash(blockHash), mapper);
  }

  public static <TRACE> Optional<TRACE> processTracing(
      final BlockchainQueries blockchainQueries,
      final Optional<BlockHeader> block,
      final Function<TraceableState, ? extends Optional<TRACE>> mapper) {
    return block
        .map(BlockHeader::getParentHash)
        .flatMap(
            parentHash ->
                blockchainQueries.getAndMapWorldState(
                    parentHash,
                    mutableWorldState -> mapper.apply(new TraceableState(mutableWorldState))));
  }

  /**
   * This class force the use of the processTracing method to do tracing. processTracing allows you
   * to cleanly manage the worldstate, to close it etc
   */
  public static class TraceableState implements MutableWorldState {
    private final MutableWorldState mutableWorldState;

    private TraceableState(final MutableWorldState mutableWorldState) {
      this.mutableWorldState = mutableWorldState;
    }

    @Override
    public void persist(final BlockHeader blockHeader) {
      mutableWorldState.persist(blockHeader);
    }

    @Override
    public WorldUpdater updater() {
      return mutableWorldState.updater();
    }

    @Override
    public Hash rootHash() {
      return mutableWorldState.rootHash();
    }

    @Override
    public Hash frontierRootHash() {
      return mutableWorldState.rootHash();
    }

    @Override
    public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
      return mutableWorldState.streamAccounts(startKeyHash, limit);
    }

    @Override
    public Account get(final Address address) {
      return mutableWorldState.get(address);
    }

    @Override
    public MutableWorldState freeze() {
      return mutableWorldState.freeze();
    }

    @Override
    public void close() throws Exception {
      mutableWorldState.close();
    }
  }
}
