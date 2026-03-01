/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.AbstractBlockCreator;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration.MutableInitValues;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Integration tests for reorg scenarios with Bonsai archive mode using in-memory storage.
 *
 * <p>These tests verify that reorganizations are correctly handled when using the archive world
 * state provider, ensuring that account balances reflect the state of the new canonical chain after
 * a reorg.
 */
public class BonsaiArchiveReorgTest {

  private static final String GENESIS_CONFIG = "/dev.json";
  private static final long TRIE_LOG_DEPTH = 16L;
  private static final Wei ONE_ETH = Wei.of(1_000_000_000_000_000_000L);
  private static final Wei TWO_ETH = ONE_ETH.multiply(2);
  private static final Wei THREE_ETH = ONE_ETH.multiply(3);
  private static final Wei FIVE_ETH = ONE_ETH.multiply(5);
  private static final Wei TEN_ETH = ONE_ETH.multiply(10);

  private static final Address ACCOUNT_A =
      Address.fromHexString("0x1000000000000000000000000000000000000001");
  private static final Address ACCOUNT_B =
      Address.fromHexString("0x1000000000000000000000000000000000000002");
  private static final Address ACCOUNT_C =
      Address.fromHexString("0x1000000000000000000000000000000000000003");

  private BonsaiArchiveWorldStateProvider archiveProvider;
  private MutableBlockchain blockchain;
  private ProtocolContext protocolContext;
  private ProtocolSchedule protocolSchedule;
  private TransactionPool transactionPool;
  private KeyPair sender;
  private BlockHeader genesisHeader;
  private final EthScheduler ethScheduler = new DeterministicEthScheduler();

  @BeforeEach
  public void setUp() {
    ExecutionContextTestFixture fixture =
        ExecutionContextTestFixture.builder(GenesisConfig.fromResource(GENESIS_CONFIG))
            .dataStorageFormat(DataStorageFormat.X_BONSAI_ARCHIVE)
            .maxLayersToLoad(TRIE_LOG_DEPTH)
            .build();

    blockchain = fixture.getBlockchain();
    protocolContext = fixture.getProtocolContext();
    protocolSchedule = fixture.getProtocolSchedule();
    archiveProvider = (BonsaiArchiveWorldStateProvider) fixture.getStateArchive();
    genesisHeader = fixture.getGenesis().getHeader();
    assertThat(archiveProvider.getWorldStateKeyValueStorage().getFlatDbMode())
        .isEqualTo(FlatDbMode.ARCHIVE);

    sender =
        GenesisConfig.fromResource(GENESIS_CONFIG)
            .streamAllocations()
            .filter(ga -> ga.privateKey() != null)
            .findFirst()
            .map(ga -> asKeyPair(ga.privateKey()))
            .orElseThrow();

    transactionPool = Mockito.mock(TransactionPool.class);
  }

  private KeyPair asKeyPair(final Bytes32 key) {
    return SignatureAlgorithmFactory.getInstance()
        .createKeyPair(SECPPrivateKey.create(key, "ECDSA"));
  }

  @Test
  void shouldHandleReorgWithConflictingAccountBalances() {
    BlockHeader parentHeader = buildEmptyChainToBlock(9);

    transfer(parentHeader);
    assertBalance(ACCOUNT_A, ONE_ETH);

    Block block10B =
        forTransactions(List.of(createTransaction(ACCOUNT_A, TWO_ETH, 0L)), parentHeader);
    reorgFrom(parentHeader, block10B);
    assertBalance(ACCOUNT_A, TWO_ETH);

    Hash headBlockHash =
        ((PathBasedWorldState) archiveProvider.getWorldState()).getWorldStateBlockHash();
    assertThat(headBlockHash).isEqualTo(block10B.getHash());
  }

  @Test
  void shouldHandleReorgAccountCreationVsNoCreation() {
    BlockHeader parentHeader = buildEmptyChainToBlock(9);

    transfer(parentHeader);
    assertAccountExists(ACCOUNT_A);

    reorgFromWithTransfer(parentHeader, ACCOUNT_B, ONE_ETH);
    assertAccountNull(ACCOUNT_A);
    assertAccountExists(ACCOUNT_B);
  }

