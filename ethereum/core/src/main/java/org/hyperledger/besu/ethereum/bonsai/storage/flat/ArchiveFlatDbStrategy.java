package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiContext;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

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

  @Override
  public Optional<Bytes> getFlatAccount(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final SegmentedKeyValueStorage storage) {
    getAccountCounter.inc();

    // keyNearest:
    Bytes keyNearest = calculateKeyPrefix(accountHash);

    // use getNearest() with an account key that is suffixed by the block context
    final Optional<Bytes> accountFound =
        storage
            .getNearestTo(ACCOUNT_INFO_STATE, keyNearest)
            .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes)
            // don't return accounts that do not have a matching account hash
            .filter(found -> accountHash.commonPrefixLength(found) >= accountHash.size());

    if (accountFound.isPresent()) {
      getAccountFoundInFlatDatabaseCounter.inc();
    } else {
      getAccountNotFoundInFlatDatabaseCounter.inc();
    }
    return accountFound;
  }

  public Bytes calculateKeyPrefix(final Hash accountHash) {
    // TODO: this can be optimized, just for PoC now
    return Bytes.of(
        Arrays.concatenate(
            accountHash.toArrayUnsafe(),
            context
                .getBlockHeader()
                .map(BlockHeader::getNumber)
                .map(Bytes::ofUnsignedLong)
                .map(Bytes::toArrayUnsafe)
                .orElseGet(
                    () -> {
                      // TODO: remove or rate limit these warnings
                      LOG.atWarn().setMessage("Block context not present, using max long").log();
                      return MAX_BLOCK_SUFFIX;
                    })));
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
