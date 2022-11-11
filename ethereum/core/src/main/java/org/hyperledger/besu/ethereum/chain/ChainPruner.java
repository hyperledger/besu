package org.hyperledger.besu.ethereum.chain;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;

public class ChainPruner implements BlockAddedObserver {

  private static final Logger LOG = LoggerFactory.getLogger(ChainPruner.class);

  private static final Bytes PRUNING_MARK_KEY =
      Bytes.wrap("blockNumberTail".getBytes(StandardCharsets.UTF_8));

  private static final Bytes VARIABLES_PREFIX = Bytes.of(1);
  private static final Bytes FORK_BLOCKS_PREFIX = Bytes.of(2);

  private final BlockchainStorage blockchainStorage;
  private final KeyValueStorage prunerStorage;
  private final long blocksToKeep;

  public ChainPruner(
      final BlockchainStorage blockchainStorage,
      final KeyValueStorage prunerStorage,
      final long blocksToKeep) {
    this.blockchainStorage = blockchainStorage;
    this.prunerStorage = prunerStorage;
    this.blocksToKeep = blocksToKeep;
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    LOG.debug("New block added event: " + event);
    // Get pruning mark
    long blockNumber = event.getBlock().getHeader().getNumber();
    Optional<Long> maybePruningMark = getPruningMark();
    if (maybePruningMark.isEmpty()) {
      // Set initial pruning mark
      maybePruningMark = Optional.of(blockNumber);
    }
    long pruningMark = maybePruningMark.get();
    if (blockNumber < pruningMark) {
      // Ignore and warn if block number < pruning mark, this normally indicates the blocksToKeep is too small.
      LOG.warn("Block added event: " + event + " has a block number of " + blockNumber + " < pruning mark " + pruningMark);
      return;
    }
    // Append block into fork blocks.
    KeyValueStorageTransaction tx = prunerStorage.startTransaction();
    Collection<Hash> forkBlocks = getForkBlocks(blockNumber);
    forkBlocks.add(event.getBlock().getHash());
    setForkBlocks(tx, blockNumber, forkBlocks);
    // If a block is a new canonical head, start pruning.
    if (event.isNewCanonicalHead()) {
      while (blockNumber - pruningMark >= blocksToKeep) {
        LOG.debug("Pruning chain data at pruning mark: " + pruningMark);
        // Get a collection of old fork blocks that need to be pruned.
        Collection<Hash> oldForkBlocks = getForkBlocks(pruningMark);
        BlockchainStorage.Updater updater = blockchainStorage.updater();
        for (Hash toPrune : oldForkBlocks) {
          Optional<BlockBody> maybeBody = blockchainStorage.getBlockBody(toPrune);
          if (maybeBody.isEmpty()) {
            continue;
          }
          // Prune block header, body, receipts, total difficulty and transaction locations.
          updater.removeBlockHeader(toPrune);
          updater.removeBlockBody(toPrune);
          updater.removeTransactionReceipts(toPrune);
          updater.removeTotalDifficulty(toPrune);
          maybeBody.get().getTransactions().forEach(t -> updater.removeTransactionLocation(t.getHash()));
        }
        // Prune canonical chain mapping and commit.
        updater.removeBlockHash(pruningMark);
        updater.commit();
        // Remove old fork blocks.
        removeForkBlocks(tx, pruningMark);
        pruningMark++;
      }
    }
    // Update pruning mark and commit
    setPruningMark(tx, pruningMark);
    tx.commit();
  }

  private Optional<Long> getPruningMark() {
    return get(VARIABLES_PREFIX, PRUNING_MARK_KEY).map(UInt256::fromBytes).map(UInt256::toLong);
  }

  private Collection<Hash> getForkBlocks(final long blockNumber) {
    return get(FORK_BLOCKS_PREFIX, UInt256.valueOf(blockNumber))
        .map(bytes -> RLP.input(bytes).readList(in -> bytesToHash(in.readBytes32())))
        .orElse(Lists.newArrayList());
  }

  private void setPruningMark(final KeyValueStorageTransaction transaction, final long pruningMark) {
    set(transaction, VARIABLES_PREFIX, PRUNING_MARK_KEY, UInt256.valueOf(pruningMark));
  }

  private void setForkBlocks(final KeyValueStorageTransaction transaction, final long blockNumber, final Collection<Hash> forkBlocks) {
    set(transaction, FORK_BLOCKS_PREFIX, UInt256.valueOf(blockNumber), RLP.encode(o -> o.writeList(forkBlocks, (val, out) -> out.writeBytes(val))));
  }

  private void removeForkBlocks(final KeyValueStorageTransaction transaction, final long blockNumber) {
    remove(transaction, FORK_BLOCKS_PREFIX, UInt256.valueOf(blockNumber));
  }

  private Optional<Bytes> get(final Bytes prefix, final Bytes key) {
    return prunerStorage.get(Bytes.concatenate(prefix, key).toArrayUnsafe()).map(Bytes::wrap);
  }

  private void set(final KeyValueStorageTransaction transaction, final Bytes prefix, final Bytes key, final Bytes value) {
    transaction.put(Bytes.concatenate(prefix, key).toArrayUnsafe(), value.toArrayUnsafe());
  }

  private void remove(final KeyValueStorageTransaction transaction, final Bytes prefix, final Bytes key) {
    transaction.remove(Bytes.concatenate(prefix, key).toArrayUnsafe());
  }

  private Hash bytesToHash(final Bytes bytes) {
    return Hash.wrap(Bytes32.wrap(bytes, 0));
  }
}
