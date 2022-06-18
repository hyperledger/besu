package org.hyperledger.besu.ethereum.bonsai;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.MutableWorldView;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

/**
 * This class takes a snapshot of the worldstate as the basis of a mutable worldstate.
 * It is not able to commit or persist however.  This is useful for async blockchain
 * opperations like block creation and/or point-in-time queries.
 */
public class BonsaiSnapshotWorldState implements MutableWorldState, MutableWorldView {
  private static final Logger LOG = LoggerFactory.getLogger(BonsaiSnapshotWorldState.class);
  final KeyValueStorageTransaction accountStorageTx;
  final KeyValueStorageTransaction codeStorageTx;
  final KeyValueStorageTransaction storageStorageTx;
  final WorldUpdater txUpdter;

  private BonsaiSnapshotWorldState(
    final KeyValueStorageTransaction accountStorageTx,
    final KeyValueStorageTransaction codeStorageTx,
    final KeyValueStorageTransaction storageStorageTx) {
    this.accountStorageTx = accountStorageTx;
    this.codeStorageTx = codeStorageTx;
    this.storageStorageTx = storageStorageTx;

    this.txUpdter = new SnapshotTransactionUpdater();
  };

  public static BonsaiSnapshotWorldState createSnapshot(
    BonsaiWorldStateKeyValueStorage storage) {
    // TODO: need to get bonsai ws kv storage supporting snapshot transactions in here.
    return new BonsaiSnapshotWorldState(
      null,null,null);

  }

  public static BonsaiSnapshotWorldState createSnapshotAt(
    BonsaiWorldStateArchive archive, Hash blockHash, BonsaiWorldStateKeyValueStorage storage) {

    //TODO: atomically roll the chain to this hash (if it exists), take a snapshot,
    //      then roll the state back to the prior head
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public MutableWorldState copy() {
    // TODO: we are transaction based, so perhaps we return a new transaction based worldstate...
    //       for now lets just return ourselves
    return this;
  }

  @Override
  public void persist(BlockHeader blockHeader) {
    // snapshots are mutable but not persistable
    throw new UnsupportedOperationException("BonsaiSnapshotWorldState does not support persisting changes to database");
  }

  @Override
  public WorldUpdater updater() {
    return txUpdter;
  }

  @Override
  public Hash rootHash() {
    return null;
  }

  @Override
  public Hash frontierRootHash() {
    return null;
  }

  @Override
  public Stream<StreamableAccount> streamAccounts(Bytes32 startKeyHash, int limit) {
    return null;
  }

  @Override
  public Account get(Address address) {
    return null;
  }

  static class SnapshotTransactionUpdater<A extends Account> implements WorldUpdater {

    protected Map<Address, UpdateTrackingAccount<A>> updatedAccounts = new HashMap<>();
    protected Set<Address> deletedAccounts = new HashSet<>();


    private UpdateTrackingAccount<A> track(final UpdateTrackingAccount<A> account) {
      final Address address = account.getAddress();
      updatedAccounts.put(address, account);
      deletedAccounts.remove(address);
      return account;
    }

    @Override
    public WorldUpdater updater() {
      return null;
    }

    @Override
    public EvmAccount createAccount(Address address, long nonce, Wei balance) {
      return null;
    }

    @Override
    public EvmAccount getAccount(Address address) {
      return null;
    }

    @Override
    public void deleteAccount(Address address) {

    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
      return null;
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      return null;
    }

    @Override
    public void revert() {

    }

    @Override
    public void commit() {
      // we explicitly do not implement commit for the transaction-based snapshot updater
    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
      return Optional.empty();
    }

    @Override
    public Account get(Address address) {
      return null;
    }
  }
}


