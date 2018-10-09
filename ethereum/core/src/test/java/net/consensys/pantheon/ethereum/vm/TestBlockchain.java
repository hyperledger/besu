package net.consensys.pantheon.ethereum.vm;

import static java.nio.charset.StandardCharsets.UTF_8;

import net.consensys.pantheon.ethereum.chain.BlockAddedObserver;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.chain.ChainHead;
import net.consensys.pantheon.ethereum.chain.TransactionLocation;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.List;
import java.util.Optional;

/**
 * A blockchain mock for the Ethereum reference tests.
 *
 * <p>The only method this class is used for is {@link TestBlockchain#getBlockHashByNumber} The
 * Ethereum reference tests for VM exection (VMTests) and transaction processing (GeneralStateTests)
 * require a block's hash to be to be the hash of the string of it's block number.
 */
public class TestBlockchain implements Blockchain {

  private static Hash generateTestBlockHash(final long number) {
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
    throw new UnsupportedOperationException();
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
