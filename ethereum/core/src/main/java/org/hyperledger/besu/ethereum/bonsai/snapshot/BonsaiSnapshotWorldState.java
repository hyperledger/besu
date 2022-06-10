package org.hyperledger.besu.ethereum.bonsai.snapshot;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.TrieLogLayer;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import org.apache.tuweni.bytes.Bytes32;

/**
 * This class attempts to roll forward or backward trielog layers in-memory to maintain a consistent
 * view of a particular worldstate/stateroot.
 */
public class BonsaiSnapshotWorldState implements MutableWorldState, WorldState {

  protected final TrieLogLayer trieLog;
  private final Hash worldStateRootHash;
  private final Hash blockHash;
  private final Hash parentHash;
  private final long blockNumber;

  private final Map<Address, Account> mutatedAccounts = new HashMap<>();

  private final Blockchain blockchain;
  private final BonsaiWorldStateArchive archive;

  BonsaiSnapshotWorldState(
      final Blockchain blockchain,
      final BonsaiWorldStateArchive archive,
      final Hash worldStateRootHash,
      final Hash blockHash,
      final Hash parentHash,
      final long blockNumber,
      final TrieLogLayer trieLog) {
    this.blockchain = blockchain;
    this.archive = archive;
    this.worldStateRootHash = worldStateRootHash;
    this.blockHash = blockHash;
    this.parentHash = parentHash;
    this.blockNumber = blockNumber;
    this.trieLog = trieLog;
  }

  public static Optional<BonsaiSnapshotWorldState> create(
      final Blockchain blockchain,
      final BonsaiWorldStateArchive archive,
      final Hash blockHash,
      final TrieLogLayer trieLog) {
    return blockchain
        .getBlockHeader(blockHash)
        .map(
            header ->
                new BonsaiSnapshotWorldState(
                    blockchain,
                    archive,
                    header.getStateRoot(),
                    blockHash,
                    header.getParentHash(),
                    header.getNumber(),
                    trieLog));
  }

  Hash headBlockHash() {
    return getHeadBlockHeader().getHash();
  }

  Hash headBlockParentHash() {
    return getHeadBlockHeader().getParentHash();
  }

  BlockHeader getHeadBlockHeader() {
    // TODO: expose persistedWorldState blockhash in BonsaiWorldStateArchive rather than relying on
    // blockchain
    // TODO: rewinding to genesis breaks assumptions about trielog layer existence, highly unlikely
    // corner case
    return blockchain.getChainHeadHeader();
  }

  Optional<Stream<TrieLogLayer>> pathFromHead() {
    BlockHeader headHeader = getHeadBlockHeader();
    // TODO: we do not deal with forks here, need to add handling for that
    if (headHeader.getNumber() == blockNumber) {
      return Optional.of(Stream.empty());
    } else if (headHeader.getNumber() > blockNumber) {
      return archive
          .getTrieLogLayer(headHeader.getBlockHash())
          .or(() -> archive.getTrieLogLayer(headHeader.getParentHash()))
          .map(this::pathBackFromHash);
    } else {
      return archive
          .getTrieLogLayer(parentHash)
          .map(priorTrieLog -> pathBackwardFromSnapshot(headHeader.getHash(), priorTrieLog));
    }
  }

  /**
   * pathBackFromHash will return a stream of trie logs to apply to the state represented by
   * fromTrieLog, in order, to arrive back at OUR worldstate.
   *
   * <p>A null response implies there is no path from hash.
   */
  Stream<TrieLogLayer> pathBackFromHash(final TrieLogLayer sourceTrieLog) {
    if (sourceTrieLog == null) {
      return null;
    } else if (blockHash.equals(sourceTrieLog.getBlockHash())) {
      // TODO: this assumes a common history between the hashes, add a hash check and ancestor
      // selection if we are on different forks
      // empty because there is nothing to apply to head once we are there
      return Stream.empty();
    }

    // TODO: limit our lookback to the configured max layers back
    return blockchain
        .getBlockHeader(sourceTrieLog.getBlockHash())
        .flatMap(
            fromHeader ->
                archive
                    .getTrieLogLayer(fromHeader.getParentHash())
                    .map(
                        priorTrieLog ->
                            Streams.concat(
                                Stream.of(sourceTrieLog), pathBackFromHash(priorTrieLog))))
        .orElse(null);
  }

  /**
   * pathBackFromHash will return a stream of trie logs to apply to the state represented by
   * targetHash, in order, to arrive back at OUR "future" worldstate.
   *
   * <p>A null response implies there is no path from hash.
   */
  Stream<TrieLogLayer> pathBackwardFromSnapshot(
      final Hash targetHash, final TrieLogLayer sourceTrieLog) {
    if (targetHash == null) {
      return null;
    } else if (targetHash.equals(sourceTrieLog.getBlockHash())) {
      // TODO: this assumes a common history between the hashes, add a hash check and ancestor
      // selection if we are on different forks
      return Stream.of(sourceTrieLog);
    }

    // TODO: limit our lookback to the configured max layers back
    return blockchain
        .getBlockHeader(sourceTrieLog.getBlockHash())
        .flatMap(
            fromHeader ->
                archive
                    .getTrieLogLayer(fromHeader.getParentHash())
                    .map(
                        priorTrieLog ->
                            Streams.concat(
                                pathBackwardFromSnapshot(targetHash, priorTrieLog),
                                Stream.of(sourceTrieLog))))
        .orElse(null);
  }

  @Override
  public MutableWorldState copy() {
    // all snapshot storage is immutable, just return this
    return this;
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    throw new UnsupportedOperationException("Bonsai snapshot does not implement persist");
  }

  @Override
  public WorldUpdater updater() {
    return null;
  }

  @Override
  public Hash rootHash() {
    return worldStateRootHash;
  }

  @Override
  public Hash frontierRootHash() {
    return rootHash();
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    throw new UnsupportedOperationException("Bonsai snapshot does not support streaming accounts");
  }

  @Override
  public Account get(final Address address) {
    if (mutatedAccounts.containsKey(address)) {
      return mutatedAccounts.get(address);
    }

    Account headVal = archive.getMutable().get(address);
    if (headBlockHash().equals(blockHash)) {
      return headVal;
    } else {
      Account mutatedAccount =
          Optional.ofNullable(trieLog)
              .filter(log -> log.getAccount(address).isPresent())
              .map(Optional::of)
              .orElseGet(
                  () ->
                      pathFromHead()
                          .flatMap(
                              logs ->
                                  logs.filter(log -> log.getAccount(address).isPresent())
                                      .reduce((a, b) -> b)))
              .flatMap(
                  earliestLog ->
                      (earliestLog.getBlockHash().equals(blockHash))
                          ? earliestLog.getAccount(address)
                          : earliestLog.getPriorAccount(address))
              .map(
                  val ->
                      (Account)
                          new BonsaiSnapshotAccount(
                              // TODO: load code and storage, ugh.
                              address, val.getNonce(), val.getBalance(), null, null))
              .orElse(null);
      mutatedAccounts.put(address, mutatedAccount);
      return mutatedAccount;
    }
  }
}