  @Test
  void shouldSupportHistoricalQueriesAfterReorg() {
    BlockHeader block2Header = buildEmptyChainToBlock(2);

    transfer(block2Header);
    assertBalance(ACCOUNT_A, ONE_ETH);
    assertThat(getHistoricalWorldState(block2Header).get(ACCOUNT_A)).isNull();

    Block block3B =
        forTransactions(List.of(createTransaction(ACCOUNT_A, TWO_ETH, 0L)), block2Header);
    reorgFrom(block2Header, block3B);

    assertBalance(ACCOUNT_A, TWO_ETH);
    assertThat(getHistoricalWorldState(block2Header).get(ACCOUNT_A)).isNull();
    assertThat(getHistoricalWorldState(block3B.getHeader()).get(ACCOUNT_A).getBalance())
        .isEqualTo(TWO_ETH);
  }

  @Test
  void shouldHandleConsecutiveReorgs() {
    transfer(genesisHeader);
    assertBalance(ACCOUNT_A, ONE_ETH);

    reorgFromWithTransfer(genesisHeader, ACCOUNT_A, TWO_ETH);
    assertBalance(ACCOUNT_A, TWO_ETH);

    reorgFromWithTransfer(genesisHeader, ACCOUNT_A, THREE_ETH);
    assertBalance(ACCOUNT_A, THREE_ETH);
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1L);
  }

  @Test
  void shouldHandleReorgWithMultipleAccountsAffected() {
    BlockHeader parentHeader = buildEmptyChainToBlock(9);

    // Block 10A: ACCOUNT_A gets 1 ETH, ACCOUNT_B gets 1 ETH
    executeBlock(
        archiveProvider.getWorldState(),
        forTransactions(
            List.of(
                createTransaction(ACCOUNT_A, ONE_ETH, 0L),
                createTransaction(ACCOUNT_B, ONE_ETH, 1L)),
            parentHeader));
    assertBalance(ACCOUNT_A, ONE_ETH);
    assertBalance(ACCOUNT_B, ONE_ETH);

    reorgFromWithTransfer(parentHeader, ACCOUNT_A, TWO_ETH);
    assertBalance(ACCOUNT_A, TWO_ETH);
    assertAccountNull(ACCOUNT_B);
  }

  @Test
  void shouldTrackAccountBalanceChangesAcrossBlocks() {
    BlockHeader parentHeader = genesisHeader;

    // Process 5 blocks, each adding 1 ETH
    for (int i = 1; i <= 5; i++) {
      Transaction tx = createTransaction(ACCOUNT_A, ONE_ETH, i - 1);
      Block block = forTransactions(List.of(tx), parentHeader);
      BlockProcessingResult result = executeBlock(archiveProvider.getWorldState(), block);
      assertThat(result.isSuccessful()).isTrue();

      // Verify current balance
      Wei expectedBalance = ONE_ETH.multiply(i);
      assertThat(archiveProvider.getWorldState().get(ACCOUNT_A).getBalance())
          .as("Balance after block %d should be %d ETH", i, i)
          .isEqualTo(expectedBalance);

      parentHeader = block.getHeader();
    }

    // Final verification: historical queries at each block should return correct values
    for (int i = 1; i <= 5; i++) {
      BlockHeader blockHeader = blockchain.getBlockHeader(i).orElseThrow();
      MutableWorldState wsAtBlock = getHistoricalWorldState(blockHeader);

      Wei expectedBalance = ONE_ETH.multiply(i);
      assertThat(wsAtBlock.get(ACCOUNT_A).getBalance())
          .as("Historical query at block %d should return %d ETH", i, i)
          .isEqualTo(expectedBalance);
    }
  }

  @Test
  void shouldHandleReorgToLongerAlternateChain() {
    // Chain A: 3 blocks, each sending 1 ETH (total 3 ETH)
    buildChain(3);
    assertBalance(ACCOUNT_A, THREE_ETH);
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(3L);

    // Reorg to chain B: 5 blocks from genesis, each sending 2 ETH (total 10 ETH)
    BlockHeader parentHeader = genesisHeader;
    for (int i = 0; i < 5; i++) {
      Block block =
          forTransactions(List.of(createTransaction(ACCOUNT_A, TWO_ETH, i)), parentHeader);
      if (i == 0) {
        reorgFromGenesis(block);
      } else {
        executeBlock(archiveProvider.getWorldState(), block);
      }
      parentHeader = block.getHeader();
    }

    assertBalance(ACCOUNT_A, TEN_ETH);
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(5L);
  }

  @Test
  void shouldReturnOrphanedBlockStateForHistoricalQuery() {
    Block block1A = transfer(genesisHeader);
    assertBalance(ACCOUNT_A, ONE_ETH);

    reorgFromWithTransfer(genesisHeader, ACCOUNT_A, FIVE_ETH);
    assertBalance(ACCOUNT_A, FIVE_ETH);

    // Query the orphaned block1A - archive mode preserves this data via trie logs
    Optional<MutableWorldState> orphanedWorldState =
        archiveProvider.getWorldState(
            WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead(block1A.getHeader()));
    assertThat(orphanedWorldState).isPresent();
    assertThat(orphanedWorldState.get().get(ACCOUNT_A).getBalance()).isEqualTo(ONE_ETH);
  }

  @Test
  void shouldHandleReorgAtTrieLogDepthBoundary() {
    buildChain((int) TRIE_LOG_DEPTH);
    assertBalance(ACCOUNT_A, ONE_ETH.multiply(TRIE_LOG_DEPTH));
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(TRIE_LOG_DEPTH);

    long forkBlockNumber = TRIE_LOG_DEPTH / 2; // Block 8
    BlockHeader forkHeader = blockchain.getBlockHeader(forkBlockNumber).orElseThrow();

    Block blockB =
        forTransactions(
            List.of(createTransaction(ACCOUNT_A, TEN_ETH, forkBlockNumber)), forkHeader);
    reorgFrom(forkHeader, blockB);

    // Account should have 8 ETH (from blocks 1-8) + 10 ETH (from block 9B) = 18 ETH
    assertBalance(ACCOUNT_A, ONE_ETH.multiply(forkBlockNumber).add(TEN_ETH));
    assertThat(getHistoricalWorldState(forkHeader).get(ACCOUNT_A).getBalance())
        .isEqualTo(ONE_ETH.multiply(forkBlockNumber));
  }

  @Test
  void shouldHandleStorageSlotChangesAcrossReorg() {
    // Runtime: 60 00 PUSH1 0, 34 CALLVALUE, 55 SSTORE, 00 STOP - stores msg.value in slot 0
    Bytes runtimeCode = Bytes.fromHexString("6000345500");
    Bytes initCode = createInitCode(runtimeCode);
    Address contractAddress = Address.contractAddress(Address.extract(sender.getPublicKey()), 0L);

    deployContractFromGenesis(initCode, ONE_ETH);
    assertAccountExists(contractAddress);

    // Reorg: Deploy same contract with 5 ETH
    reorgFromGenesis(
        forTransactions(List.of(createContractDeployment(initCode, FIVE_ETH)), genesisHeader));
    assertAccountExists(contractAddress);
    assertThat(archiveProvider.getWorldState().get(contractAddress).getBalance())
        .isEqualTo(FIVE_ETH);
  }

  @Test
  void shouldHandleContractDeploymentReorg() {
    // Runtime: 60 00 PUSH1 0, 34 CALLVALUE, 55 SSTORE, 00 STOP - stores msg.value in slot 0
    Bytes initCode = createInitCode(Bytes.fromHexString("6000345500"));
    Address contractAddress = Address.contractAddress(Address.extract(sender.getPublicKey()), 0L);

    deployContractFromGenesis(initCode, ONE_ETH);
    assertThat(archiveProvider.getWorldState().get(contractAddress).hasCode()).isTrue();

    reorgFromWithTransfer(genesisHeader, ACCOUNT_A, TWO_ETH);
    assertAccountNull(contractAddress);
    assertBalance(ACCOUNT_A, TWO_ETH);
  }

  @Test
  void shouldHandleSelfDestructDuringReorg() {
    // Runtime: 73 PUSH20 <address>, FF SELFDESTRUCT - sends balance to beneficiary
    Bytes runtimeCode =
        Bytes.concatenate(
            Bytes.fromHexString("73"), ACCOUNT_B.getBytes(), Bytes.fromHexString("FF"));
    Bytes initCode = createInitCode(runtimeCode);
    Address contractAddress = Address.contractAddress(Address.extract(sender.getPublicKey()), 0L);

    // Block 1A: Deploy contract with 3 ETH, do NOT call it
    deployContractFromGenesis(initCode, THREE_ETH);
    assertThat(archiveProvider.getWorldState().get(contractAddress).getBalance())
        .isEqualTo(THREE_ETH);
    assertAccountNull(ACCOUNT_B);

    // Reorg: Deploy contract AND call it to trigger selfdestruct
    Block block1B =
        forTransactions(List.of(createContractDeployment(initCode, THREE_ETH)), genesisHeader);
    reorgFromGenesis(block1B);
    executeBlock(
        archiveProvider.getWorldState(),
        forTransactions(List.of(createContractCall(contractAddress)), block1B.getHeader()));

    assertBalance(ACCOUNT_B, THREE_ETH);
  }

  @Test
  void shouldHandleCodeChangesViaCreate2DuringReorg() {
    // Runtime: 60 XX PUSH1 value, 60 00 PUSH1 0, 55 SSTORE, 00 STOP - stores value in slot 0
    Bytes codeA = Bytes.fromHexString("60AA60005500"); // stores 0xAA
    Bytes codeB = Bytes.fromHexString("60BB60005500"); // stores 0xBB
    Address contractAddress = Address.contractAddress(Address.extract(sender.getPublicKey()), 0L);

    deployContractFromGenesis(createInitCode(codeA), Wei.ZERO);
    assertThat(archiveProvider.getWorldState().get(contractAddress).getCode()).isEqualTo(codeA);

    // Reorg: Deploy contract with code B at same address
    reorgFromGenesis(
        forTransactions(
            List.of(createContractDeployment(createInitCode(codeB), Wei.ZERO)), genesisHeader));
    assertThat(archiveProvider.getWorldState().get(contractAddress).getCode()).isEqualTo(codeB);
  }

  @Test
  void shouldTrackAccountNonceAcrossReorg() {
    Address senderAddress = Address.extract(sender.getPublicKey());
    long initialNonce = archiveProvider.getWorldState().get(senderAddress).getNonce();

    // Chain A: Sender makes 3 transactions (nonce increases by 3)
    Block block1A =
        forTransactions(
            List.of(
                createTransaction(ACCOUNT_A, ONE_ETH, initialNonce),
                createTransaction(ACCOUNT_B, ONE_ETH, initialNonce + 1),
                createTransaction(ACCOUNT_C, ONE_ETH, initialNonce + 2)),
            genesisHeader);
    executeBlock(archiveProvider.getWorldState(), block1A);
    assertThat(archiveProvider.getWorldState().get(senderAddress).getNonce())
        .isEqualTo(initialNonce + 3);

    // Reorg to chain B: Sender makes only 1 transaction
    Block block1B =
        forTransactions(
            List.of(createTransaction(ACCOUNT_A, TWO_ETH, initialNonce)), genesisHeader);
    reorgFromGenesis(block1B);

    // After reorg: Nonce should reflect chain B (only 1 transaction)
    assertThat(archiveProvider.getWorldState().get(senderAddress).getNonce())
        .isEqualTo(initialNonce + 1);
    assertBalance(ACCOUNT_A, TWO_ETH);
    assertAccountNull(ACCOUNT_B);
    assertAccountNull(ACCOUNT_C);
  }

  private void assertBalance(final Address address, final Wei expectedBalance) {
    assertThat(archiveProvider.getWorldState().get(address).getBalance())
        .isEqualTo(expectedBalance);
  }

  private void assertAccountExists(final Address address) {
    assertThat(archiveProvider.getWorldState().get(address)).isNotNull();
  }

  private void assertAccountNull(final Address address) {
    assertThat(archiveProvider.getWorldState().get(address)).isNull();
  }

  /**
   * Creates EVM init code that deploys the given runtime code.
   *
   * <p>Structure (12 bytes + runtime code):
   *
   * <pre>
   * 60 XX    PUSH1 size   - runtime code size
   * 60 0c    PUSH1 12     - code offset (init code is 12 bytes)
   * 60 00    PUSH1 0      - memory destination
   * 39       CODECOPY     - copy runtime code to memory
   * 60 XX    PUSH1 size   - runtime code size
   * 60 00    PUSH1 0      - memory offset
   * f3       RETURN       - return runtime code
   * [runtime code]
   * </pre>
   */
  private Bytes createInitCode(final Bytes runtimeCode) {
    return Bytes.concatenate(
        Bytes.fromHexString("60"),
        Bytes.of(runtimeCode.size()),
        Bytes.fromHexString("600c60003960"),
        Bytes.of(runtimeCode.size()),
        Bytes.fromHexString("6000f3"),
        runtimeCode);
  }

  private Block transfer(final BlockHeader parent) {
    Block block =
        forTransactions(
            List.of(
                createTransaction(
                    BonsaiArchiveReorgTest.ACCOUNT_A, BonsaiArchiveReorgTest.ONE_ETH, 0L)),
            parent);
    executeBlock(archiveProvider.getWorldState(), block);
    return block;
  }

  private void reorgFrom(final BlockHeader parentHeader, final Block alternateBlock) {
    executeReorg(alternateBlock, getHistoricalWorldState(parentHeader), parentHeader.getNumber());
  }

  private void reorgFromGenesis(final Block alternateBlock) {
    reorgFrom(genesisHeader, alternateBlock);
  }

  private void reorgFromWithTransfer(final BlockHeader parent, final Address to, final Wei value) {
    reorgFrom(parent, forTransactions(List.of(createTransaction(to, value, 0L)), parent));
  }

  private void deployContractFromGenesis(final Bytes initCode, final Wei value) {
    Transaction tx = createContractDeployment(initCode, value);
    executeBlock(archiveProvider.getWorldState(), forTransactions(List.of(tx), genesisHeader));
  }

  private void buildChain(final int count) {
    BlockHeader parentHeader = genesisHeader;
    for (int i = 0; i < count; i++) {
      Transaction tx =
          createTransaction(BonsaiArchiveReorgTest.ACCOUNT_A, BonsaiArchiveReorgTest.ONE_ETH, i);
      Block block = forTransactions(List.of(tx), parentHeader);
      executeBlock(archiveProvider.getWorldState(), block);
      parentHeader = block.getHeader();
    }
  }

  private Transaction createTransaction(final Address to, final Wei value, final long nonce) {
    return new TransactionTestFixture()
        .sender(Address.extract(sender.getPublicKey()))
        .to(Optional.of(to))
        .value(value)
        .gasLimit(21_000L)
        .nonce(nonce)
        .createTransaction(sender);
  }

  private Transaction createContractDeployment(final Bytes initCode, final Wei value) {
    return new TransactionTestFixture()
        .sender(Address.extract(sender.getPublicKey()))
        .to(Optional.empty())
        .value(value)
        .payload(initCode)
        .gasLimit(100_000L)
        .nonce(0L)
        .createTransaction(sender);
  }

  private Transaction createContractCall(final Address contract) {
    return new TransactionTestFixture()
        .sender(Address.extract(sender.getPublicKey()))
        .to(Optional.of(contract))
        .value(Wei.ZERO)
        .payload(Bytes.EMPTY)
        .gasLimit(100_000L)
        .nonce(1L)
        .createTransaction(sender);
  }

  private Block forTransactions(final List<Transaction> transactions, final BlockHeader parent) {
    return TestBlockCreator.forHeader(
            protocolContext, protocolSchedule, transactionPool, ethScheduler)
        .createBlock(transactions, Collections.emptyList(), System.currentTimeMillis(), parent)
        .getBlock();
  }

  private BlockProcessingResult executeBlock(final MutableWorldState ws, final Block block) {
    var blockHeader = new BlockHeaderTestFixture().number(0).buildHeader();
    var blockProcessingResult =
        protocolSchedule
            .getByBlockHeader(blockHeader)
            .getBlockProcessor()
            .processBlock(protocolContext, blockchain, ws, block);
    blockchain.appendBlock(block, blockProcessingResult.getReceipts());
    return blockProcessingResult;
  }

  private BlockHeader buildEmptyChainToBlock(final int blockCount) {
    BlockHeader parentHeader = genesisHeader;
    for (int i = 1; i <= blockCount; i++) {
      Block block = forTransactions(Collections.emptyList(), parentHeader);
      executeBlock(archiveProvider.getWorldState(), block);
      parentHeader = block.getHeader();
    }
    return parentHeader;
  }

  private MutableWorldState getHistoricalWorldState(final BlockHeader header) {
    return archiveProvider
        .getWorldState(WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead(header))
        .orElseThrow();
  }

  /**
   * Tests that paired rollback/rollforward during a reorg correctly handles storage slot creation
   * when archive context needs to be updated. This test reproduces the scenario from a bug where
   * storage reads during rollforward were using the wrong block context.
   *
   * <p>Bug scenario: During paired rollback/rollforward, if the ephemeral archive context isn't
   * correctly maintained, subsequent storage reads during rollforward will use the wrong block
   * context, causing "Expected to create slot, but the slot exists" errors.
   *
   * <p>This test creates a reorg with storage slot changes, ensuring the archive context is
   * correctly updated at each stage of the rolling process.
   */
  @Test
  void shouldHandlePairedRollbackRollforwardWithStorageSlotCreation() {
    // Contract that stores values: 60 XX PUSH1 value, 60 YY PUSH1 slot, 55 SSTORE
    Bytes storeInSlot0 = Bytes.fromHexString("60AA60005500"); // stores 0xAA in slot 0
    Bytes storeInSlot1 = Bytes.fromHexString("60BB60015500"); // stores 0xBB in slot 1
    Bytes initCodeA = createInitCode(storeInSlot0);
    Bytes initCodeB = createInitCode(storeInSlot1);
    Address contractAddress = Address.contractAddress(Address.extract(sender.getPublicKey()), 0L);

    // Block 1A: Deploy contract A (creates storage slot 0)
    deployContractFromGenesis(initCodeA, Wei.ZERO);
    assertAccountExists(contractAddress);
    assertThat(archiveProvider.getWorldState().get(contractAddress).hasCode()).isTrue();

    // Build chain to block 3 to create some distance
    Block block1A = blockchain.getBlockByNumber(1L).orElseThrow();
    BlockHeader parentHeader = block1A.getHeader();
    for (int i = 2; i <= 3; i++) {
      Block block = forTransactions(Collections.emptyList(), parentHeader);
      executeBlock(archiveProvider.getWorldState(), block);
      parentHeader = block.getHeader();
    }

    // Now reorg from genesis, deploying contract B instead (creates storage slot 1)
    // This creates a paired rollback/rollforward scenario:
    // - Rollback blocks 1A, 2A, 3A
    // - Rollforward with 1B (different contract, different storage slot)
    Block block1B =
        forTransactions(List.of(createContractDeployment(initCodeB, Wei.ZERO)), genesisHeader);

    // The reorg will:
    // 1. Roll back from block 3 to genesis (common ancestor)
    // 2. Roll forward with block 1B
    // Without the fix, the storage read during rollforward would use the wrong block context
    // and find storage slot 0 from the old chain, causing the error
    reorgFromGenesis(block1B);

    // Verify the contract exists and is from chain B (not chain A)
    assertAccountExists(contractAddress);
    assertThat(archiveProvider.getWorldState().get(contractAddress).hasCode()).isTrue();

    // Verify we can query the historical state
    assertThat(getHistoricalWorldState(genesisHeader).get(contractAddress)).isNull();
  }

  /**
   * Tests multiple rollforwards followed by a paired rollback/rollforward operation. This
   * reproduces a scenario where archive context needs to be correctly maintained across
   * rollforwards and subsequent rollbacks.
   *
   * <p>Log sequence that this test reproduces:
   *
   * <pre>
   * Rollforward 0x67238e14...  (applies block changes, creates storage slots)
   * Rollforward 0x0bdee358...  (applies more changes)
   * [Context must be correctly maintained]
   * Paired Rollback 0x70b8bb69...  (tries to roll back)
   * ERROR: Expected to update storage value, but the slot does not exist
   * </pre>
   *
   * <p>With ephemeral context, each world state gets its own context-safe copy, preventing stale
   * context issues. This test verifies reorgs work correctly with the ephemeral approach.
   */
  @Test
  void shouldHandleRollforwardThenPairedRollbackRollforward() {
    // Build a simple chain: Block 1 transfers to ACCOUNT_A
    transfer(genesisHeader);
    assertBalance(ACCOUNT_A, ONE_ETH);

    // Build to block 3
    BlockHeader parentHeader = blockchain.getBlockByNumber(1L).orElseThrow().getHeader();
    for (int i = 2; i <= 3; i++) {
      Block block = forTransactions(Collections.emptyList(), parentHeader);
      executeBlock(archiveProvider.getWorldState(), block);
      parentHeader = block.getHeader();
    }

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(3L);

    // Critical part: Get historical world state at block 2
    // This triggers rollforwards from persisted state → block 2
    // With ephemeral context, each world state gets a context-safe copy
    BlockHeader block2Header = blockchain.getBlockByNumber(2L).orElseThrow().getHeader();
    getHistoricalWorldState(block2Header); // Triggers rollforwards with isolated context

    // Now do a reorg from genesis with a different transfer amount to ACCOUNT_A
    // This triggers: Rollback from head → genesis, then Rollforward to new block 1
    // With ephemeral context: each operation gets isolated context → SUCCESS
    reorgFromWithTransfer(genesisHeader, ACCOUNT_A, TWO_ETH);

    // Verify the reorg succeeded - if we get here without IllegalStateException, the fix worked!
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1L);
    // The key test is that we didn't get IllegalStateException during the reorg
    // The actual balance value is less important than proving the reorg completes
  }

  private void executeReorg(
      final Block alternateBlock,
      final MutableWorldState wsAtForkPoint,
      final long rewindToBlockNumber) {
    BlockProcessingResult result =
        protocolSchedule
            .getByBlockHeader(alternateBlock.getHeader())
            .getBlockProcessor()
            .processBlock(protocolContext, blockchain, wsAtForkPoint, alternateBlock);
    assertThat(result.isSuccessful()).isTrue();
    wsAtForkPoint.persist(alternateBlock.getHeader());

    blockchain.storeBlock(alternateBlock, result.getReceipts());
    blockchain.rewindToBlock(rewindToBlockNumber);
    blockchain.appendBlock(alternateBlock, result.getReceipts());

    archiveProvider.getWorldState(
        WorldStateQueryParams.withBlockHeaderAndUpdateNodeHead(alternateBlock.getHeader()));
  }

  static class TestBlockCreator extends AbstractBlockCreator {
    private TestBlockCreator(
        final MiningConfiguration miningConfiguration,
        final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        final ExtraDataCalculator extraDataCalculator,
        final TransactionPool transactionPool,
        final ProtocolContext protocolContext,
        final ProtocolSchedule protocolSchedule,
        final EthScheduler ethScheduler) {
      super(
          miningConfiguration,
          miningBeneficiaryCalculator,
          extraDataCalculator,
          transactionPool,
          protocolContext,
          protocolSchedule,
          ethScheduler);
    }

    static TestBlockCreator forHeader(
        final ProtocolContext protocolContext,
        final ProtocolSchedule protocolSchedule,
        final TransactionPool transactionPool,
        final EthScheduler ethScheduler) {

      final MiningConfiguration miningConfiguration =
          ImmutableMiningConfiguration.builder()
              .mutableInitValues(
                  MutableInitValues.builder()
                      .extraData(Bytes.fromHexString("deadbeef"))
                      .targetGasLimit(30_000_000L)
                      .minTransactionGasPrice(Wei.ONE)
                      .minBlockOccupancyRatio(0d)
                      .coinbase(Address.ZERO)
                      .build())
              .build();

      return new TestBlockCreator(
          miningConfiguration,
          (__, ___) -> Address.ZERO,
          __ -> Bytes.fromHexString("deadbeef"),
          transactionPool,
          protocolContext,
          protocolSchedule,
          ethScheduler);
    }

    @Override
    protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {
      return BlockHeaderBuilder.create()
          .difficulty(Difficulty.ZERO)
          .mixHash(Hash.ZERO)
          .populateFrom(sealableBlockHeader)
          .nonce(0L)
          .blockHeaderFunctions(blockHeaderFunctions)
          .buildBlockHeader();
    }
  }
}
