/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.vm;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A blockchain mock for the Ethereum reference tests.
 *
 * <p>Operations which would lead to non-deterministic behaviour if executed while processing
 * transactions throw {@link NonDeterministicOperationException}. For example all methods that
 * lookup blocks by number since the block being processed may not be on the canonical chain but
 * that must not affect the execution of its transactions.
 *
 * <p>The Ethereum reference tests for VM exection (VMTests) and transaction processing
 * (GeneralStateTests) require a block's hash to be to be the hash of the string of it's block
 * number.
 */
public class TestBlockchain implements Blockchain {

  // Maximum number of blocks prior to the chain head that can be retrieved by hash.
  private static final long MAXIMUM_BLOCKS_BEHIND_HEAD = 256;
  private static final String NUMBER_LOOKUP_ERROR =
      "Blocks must not be looked up by number in the EVM. The block being processed may not be on the canonical chain.";
  private static final String CHAIN_HEAD_ERROR =
      "Chain head is inherently non-deterministic. The block currently being processed should be treated as the chain head.";
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
    throw new NonDeterministicOperationException(NUMBER_LOOKUP_ERROR);
  }

  @Override
  public ChainHead getChainHead() {
    throw new NonDeterministicOperationException(CHAIN_HEAD_ERROR);
  }

  @Override
  public long getChainHeadBlockNumber() {
    throw new NonDeterministicOperationException(CHAIN_HEAD_ERROR);
  }

  @Override
  public Hash getChainHeadHash() {
    throw new NonDeterministicOperationException(CHAIN_HEAD_ERROR);
  }

  @Override
  public Optional<TransactionLocation> getTransactionLocation(final Hash transactionHash) {
    throw new NonDeterministicOperationException("Transaction location may be different on forks");
  }

  @Override
  public Optional<BlockHeader> getBlockHeader(final long blockNumber) {
    throw new NonDeterministicOperationException(NUMBER_LOOKUP_ERROR);
  }

  @Override
  public Optional<BlockHeader> getBlockHeader(final Hash blockHeaderHash) {
    return Optional.ofNullable(hashToHeader.get(blockHeaderHash));
  }

  @Override
  public Optional<BlockBody> getBlockBody(final Hash blockHeaderHash) {
    // Deterministic, but just not implemented.
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<List<TransactionReceipt>> getTxReceipts(final Hash blockHeaderHash) {
    // Deterministic, but just not implemented.
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<UInt256> getTotalDifficultyByHash(final Hash blockHeaderHash) {
    // Deterministic, but just not implemented.
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    throw new NonDeterministicOperationException(
        "Which transactions are on the chain may vary on different forks");
  }

  @Override
  public long observeBlockAdded(final BlockAddedObserver observer) {
    throw new NonDeterministicOperationException("Listening for new blocks is not deterministic");
  }

  @Override
  public boolean removeObserver(final long observerId) {
    throw new NonDeterministicOperationException("Listening for new blocks is not deterministic");
  }

  public static class NonDeterministicOperationException extends RuntimeException {
    public NonDeterministicOperationException(final String message) {
      super(message);
    }
  }
}
