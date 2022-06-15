/*
 * Copyright Hyperledger Besu contributors.
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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * This class attempts to roll forward or backward trielog layers in-memory to maintain a consistent
 * view of a particular worldstate/stateroot.
 */
public class BonsaiSnapshotWorldState implements MutableWorldState, WorldState, WorldUpdater {

  protected final TrieLogLayer trieLog;
  private final Hash worldStateRootHash;
  private final Hash blockHash;
  private final Hash parentHash;
  private final long blockNumber;

  private final Map<Address, Optional<BonsaiSnapshotAccount>> accumulatedAccounts = new HashMap<>();
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

  StorageException noPathFrom(final Hash hash) {
    return new StorageException(
        String.format(
            "No path from hash %s to this log blockhash %s",
            hash.toHexString(), blockHash.toHexString()));
  }

  Stream<TrieLogLayer> pathFromHead() {
    // TODO: implement path caching
    BlockHeader headHeader = getHeadBlockHeader();
    // TODO: we do not deal with forks here, need to add handling for that
    if (headHeader.getNumber() == blockNumber) {
      return Stream.empty();
    } else if (headHeader.getNumber() > blockNumber) {
      return archive
          .getTrieLogLayer(headHeader.getBlockHash())
          .or(() -> archive.getTrieLogLayer(headHeader.getParentHash()))
          .map(this::pathBackFromHash)
          .orElseThrow(() -> noPathFrom(headHeader.getHash()));
    } else {
      return archive
          .getTrieLogLayer(parentHash)
          .map(priorTrieLog -> pathBackwardFromSnapshot(headHeader.getHash(), priorTrieLog))
          .orElseThrow(() -> noPathFrom(headHeader.getHash()));
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
      //       selection if we are on different forks.

      // Empty because there is nothing to apply to head once we are there
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
      //       selection if we are on different forks.
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
    return this;
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
    return getAccount(address);
  }

  private Optional<StateTrieAccountValue> getLatestStateTrieAccountValue(final Address address) {
    return resolveValueFromPath(address, TrieLogLayer::getAccount, TrieLogLayer::getPriorAccount);
  }

  private Optional<Bytes> getLatestCodeFromTries(final Address address) {
    return resolveValueFromPath(address, TrieLogLayer::getCode, TrieLogLayer::getPriorCode);
  }

  private Optional<BonsaiValue<UInt256>> getLatestStorageSlotFromTries(
      final Address address, final UInt256 key) {
    // derive from path looking for storage
    return bonsaiValueFromPath(
        address,
        (log, k) -> log.getStorageBySlotHash(address, Hash.hash(k)),
        (log, k) -> log.getPriorStorageBySlotHash(address, Hash.hash(k)));
  }

  <T> Optional<T> resolveValueFromPath(
      final Address address,
      final BiFunction<TrieLogLayer, Address, Optional<T>> logCurrent,
      final BiFunction<TrieLogLayer, Address, Optional<T>> logPrior) {

    return trieLogLayerFromPath(address, logCurrent)
        .flatMap(
            mostRecentLog -> {
              var mapper = (mostRecentLog.getBlockHash().equals(blockHash)) ? logCurrent : logPrior;
              return mapper.apply(mostRecentLog, address);
            });
  }

  <T> Optional<BonsaiValue<T>> bonsaiValueFromPath(
      final Address address,
      final BiFunction<TrieLogLayer, Address, Optional<T>> logCurrent,
      final BiFunction<TrieLogLayer, Address, Optional<T>> logPrior) {
    // check to see if our current log layer has a value for this type
    return trieLogLayerFromPath(address, logCurrent)
        .map(
            log ->
                new BonsaiValue<>(
                    logCurrent.apply(log, address).orElse(null),
                    logPrior.apply(log, address).orElse(null)));
  }

  <T> Optional<TrieLogLayer> trieLogLayerFromPath(
      final Address address,
      final BiFunction<TrieLogLayer, Address, Optional<T>> isPresentInLayer) {
    // see if our snapshot trielog contains a change for this address:
    return Optional.ofNullable(trieLog)
        .filter(log -> isPresentInLayer.apply(log, address).isPresent())
        .map(Optional::of)
        // otherwise try to fetch it from the stream of logs, in case we are ahead of head
        .orElseGet(
            () ->
                pathFromHead().filter(log -> log.getCode(address).isPresent()).reduce((a, b) -> b));
  }

  /* WorldUpdater methods */

  @Override
  public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
    final BonsaiValue<Bytes> codeValue = new BonsaiValue<>(null, null);
    final Map<UInt256, BonsaiValue<UInt256>> storageMap = new HashMap<>();
    var ephemeralAccount = new BonsaiSnapshotAccount(
      address,
      nonce,
      balance,
      __ -> Optional.ofNullable(codeValue.getUpdated()),
      (addr, slot) -> Optional.ofNullable(storageMap.get(slot)));
    accumulatedAccounts.put(address, Optional.of(ephemeralAccount));
    return ephemeralAccount;
  }

  BonsaiSnapshotAccount cloneAccount(final Account account) {
    if (accumulatedAccounts.containsKey(account.getAddress())) {
      throw new StorageException(String.format("Account %s already exists", account.getAddress().toHexString()));
    }

    var ephemeralAccount = new BonsaiSnapshotAccount(
      account.getAddress(),
      account.getNonce(),
      account.getBalance(),
      this::getLatestCodeFromTries,
      this::getLatestStorageSlotFromTries);
    accumulatedAccounts.put(account.getAddress(), Optional.of(ephemeralAccount));
    return ephemeralAccount;
  }

  @Override
  public void deleteAccount(final Address address) {
    // use empty optional to indicate a deleted account
    accumulatedAccounts.put(address, Optional.empty());
  }

  @Override
  public EvmAccount getAccount(final Address address) {
    if (accumulatedAccounts.containsKey(address) && accumulatedAccounts.get(address).isPresent()) {
      return accumulatedAccounts.get(address).get();
    }

    Account headVal = archive.getMutable().get(address);

    if (headBlockHash().equals(blockHash)) {
      // clone account for mutation:
      return cloneAccount(headVal);
    } else {
      Optional<BonsaiSnapshotAccount> mutatedAccount =
        getLatestStateTrieAccountValue(address)
          .map(
            val ->
              new BonsaiSnapshotAccount(
                address,
                val.getNonce(),
                val.getBalance(),
                this::getLatestCodeFromTries,
                this::getLatestStorageSlotFromTries));
      mutatedAccount.ifPresent(acct -> accumulatedAccounts.put(address, Optional.of(acct)));
      return mutatedAccount.orElse(null);
    }
  }

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return accumulatedAccounts.values().stream()
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return accumulatedAccounts.entrySet().stream()
      .filter(e -> e.getValue().isEmpty())
      .map(Map.Entry::getKey)
      .collect(Collectors.toList());
  }

  @Override
  public void revert() {
    //no-op
  }

  @Override
  public void commit() {
   //no-op
  }

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    return Optional.empty();
  }
}
