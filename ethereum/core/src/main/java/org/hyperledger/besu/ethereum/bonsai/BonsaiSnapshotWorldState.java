package org.hyperledger.besu.ethereum.bonsai;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.MutableWorldView;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hyperledger.besu.ethereum.bonsai.BonsaiAccount.fromRLP;

/**
 * This class takes a snapshot of the worldstate as the basis of a mutable worldstate. It is not
 * able to commit or persist however. This is useful for async blockchain opperations like block
 * creation and/or point-in-time queries.
 */
public class BonsaiSnapshotWorldState implements MutableWorldState, MutableWorldView {
  //  private static final Logger LOG = LoggerFactory.getLogger(BonsaiSnapshotWorldState.class);
  final KeyValueStorage accountStorage;
  final KeyValueStorage codeStorage;
  final KeyValueStorage storageStorage;
  final KeyValueStorage trieBranchStorage;
  final KeyValueStorage trieLogStorage;
  final WorldUpdater txUpdter;

  //TODO: we may or may not persist trie logs in the archive trielog layer.  this might just be a callback instead
  final BonsaiWorldStateArchive archive;

  public BonsaiSnapshotWorldState(
      final BonsaiWorldStateArchive archive,
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage,
      final KeyValueStorage trieBranchStorage,
      final KeyValueStorage trieLogStorage
      ) {
    this.accountStorage = accountStorage;
    this.codeStorage = codeStorage;
    this.storageStorage = storageStorage;
    this.trieBranchStorage = trieBranchStorage;
    this.trieLogStorage = trieLogStorage;
    this.archive = archive;

    // TODO: revisit updater.  In memory or transaction based?
    this.txUpdter = new SnapshotTransactionUpdater<EvmAccount>();
  }

  public static BonsaiSnapshotWorldState create(
      final BonsaiWorldStateArchive archive,
      final BonsaiWorldStateKeyValueStorage parentWorldStateStorage) {
    return new BonsaiSnapshotWorldState(
        archive,
        ((SnappableKeyValueStorage) parentWorldStateStorage.accountStorage).takeSnapshot(),
        ((SnappableKeyValueStorage) parentWorldStateStorage.codeStorage).takeSnapshot(),
        ((SnappableKeyValueStorage) parentWorldStateStorage.storageStorage).takeSnapshot(),
        ((SnappableKeyValueStorage) parentWorldStateStorage.trieBranchStorage).takeSnapshot(),
        parentWorldStateStorage.trieLogStorage);
  }
  @Override
  public MutableWorldState copy() {
    // TODO: we are transaction based, so perhaps we return a new transaction based worldstate...
    //       for now lets just return ourselves
    return this;
  }

  @Override
  public void persist(final BlockHeader blockHeader) {
    // snapshots are mutable but not persistable
//    throw new UnsupportedOperationException(
//        "BonsaiSnapshotWorldState does not support persisting changes to database");
    // TODO: for testing we are using persist to indicate we can release the snapshot
  try {
    trieBranchStorage.close();
    accountStorage.close();
    codeStorage.close();
    storageStorage.close();
    } catch (IOException ex) {
    System.err.println("unexpected ioexception closing snapshot: " + ex);
  }

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
    //TODO: this should be moved to shared abstract BonsaiWorldStateKVStorage
    //TODO: need to add context for code lookup
    final var accountHash = Hash.hash(address).toArrayUnsafe();
    return accountStorage
      .get(accountHash)
        .map(Bytes::wrap)
        .map(Optional::of)
      .orElseGet(() -> getWorldStateRootHash()
        .map(stateRootHash -> new StoredMerklePatriciaTrie<>(
                    new StoredNodeFactory<>(
                        this::getAccountStateTrieNode, Function.identity(), Function.identity()),
                    Bytes32.wrap(stateRootHash)))
          .flatMap(mpt -> mpt.get(Bytes.of(accountHash))))
        .map(bytes -> fromRLP(null, address, bytes, true))
        .orElse(null);
  }

  //TODO: this is a WorldStateStorage method, refactor
  Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
    if (nodeHash.equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      return Optional.of(MerklePatriciaTrie.EMPTY_TRIE_NODE);
    } else {
      return trieBranchStorage.get(location.toArrayUnsafe()).map(Bytes::wrap);
    }
  }
  //TODO: also ripped off from WorldStateStorage
  static final byte[] WORLD_ROOT_HASH_KEY = "worldRoot".getBytes(StandardCharsets.UTF_8);
  Optional<Bytes> getWorldStateRootHash() {
    return trieBranchStorage.get(WORLD_ROOT_HASH_KEY).map(Bytes::wrap);
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
