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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import org.hyperledger.besu.ethereum.api.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.LogWithMetadata;
import org.hyperledger.besu.ethereum.api.LogsQuery;
import org.hyperledger.besu.ethereum.api.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

public class BlockchainQueriesTest {
  private BlockDataGenerator gen;

  @Before
  public void setup() {
    gen = new BlockDataGenerator();
  }

  @Test
  public void getBlockByHash() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Block targetBlock = data.blockData.get(1).block;

    final BlockWithMetadata<TransactionWithMetadata, Hash> result =
        queries.blockByHash(targetBlock.getHash()).get();
    assertBlockMatchesResult(targetBlock, result);
  }

  @Test
  public void transactionByBlockHashAndIndexForInvalidHash() {
    final BlockchainWithData data = setupBlockchain(2);
    final BlockchainQueries queries = data.blockchainQueries;

    final Optional<TransactionWithMetadata> transactionWithMetadata =
        queries.transactionByBlockHashAndIndex(gen.hash(), 1);
    assertThat(transactionWithMetadata).isEmpty();
  }

  @Test
  public void getBlockByHashForInvalidHash() {
    final BlockchainWithData data = setupBlockchain(2);
    final BlockchainQueries queries = data.blockchainQueries;

    final Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> result =
        queries.blockByHash(gen.hash());
    assertThat(result).isEmpty();
  }

  @Test
  public void getBlockByNumber() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Block targetBlock = data.blockData.get(1).block;

    final BlockWithMetadata<TransactionWithMetadata, Hash> result =
        queries.blockByNumber(targetBlock.getHeader().getNumber()).get();
    assertBlockMatchesResult(targetBlock, result);
  }

  @Test
  public void getBlockByNumberForInvalidNumber() {
    final BlockchainWithData data = setupBlockchain(2);
    final BlockchainQueries queries = data.blockchainQueries;

    final Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> result =
        queries.blockByNumber(10L);
    assertThat(result).isEmpty();
  }

  @Test
  public void getLatestBlock() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Block targetBlock = data.blockData.get(2).block;

    final BlockWithMetadata<TransactionWithMetadata, Hash> result = queries.latestBlock().get();
    assertBlockMatchesResult(targetBlock, result);
  }

  @Test
  public void getBlockByHashWithTxHashes() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Block targetBlock = data.blockData.get(1).block;

    final BlockWithMetadata<Hash, Hash> result =
        queries.blockByHashWithTxHashes(targetBlock.getHash()).get();
    assertBlockMatchesResultWithTxHashes(targetBlock, result);
  }

  @Test
  public void getBlockByHashWithTxHashesForInvalidHash() {
    final BlockchainWithData data = setupBlockchain(2);
    final BlockchainQueries queries = data.blockchainQueries;

    final Optional<BlockWithMetadata<Hash, Hash>> result =
        queries.blockByHashWithTxHashes(gen.hash());
    assertThat(result).isEmpty();
  }

  @Test
  public void getBlockByNumberWithTxHashes() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Block targetBlock = data.blockData.get(1).block;

    final BlockWithMetadata<Hash, Hash> result =
        queries.blockByNumberWithTxHashes(targetBlock.getHeader().getNumber()).get();
    assertBlockMatchesResultWithTxHashes(targetBlock, result);
  }

  @Test
  public void getBlockByNumberWithTxHashesForInvalidHash() {
    final BlockchainWithData data = setupBlockchain(2);
    final BlockchainQueries queries = data.blockchainQueries;

    final Optional<BlockWithMetadata<Hash, Hash>> result = queries.blockByNumberWithTxHashes(10L);
    assertThat(result).isEmpty();
  }

  @Test
  public void getLatestBlockWithTxHashes() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Block targetBlock = data.blockData.get(2).block;

    final BlockWithMetadata<Hash, Hash> result = queries.latestBlockWithTxHashes().get();
    assertBlockMatchesResultWithTxHashes(targetBlock, result);
  }

  @Test
  public void getHeadBlockNumber() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    long result = queries.headBlockNumber();
    assertThat(result).isEqualTo(2L);

    // Increment and test
    final Block lastBlock = data.blockData.get(2).block;
    final Block nextBlock = gen.nextBlock(lastBlock);
    data.blockchain.appendBlock(nextBlock, gen.receipts(nextBlock));

    // Check that number has incremented
    result = queries.headBlockNumber();
    assertThat(result).isEqualTo(3L);
  }

  @Test
  public void getAccountStorageBlockNumber() {
    final List<Address> addresses = Arrays.asList(gen.address(), gen.address(), gen.address());
    final List<UInt256> storageKeys =
        Arrays.asList(gen.storageKey(), gen.storageKey(), gen.storageKey());
    final BlockchainWithData data = setupBlockchain(3, addresses, storageKeys);
    final BlockchainQueries queries = data.blockchainQueries;

    final Hash latestStateRoot0 = data.blockData.get(2).block.getHeader().getStateRoot();
    final WorldState worldState0 = data.worldStateArchive.get(latestStateRoot0).get();
    addresses.forEach(
        address ->
            storageKeys.forEach(
                storageKey -> {
                  final Account actualAccount0 = worldState0.get(address);
                  final Optional<UInt256> result = queries.storageAt(address, storageKey, 2L);
                  assertThat(result).contains(actualAccount0.getStorageValue(storageKey));
                }));

    final Hash latestStateRoot1 = data.blockData.get(1).block.getHeader().getStateRoot();
    final WorldState worldState1 = data.worldStateArchive.get(latestStateRoot1).get();
    addresses.forEach(
        address ->
            storageKeys.forEach(
                storageKey -> {
                  final Account actualAccount1 = worldState1.get(address);
                  final Optional<UInt256> result = queries.storageAt(address, storageKey, 1L);
                  assertThat(result).contains(actualAccount1.getStorageValue(storageKey));
                }));
  }

  @Test
  public void getAccountBalanceAtBlockNumber() {
    final List<Address> addresses = Arrays.asList(gen.address(), gen.address(), gen.address());
    final int blockCount = 3;
    final BlockchainWithData data = setupBlockchain(blockCount, addresses);
    final BlockchainQueries queries = data.blockchainQueries;

    for (int i = 0; i < blockCount; i++) {
      final long curBlockNumber = i;
      final Hash stateRoot = data.blockData.get(i).block.getHeader().getStateRoot();
      final WorldState worldState = data.worldStateArchive.get(stateRoot).get();
      assertThat(addresses).isNotEmpty();

      addresses.forEach(
          address -> {
            final Account actualAccount = worldState.get(address);
            final Optional<Wei> result = queries.accountBalance(address, curBlockNumber);

            assertThat(result).contains(actualAccount.getBalance());
          });
    }
  }

  @Test
  public void getAccountBalanceNonExistentAtBlockNumber() {
    final List<Address> addresses = Arrays.asList(gen.address(), gen.address(), gen.address());
    final BlockchainWithData data = setupBlockchain(3, addresses);
    final BlockchainQueries queries = data.blockchainQueries;
    assertThat(addresses).isNotEmpty();

    // Get random non-existent account
    final Wei result = queries.accountBalance(gen.address(), 1L).get();
    assertThat(result).isEqualByComparingTo(Wei.ZERO);
  }

  @Test
  public void getOmmerCountByHash() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Block targetBlock = data.blockData.get(1).block;

    final Optional<Integer> result = queries.getOmmerCount(targetBlock.getHash());
    assertThat(result).contains(targetBlock.getBody().getOmmers().size());
  }

  @Test
  public void getOmmerCountByInvalidHash() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Optional<Integer> result = queries.getOmmerCount(gen.hash());
    assertThat(result).isEmpty();
  }

  @Test
  public void getOmmerCountByNumber() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Block targetBlock = data.blockData.get(1).block;

    final Optional<Integer> result = queries.getOmmerCount(targetBlock.getHeader().getNumber());
    assertThat(result).contains(targetBlock.getBody().getOmmers().size());
  }

  @Test
  public void getOmmerCountForInvalidNumber() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final long invalidNumber = data.blockchain.getChainHeadBlockNumber() + 10;
    final Optional<Integer> result = queries.getOmmerCount(invalidNumber);
    assertThat(result).isEmpty();
  }

  @Test
  public void getOmmerCountForLatestBlock() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Block targetBlock = data.blockData.get(data.blockData.size() - 1).block;
    final Optional<Integer> result = queries.getOmmerCount();
    assertThat(result).contains(targetBlock.getBody().getOmmers().size());
  }

  @Test
  public void logsShouldBeFlaggedAsRemovedWhenBlockIsNotInCanonicalChain() {
    // create initial blockchain
    final BlockchainWithData data = setupBlockchain(3);
    final Block targetBlock = data.blockData.get(data.blockData.size() - 1).block;

    // check that logs have removed = false
    List<LogWithMetadata> logs =
        data.blockchainQueries.matchingLogs(targetBlock.getHash(), new LogsQuery.Builder().build());
    assertThat(logs).isNotEmpty();
    assertThat(logs).allMatch(l -> !l.isRemoved());

    // Create parallel fork of length 1
    final int forkBlock = 2;
    final int commonAncestor = 1;
    final BlockOptions options =
        new BlockOptions()
            .setParentHash(data.blockchain.getBlockHashByNumber(commonAncestor).get())
            .setBlockNumber(forkBlock)
            .setDifficulty(
                data.blockchain.getBlockHeader(forkBlock).get().getDifficulty().plus(10L));
    final Block fork = gen.block(options);
    final List<TransactionReceipt> forkReceipts = gen.receipts(fork);

    // Add fork
    data.blockchain.appendBlock(fork, forkReceipts);

    // check that logs have removed = true
    logs =
        data.blockchainQueries.matchingLogs(targetBlock.getHash(), new LogsQuery.Builder().build());
    assertThat(logs).isNotEmpty();
    assertThat(logs).allMatch(LogWithMetadata::isRemoved);
  }

  @Test
  public void matchingLogsShouldReturnAnEmptyListWhenGivenAnInvalidBlockHash() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;
    List<LogWithMetadata> logs = queries.matchingLogs(Hash.ZERO, new LogsQuery.Builder().build());
    assertThat(logs).isEmpty();
  }

  @Test
  public void getOmmerByBlockHashAndIndexShouldReturnEmptyWhenBlockDoesNotExist() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Optional<BlockHeader> ommerOptional = queries.getOmmer(Hash.ZERO, 0);

    assertThat(ommerOptional).isEmpty();
  }

  @Test
  public void getOmmerByBlockHashAndIndexShouldReturnEmptyWhenBlockDoesNotHaveOmmers() {
    final BlockchainWithData data = setupBlockchain(1);
    final BlockchainQueries queries = data.blockchainQueries;
    final Block targetBlock = data.blockData.get(0).block;

    final Optional<BlockHeader> ommerOptional = queries.getOmmer(targetBlock.getHash(), 0);

    assertThat(targetBlock.getBody().getOmmers()).hasSize(0);
    assertThat(ommerOptional).isEmpty();
  }

  @Test
  public void getOmmerByBlockHashAndIndexShouldReturnEmptyWhenIndexIsOutOfRange() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;
    final Block targetBlock = data.blockData.get(0).block;
    final int indexOutOfRange = targetBlock.getBody().getOmmers().size() + 1;

    final Optional<BlockHeader> ommerOptional =
        queries.getOmmer(targetBlock.getHash(), indexOutOfRange);

    assertThat(ommerOptional).isEmpty();
  }

  @Test
  public void getOmmerByBlockHashAndIndexShouldReturnExpectedOmmerHeader() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;
    final Block targetBlock = data.blockData.get(data.blockData.size() - 1).block;
    final BlockHeader ommerBlockHeader = targetBlock.getBody().getOmmers().get(0);

    final BlockHeader retrievedOmmerBlockHeader = queries.getOmmer(targetBlock.getHash(), 0).get();

    assertThat(retrievedOmmerBlockHeader).isEqualTo(ommerBlockHeader);
  }

  @Test
  public void getOmmerByBlockNumberAndIndexShouldReturnEmptyWhenBlockDoesNotExist() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;

    final Optional<BlockHeader> ommerOptional = queries.getOmmer(999, 0);

    assertThat(ommerOptional).isEmpty();
  }

  @Test
  public void getOmmerByBlockNumberAndIndexShouldReturnEmptyWhenBlockDoesNotHaveOmmers() {
    final BlockchainWithData data = setupBlockchain(1);
    final BlockchainQueries queries = data.blockchainQueries;
    final Block targetBlock = data.blockData.get(0).block;

    final Optional<BlockHeader> ommerOptional =
        queries.getOmmer(targetBlock.getHeader().getNumber(), 0);

    assertThat(targetBlock.getBody().getOmmers()).hasSize(0);
    assertThat(ommerOptional).isEmpty();
  }

  @Test
  public void getOmmerByBlockNumberAndIndexShouldReturnEmptyWhenIndexIsOutOfRange() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;
    final Block targetBlock = data.blockData.get(0).block;
    final int indexOutOfRange = targetBlock.getBody().getOmmers().size() + 1;

    final Optional<BlockHeader> ommerOptional =
        queries.getOmmer(targetBlock.getHeader().getNumber(), indexOutOfRange);

    assertThat(ommerOptional).isEmpty();
  }

  @Test
  public void getOmmerByBlockNumberAndIndexShouldReturnExpectedOmmerHeader() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;
    final Block targetBlock = data.blockData.get(data.blockData.size() - 1).block;
    final BlockHeader ommerBlockHeader = targetBlock.getBody().getOmmers().get(1);

    final BlockHeader retrievedOmmerBlockHeader =
        queries.getOmmer(targetBlock.getHeader().getNumber(), 1).get();

    assertThat(retrievedOmmerBlockHeader).isEqualTo(ommerBlockHeader);
  }

  @Test
  public void getLatestBlockOmmerByIndexShouldReturnEmptyWhenLatestBlockDoesNotHaveOmmers() {
    final BlockchainWithData data = setupBlockchain(1);
    final BlockchainQueries queries = data.blockchainQueries;

    final Optional<BlockHeader> ommerOptional = queries.getOmmer(0);

    assertThat(queries.blockByNumber(queries.headBlockNumber()).get().getOmmers()).hasSize(0);
    assertThat(ommerOptional).isEmpty();
  }

  @Test
  public void getLatestBlockOmmerByIndexShouldReturnEmptyWhenIndexIsOutOfRange() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;
    final Block targetBlock = data.blockData.get(data.blockData.size() - 1).block;
    final int indexOutOfRange = targetBlock.getBody().getOmmers().size() + 1;

    final Optional<BlockHeader> ommerOptional = queries.getOmmer(indexOutOfRange);

    assertThat(ommerOptional).isEmpty();
  }

  @Test
  public void getLatestBlockOmmerByIndexShouldReturnExpectedOmmerHeader() {
    final BlockchainWithData data = setupBlockchain(3);
    final BlockchainQueries queries = data.blockchainQueries;
    final Block targetBlock = data.blockData.get(data.blockData.size() - 1).block;
    final BlockHeader ommerBlockHeader = targetBlock.getBody().getOmmers().get(0);

    final BlockHeader retrievedOmmerBlockHeader = queries.getOmmer(0).get();

    assertThat(retrievedOmmerBlockHeader).isEqualTo(ommerBlockHeader);
  }

  private void assertBlockMatchesResult(
      final Block targetBlock, final BlockWithMetadata<TransactionWithMetadata, Hash> result) {
    assertThat(result.getHeader()).isEqualTo(targetBlock.getHeader());
    final List<Hash> expectedOmmers =
        targetBlock.getBody().getOmmers().stream()
            .map(BlockHeader::getHash)
            .collect(Collectors.toList());
    assertThat(result.getOmmers()).isEqualTo(expectedOmmers);

    for (int i = 0; i < result.getTransactions().size(); i++) {
      final TransactionWithMetadata txResult = result.getTransactions().get(i);
      final Transaction targetTx = targetBlock.getBody().getTransactions().get(i);
      assertThat(txResult.getTransaction()).isEqualTo(targetTx);
      assertThat(txResult.getTransactionIndex()).contains(i);
      assertThat(txResult.getBlockHash()).contains(targetBlock.getHash());
      assertThat(txResult.getBlockNumber()).contains(targetBlock.getHeader().getNumber());
    }
  }

  private void assertBlockMatchesResultWithTxHashes(
      final Block targetBlock, final BlockWithMetadata<Hash, Hash> result) {
    assertThat(result.getHeader()).isEqualTo(targetBlock.getHeader());
    final List<Hash> expectedOmmers =
        targetBlock.getBody().getOmmers().stream()
            .map(BlockHeader::getHash)
            .collect(Collectors.toList());
    assertThat(result.getOmmers()).isEqualTo(expectedOmmers);

    for (int i = 0; i < result.getTransactions().size(); i++) {
      final Hash txResult = result.getTransactions().get(i);
      final Transaction actualTx = targetBlock.getBody().getTransactions().get(i);
      assertThat(txResult).isEqualByComparingTo(actualTx.hash());
    }
  }

  private BlockchainWithData setupBlockchain(final int blocksToAdd) {
    return setupBlockchain(blocksToAdd, Collections.emptyList(), Collections.emptyList());
  }

  private BlockchainWithData setupBlockchain(
      final int blocksToAdd, final List<Address> accountsToSetup) {
    return setupBlockchain(blocksToAdd, accountsToSetup, Collections.emptyList());
  }

  private BlockchainWithData setupBlockchain(
      final int blocksToAdd, final List<Address> accountsToSetup, final List<UInt256> storageKeys) {
    checkArgument(blocksToAdd >= 1, "Must add at least one block to the queries");

    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();

    // Generate some queries data
    final List<BlockData> blockData = new ArrayList<>(blocksToAdd);
    final List<Block> blocks =
        gen.blockSequence(blocksToAdd, worldStateArchive, accountsToSetup, storageKeys);
    for (int i = 0; i < blocksToAdd; i++) {
      final Block block = blocks.get(i);
      final List<TransactionReceipt> receipts = gen.receipts(block);
      blockData.add(new BlockData(block, receipts));
    }

    // Setup blockchain
    final MutableBlockchain blockchain = createInMemoryBlockchain(blocks.get(0));
    blockData
        .subList(1, blockData.size())
        .forEach(b -> blockchain.appendBlock(b.block, b.receipts));

    return new BlockchainWithData(blockchain, blockData, worldStateArchive);
  }

  private static class BlockchainWithData {
    final MutableBlockchain blockchain;
    final List<BlockData> blockData;
    final WorldStateArchive worldStateArchive;
    final BlockchainQueries blockchainQueries;

    private BlockchainWithData(
        final MutableBlockchain blockchain,
        final List<BlockData> blockData,
        final WorldStateArchive worldStateArchive) {
      this.blockchain = blockchain;
      this.blockData = blockData;
      this.worldStateArchive = worldStateArchive;
      this.blockchainQueries = new BlockchainQueries(blockchain, worldStateArchive);
    }
  }

  private static class BlockData {
    final Block block;
    final List<TransactionReceipt> receipts;

    private BlockData(final Block block, final List<TransactionReceipt> receipts) {
      this.block = block;
      this.receipts = receipts;
    }
  }
}
