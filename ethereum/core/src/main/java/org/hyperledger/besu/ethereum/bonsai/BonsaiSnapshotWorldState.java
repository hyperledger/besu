package org.hyperledger.besu.ethereum.bonsai;

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
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes32;

/**
 * This class takes a snapshot of the worldstate as the basis of a mutable worldstate. It is not
 * able to commit or persist however. This is useful for async blockchain opperations like block
 * creation and/or point-in-time queries.
 */
public class BonsaiSnapshotWorldState implements MutableWorldState, MutableWorldView {
  //  private static final Logger LOG = LoggerFactory.getLogger(BonsaiSnapshotWorldState.class);
  final KeyValueStorage accountStorageTx;
  final KeyValueStorage codeStorageTx;
  final KeyValueStorage storageStorageTx;
  final WorldUpdater txUpdter;

  public BonsaiSnapshotWorldState(
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage) {
    this.accountStorageTx = accountStorage;
    this.codeStorageTx = codeStorage;
    this.storageStorageTx = storageStorage;

    this.txUpdter = new SnapshotTransactionUpdater<EvmAccount>();
  }
  ;

  @Override
  public MutableWorldState copy() {
    // TODO: we are transaction based, so perhaps we return a new transaction based worldstate...
    //       for now lets just return ourselves
    return this;
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    // snapshots are mutable but not persistable
    throw new UnsupportedOperationException(
        "BonsaiSnapshotWorldState does not support persisting changes to database");
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
  public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
    return null;
  }

  @Override
  public Account get(final Address address) {
    return null;
  }

  static class SnapshotTransactionUpdater<A extends Account> implements WorldUpdater {

    protected Map<Address, UpdateTrackingAccount<A>> updatedAccounts = new HashMap<>();
    protected Set<Address> deletedAccounts = new HashSet<>();

    SnapshotTransactionUpdater() {
      // TODO: writeme
    }

    //    private UpdateTrackingAccount<A> track(final UpdateTrackingAccount<A> account) {
    //      final Address address = account.getAddress();
    //      updatedAccounts.put(address, account);
    //      deletedAccounts.remove(address);
    //      return account;
    //    }

    @Override
    public WorldUpdater updater() {
      return null;
    }

    @Override
    public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
      return null;
    }

    @Override
    public EvmAccount getAccount(final Address address) {
      return null;
    }

    @Override
    public void deleteAccount(final Address address) {}

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
      return null;
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      return null;
    }

    @Override
    public void revert() {}

    @Override
    public void commit() {
      // we explicitly do not implement commit for the transaction-based snapshot updater
    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
      return Optional.empty();
    }

    @Override
    public Account get(final Address address) {
      return null;
    }
  }
}
