package org.hyperledger.besu.ethereum.bonsai.snapshot;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldView;
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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * This class attempts to roll forward or backward trielog layers in-memory to maintain a
 * consistent view of a particular worldstate/stateroot.
 */
public class BonsaiSnapshotWorldState implements MutableWorldState, BonsaiWorldView, WorldState {

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
      final TrieLogLayer trieLog
  ) {
    return blockchain.getBlockHeader(blockHash)
        .map(header ->
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
    return headBlockHeader().getHash();
  }

  Hash headBlockParentHash() {
    return headBlockHeader().getParentHash();
  }

  BlockHeader headBlockHeader() {
    // TODO: expose persistedWorldState blockhash in BonsaiWorldStateArchive rather than relying on blockchain
    return blockchain.getChainHeadHeader();
  }

  Optional<Stream<TrieLogLayer>> pathFromHead() {
    return archive.getTrieLogLayer(headBlockParentHash())
        .map(this::pathFromHash);
  }

  /**
   * pathFromHash will return a stream of trie logs to apply, in order, to arrive back at OUR worldstate.
   * Null response implies there is no path from hash.
   */
  Stream<TrieLogLayer> pathFromHash(final TrieLogLayer fromTrieLog) {
    if (fromTrieLog == null) {
      return null;
    } else if (blockHash.equals(fromTrieLog.getBlockHash())) {
      return Stream.of(trieLog);
    }

    // TODO: this assumes a common history between the hashes, add hash check and ancestor selection for forks
    return blockchain.getBlockHeader(fromTrieLog.getBlockHash())
        .flatMap(headHeader -> (headHeader.getNumber() > blockNumber) ?
            // hash is ahead of us, recurse with head's parent
            archive.getTrieLogLayer(headHeader.getParentHash()).map(headParentLog ->
                Streams.concat(pathFromHash(headParentLog), Stream.of(fromTrieLog))) :
            // hash is behind us recurse with our parent
            archive.getTrieLogLayer(parentHash).map(ourPriorLog ->
                Streams.concat(pathFromHash(ourPriorLog), Stream.of(fromTrieLog))))
        .orElse(null);
  }


  @Override
  public Optional<Bytes> getCode(final Address address) {
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getStateTrieNode(final Bytes location) {
    return Optional.empty();
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 key) {
    return null;
  }

  @Override
  public Optional<UInt256> getStorageValueBySlotHash(final Address address, final Hash slotHash) {
    return Optional.empty();
  }

  @Override
  public UInt256 getPriorStorageValue(final Address address, final UInt256 key) {
    return null;
  }

  @Override
  public Map<Bytes32, Bytes> getAllAccountStorage(final Address address, final Hash rootHash) {
    return null;
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
      Account mutatedAccount = Optional.ofNullable(trieLog)
          .filter(log -> log.getAccount(address).isPresent())
          .map(Optional::of)
          .orElseGet(() -> pathFromHead()
              .flatMap(logs -> logs
                  .filter(log -> log.getAccount(address).isPresent())
                  .reduce((a, b) -> b)))
          .flatMap(earliestLog -> (earliestLog.getBlockHash().equals(blockHash)) ?
              earliestLog.getAccount(address):
              earliestLog.getPriorAccount(address))
          .map(val -> (Account) new BonsaiSnapshotAccount(
              //TODO: load code and storage, ugh.
              address, val.getNonce(), val.getBalance(), null, null))
          .orElse(null);
      mutatedAccounts.put(address, mutatedAccount);
      return mutatedAccount;
    }
  }

}
