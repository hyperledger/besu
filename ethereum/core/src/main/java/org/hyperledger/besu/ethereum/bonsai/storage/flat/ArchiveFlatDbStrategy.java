package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiContext;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveFlatDbStrategy extends FullFlatDbStrategy {
  private final BonsaiContext context;
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveFlatDbStrategy.class);

  public ArchiveFlatDbStrategy(final BonsaiContext context, final MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.context = context;
  }

  static final byte[] MAX_BLOCK_SUFFIX = Bytes.ofUnsignedLong(Long.MAX_VALUE).toArrayUnsafe();
  static final byte[] MIN_BLOCK_SUFFIX = Bytes.ofUnsignedLong(0L).toArrayUnsafe();
  static final byte[] DELETED_ACCOUNT_VALUE = new byte[0];

  @Override
  public Optional<Bytes> getFlatAccount(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final SegmentedKeyValueStorage storage) {
    getAccountCounter.inc();

    // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
    Bytes keyNearest = calculateKeyWithMaxSuffix(accountHash);

    // use getNearest() with an account key that is suffixed by the block context
    final Optional<Bytes> accountFound =
        storage
            .getNearestTo(ACCOUNT_INFO_STATE, keyNearest)
            // don't return accounts that do not have a matching account hash
            .filter(
                found ->
                    !Arrays.areEqual(
                        DELETED_ACCOUNT_VALUE, found.value().orElse(DELETED_ACCOUNT_VALUE)))
            .filter(found -> accountHash.commonPrefixLength(found.key()) >= accountHash.size())
            .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes);

    if (accountFound.isPresent()) {
      getAccountFoundInFlatDatabaseCounter.inc();
    } else {
      getAccountNotFoundInFlatDatabaseCounter.inc();
    }
    return accountFound;
  }

  /*
   * Puts the account data for the given account hash and block context.
   */
  @Override
  public void putFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Bytes accountValue) {

    // key suffixed with block context, or MIN_BLOCK_SUFFIX if we have no context:
    byte[] keySuffixed = calculateKeyWithMinSuffix(accountHash);

    transaction.put(ACCOUNT_INFO_STATE, keySuffixed, accountValue.toArrayUnsafe());
  }

  @Override
  public void removeFlatAccount(
      final SegmentedKeyValueStorageTransaction transaction, final Hash accountHash) {

    // insert a key suffixed with block context, with 'deleted account' value
    byte[] keySuffixed = calculateKeyWithMinSuffix(accountHash);

    transaction.put(ACCOUNT_INFO_STATE, keySuffixed, DELETED_ACCOUNT_VALUE);
  }

  public byte[] calculateKeyWithMinSuffix(final Hash accountHash) {
    return calculateKeyWithSuffix(accountHash, MIN_BLOCK_SUFFIX);
  }

  public Bytes calculateKeyWithMaxSuffix(final Hash accountHash) {
    return Bytes.of(calculateKeyWithSuffix(accountHash, MAX_BLOCK_SUFFIX));
  }

  public byte[] calculateKeyWithSuffix(final Hash accountHash, final byte[] orElseSuffix) {
    // TODO: this can be optimized, just for PoC now
    return Arrays.concatenate(
        accountHash.toArrayUnsafe(),
        context
            .getBlockHeader()
            .map(BlockHeader::getNumber)
            .map(Bytes::ofUnsignedLong)
            .map(Bytes::toArrayUnsafe)
            .orElseGet(
                () -> {
                  // TODO: remove or rate limit these warnings
                  LOG.atWarn().setMessage("Block context not present, using default suffix").log();
                  return orElseSuffix;
                }));
  }

  @Override
  public void updateBlockContext(final BlockHeader blockHeader) {
    context.setBlockHeader(blockHeader);
  }

  @Override
  public FlatDbStrategy contextSafeClone() {
    return new ArchiveFlatDbStrategy(context.copy(), metricsSystem);
  }
}
