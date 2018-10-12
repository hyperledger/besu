package tech.pegasys.pantheon.ethereum.vm;

import static java.nio.charset.StandardCharsets.UTF_8;

import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.ChainHead;
import tech.pegasys.pantheon.ethereum.chain.TransactionLocation;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A blockchain mock for the Ethereum reference tests.
 *
 * <p>The only method this class is used for is {@link TestBlockchain#getBlockHashByNumber} The
 * Ethereum reference tests for VM exection (VMTests) and transaction processing (GeneralStateTests)
 * require a block's hash to be to be the hash of the string of it's block number.
 */
public class TestBlockchain implements Blockchain {

  // Maximum number of blocks prior to the chain head that can be retrieved by hash.
  private static final long MAXIMUM_BLOCKS_BEHIND_HEAD = 256;
  private final Map<Hash, BlockHeader> hashToHeader = new HashMap<>();

  public TestBlockchain() {
    this(0);
  }

  public TestBlockchain(final long chainHeadBlockNumber) {
    for (long blockNumber = Math.max(0L, chainHeadBlockNumber - MAXIMUM_BLOCKS_BEHIND_HEAD);
        blockNumber < chainHeadBlockNumber;
        blockNumber++) {
      final Hash hash = generateTestBlockHash(blockNumber);
      hashToHeader.put(
          hash,
          new BlockHeaderTestFixture()
              .number(blockNumber)
              .parentHash(generateTestBlockHash(blockNumber - 1))
              .buildHeader());
    }
  }

  public static Hash generateTestBlockHash(final long number) {
    final byte[] bytes = Long.toString(number).getBytes(UTF_8);
    return Hash.hash(BytesValue.wrap(bytes));
  }

  @Override
  public Optional<Hash> getBlockHashByNumber(final long number) {
    return Optional.of(generateTestBlockHash(number));
  }

  @Override
  public ChainHead getChainHead() {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getChainHeadBlockNumber() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Hash getChainHeadHash() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<TransactionLocation> getTransactionLocation(final Hash transactionHash) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<BlockHeader> getBlockHeader(final long blockNumber) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<BlockHeader> getBlockHeader(final Hash blockHeaderHash) {
    return Optional.ofNullable(hashToHeader.get(blockHeaderHash));
  }

  @Override
  public Optional<BlockBody> getBlockBody(final Hash blockHeaderHash) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<List<TransactionReceipt>> getTxReceipts(final Hash blockHeaderHash) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<UInt256> getTotalDifficultyByHash(final Hash blockHeaderHash) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long observeBlockAdded(final BlockAddedObserver observer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeObserver(final long observerId) {
    throw new UnsupportedOperationException();
  }
}
