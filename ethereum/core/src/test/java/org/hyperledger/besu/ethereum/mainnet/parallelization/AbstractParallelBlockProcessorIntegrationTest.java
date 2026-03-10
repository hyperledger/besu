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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ImmutableBalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiAccount;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

/**
 * Abstract integration tests for parallel block processing. Subclasses provide specific parallel
 * preprocessing implementations (BAL or Optimistic) to verify that both produce identical state
 * roots to sequential execution across various conflict scenarios.
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractParallelBlockProcessorIntegrationTest {

  // ==================== Genesis Configuration ====================
  protected static final String PARALLEL_GENESIS_RESOURCE =
      "/org/hyperledger/besu/ethereum/mainnet/parallelization/genesis-parallel-it.json";
  protected static final BigInteger CHAIN_ID = BigInteger.valueOf(42);

  // ==================== Test Accounts (from genesis) ====================
  protected static final KeyPair SENDER_1 =
      createKeyPair("c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3");
  protected static final KeyPair SENDER_2 =
      createKeyPair("fc5141e75bf622179f8eedada7fab3e2e6b3e3da8eb9df4f46d84df22df7430e");
  protected static final String SENDER_2_ADDRESS = "0x7f2d653f56ea8de6ffa554c7a0cd4e03af79f3eb";

  // Coinbase: existing genesis account to avoid EIP-161 empty account issues
  protected static final Address COINBASE =
      Address.fromHexString("0xa4664C40AACeBD82A2Db79f0ea36C06Bc6A19Adb");

  // ==================== Recipient Addresses ====================
  protected static final String RECIPIENT_1 = "0x0000000000000000000000000000000000000002";
  protected static final String RECIPIENT_2 = "0x0000000000000000000000000000000000000003";
  protected static final String RECIPIENT_3 = "0x0000000000000000000000000000000000000004";
  protected static final String RECIPIENT_4 = "0x0000000000000000000000000000000000000005";
  protected static final String RECIPIENT_5 = "0x0000000000000000000000000000000000000006";

  // ==================== Contract Addresses (from genesis) ====================
  protected static final String STORAGE_CONTRACT = "0x00000000000000000000000000000000000fffff";
  protected static final String REVERT_CONTRACT = "0x0000000000000000000000000000000000000666";
  // ParallelTestStorage with incrementSlot1() — slot1 initial value = 10 (0xa in genesis)
  protected static final String INCREMENT_CONTRACT = "0x00000000000000000000000000000000000eeeee";

  // ==================== Wei Constants ====================
  protected static final long ONE_ETH = 1_000_000_000_000_000_000L;
  protected static final long HALF_ETH = ONE_ETH / 2;
  protected static final long TENTH_ETH = ONE_ETH / 10;
  protected static final long QUARTER_ETH = ONE_ETH / 4;

  // ==================== Gas/Fee Constants ====================
  protected static final long DEFAULT_GAS_LIMIT = 300_000L;
  protected static final long CONTRACT_GAS_LIMIT = 3_000_000L;
  protected static final Wei ZERO_BASE_FEE = Wei.ZERO;
  protected static final Wei LOW_BASE_FEE = Wei.of(1_000_000_000L); // 1 Gwei
  protected static final Wei DEFAULT_PRIORITY_FEE = Wei.of(2_000_000_000L); // 2 Gwei
  protected static final Wei DEFAULT_MAX_FEE = Wei.of(10_000_000_000L); // 10 Gwei

  // ==================== Sequential Configuration ====================
  // Pure sequential config with all BAL optimizations disabled
  protected static final BalConfiguration SEQUENTIAL_CONFIG =
      ImmutableBalConfiguration.builder().isBalOptimisationEnabled(false).build();

  // ==================== Test Infrastructure ====================
  protected ProtocolContext protocolContext;
  protected WorldStateArchive worldStateArchive;
  protected DefaultBlockchain blockchain;
  protected ProtocolSchedule protocolSchedule;

  /** Creates the parallel preprocessing implementation for this variant. */
  protected abstract ParallelTransactionPreprocessing createParallelPreprocessing(
      MainnetTransactionProcessor transactionProcessor);

  /** Returns a descriptive name for this variant (for test display). */
  protected abstract String getVariantName();

  @BeforeEach
  void setUp() {
    final ExecutionContextTestFixture fixture =
        ExecutionContextTestFixture.builder(GenesisConfig.fromResource(PARALLEL_GENESIS_RESOURCE))
            .dataStorageFormat(DataStorageFormat.BONSAI)
            .build();
    worldStateArchive = fixture.getStateArchive();
    protocolContext = fixture.getProtocolContext();
    blockchain = (DefaultBlockchain) fixture.getBlockchain();
    protocolSchedule = fixture.getProtocolSchedule();
  }

  // ==================== Test Classes ====================

  @Nested
  @DisplayName("Non-Conflicting Transactions (Zero Reward)")
  class NonConflictingTransactionsZeroReward {

    @Test
    @DisplayName("Independent transfers produce same state root")
    void independentTransfers() {
      final Transaction tx1 = transfer(SENDER_1, 0, RECIPIENT_1, ONE_ETH);
      final Transaction tx2 = transfer(SENDER_2, 0, RECIPIENT_2, 2 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0xef95a24ab059feb70c632c1d7dfc27eec6d1655c638bc23106d0637b07ff6b41",
              ZERO_BASE_FEE,
              tx1,
              tx2));
    }

    @Test
    @DisplayName("Multiple independent transfers produce same state root")
    void multipleIndependentTransfers() {
      final Transaction tx1 = transfer(SENDER_1, 0, RECIPIENT_1, TENTH_ETH);
      final Transaction tx2 = transfer(SENDER_2, 0, RECIPIENT_2, 2 * TENTH_ETH);
      final Transaction tx3 = transfer(SENDER_1, 1, RECIPIENT_3, TENTH_ETH + HALF_ETH / 10);
      final Transaction tx4 = transfer(SENDER_2, 1, RECIPIENT_4, QUARTER_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x19cdee8223e68071cf62802339148bdfcd3dd0fab75c209d6f7cbaefd8b6dee2",
              ZERO_BASE_FEE,
              tx1,
              tx2,
              tx3,
              tx4));
    }

    @Test
    @DisplayName("Independent contract storage updates produce same state root")
    void independentStorageUpdates() {
      final Address contract = Address.fromHexStringStrict(STORAGE_CONTRACT);

      final Transaction tx1 = storageUpdate(SENDER_1, 0, contract, "setSlot1", 100);
      final Transaction tx2 = storageUpdate(SENDER_2, 0, contract, "setSlot2", 200);
      final Transaction tx3 = storageUpdate(SENDER_1, 1, contract, "setSlot3", 300);

      assertStateRootsMatch(
          executeAndCompare(
              "0x31898c533edafdd09b434cb7a75ed7606198f31b6e2b8e6b51eefb3c56c72de7",
              ZERO_BASE_FEE,
              tx1,
              tx2,
              tx3));
    }
  }

  @Nested
  @DisplayName("Non-Conflicting Transactions (With Rewards)")
  class NonConflictingTransactionsWithRewards {

    @Test
    @DisplayName("Independent transfers with coinbase rewards produce same state root")
    void independentTransfersWithRewards() {
      final Transaction tx1 = transferWithFees(SENDER_1, 0, RECIPIENT_1, ONE_ETH);
      final Transaction tx2 = transferWithFees(SENDER_2, 0, RECIPIENT_2, 2 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x49b9ce081187cc9405cbf6efa95f47b6122f66057cd1cd511bce58d39cd08798",
              LOW_BASE_FEE,
              tx1,
              tx2));
    }

    @Test
    @DisplayName("Multiple transfers with varying fees produce same state root")
    void multipleTransfersWithFees() {
      final Transaction tx1 = transferWithFees(SENDER_1, 0, RECIPIENT_1, TENTH_ETH);
      final Transaction tx2 = transferWithFees(SENDER_2, 0, RECIPIENT_2, 2 * TENTH_ETH);
      final Transaction tx3 = transferWithFees(SENDER_1, 1, RECIPIENT_3, QUARTER_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x5a2c2b20cd6d5dc224f6916911c60d34a335ecf582f052c2579f0d9c6e6cfe32",
              LOW_BASE_FEE,
              tx1,
              tx2,
              tx3));
    }

    @Test
    @DisplayName("Storage updates with rewards produce same state root")
    void storageUpdatesWithRewards() {
      final Address contract = Address.fromHexStringStrict(STORAGE_CONTRACT);

      final Transaction tx1 = storageUpdateWithFees(SENDER_1, 0, contract, "setSlot1", 100);
      final Transaction tx2 = transferWithFees(SENDER_2, 0, RECIPIENT_1, ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0xbe89b33437a30b6ed7cf551014637e8422979e3ec24b8c79d62a8c4270386dd0",
              LOW_BASE_FEE,
              tx1,
              tx2));
    }
  }

  @Nested
  @DisplayName("Same Sender Conflicts (Nonce)")
  class SameSenderConflicts {

    @Test
    @DisplayName("Sequential transactions from same sender (zero reward)")
    void sameSenderSequentialZeroReward() {
      final Transaction tx1 = transfer(SENDER_1, 0, RECIPIENT_3, ONE_ETH);
      final Transaction tx2 = transfer(SENDER_1, 1, RECIPIENT_4, 2 * ONE_ETH);
      final Transaction tx3 = transfer(SENDER_1, 2, RECIPIENT_5, 3 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0xbc1c73587f26ece98f71088e22c874104d626930102dfb3a19cc9fc7c10cae11",
              ZERO_BASE_FEE,
              tx1,
              tx2,
              tx3));
    }

    @Test
    @DisplayName("Sequential transactions from same sender (with rewards)")
    void sameSenderSequentialWithRewards() {
      final Transaction tx1 = transferWithFees(SENDER_1, 0, RECIPIENT_3, ONE_ETH);
      final Transaction tx2 = transferWithFees(SENDER_1, 1, RECIPIENT_4, 2 * ONE_ETH);
      final Transaction tx3 = transferWithFees(SENDER_1, 2, RECIPIENT_5, 3 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x0935bcb4b5b85cc3ed57056e92cdae462e15ecd0c0b64dda09eb78f99c3a66ab",
              LOW_BASE_FEE,
              tx1,
              tx2,
              tx3));
    }
  }

  @Nested
  @DisplayName("Balance Conflicts (Receiver = Sender)")
  class BalanceConflicts {

    @Test
    @DisplayName("Transfer to account that then sends (zero reward)")
    void transferToAccountThatSendsZeroReward() {
      final Transaction tx1 = transfer(SENDER_1, 0, SENDER_2_ADDRESS, ONE_ETH);
      final Transaction tx2 = transfer(SENDER_2, 0, RECIPIENT_1, 2 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x4934236ec104badbc7e7876bd8112cffd9cd43dc564b1acf5079c80476beef6b",
              ZERO_BASE_FEE,
              tx1,
              tx2));
    }

    @Test
    @DisplayName("Transfer to account that then sends (with rewards)")
    void transferToAccountThatSendsWithRewards() {
      final Transaction tx1 = transferWithFees(SENDER_1, 0, SENDER_2_ADDRESS, ONE_ETH);
      final Transaction tx2 = transferWithFees(SENDER_2, 0, RECIPIENT_1, 2 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x0e1201e1266776eb75009e15401bd007ad87900487369f3c07dfc4b0598ed3b4",
              LOW_BASE_FEE,
              tx1,
              tx2));
    }

    @Test
    @DisplayName("Multiple transfers to same receiver (zero reward)")
    void multipleTransfersToSameReceiver() {
      final Transaction tx1 = transfer(SENDER_1, 0, RECIPIENT_1, ONE_ETH);
      final Transaction tx2 = transfer(SENDER_2, 0, RECIPIENT_1, 2 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x724158e7c6f2e446c16981588664277fc69fedacd61b0f9338daffa71ee179dd",
              ZERO_BASE_FEE,
              tx1,
              tx2));
    }

    @Test
    @DisplayName("Multiple transfers to same receiver (with rewards)")
    void multipleTransfersToSameReceiverWithRewards() {
      final Transaction tx1 = transferWithFees(SENDER_1, 0, RECIPIENT_1, ONE_ETH);
      final Transaction tx2 = transferWithFees(SENDER_2, 0, RECIPIENT_1, 2 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x1b403f2bcf5f5b47b80d06a60edb70a924a1fa2d1f1f31a82095393a8978fd8f",
              LOW_BASE_FEE,
              tx1,
              tx2));
    }

    @Test
    @DisplayName(
        "Sender receives funds in Tx1 then spends more than initial balance in Tx2 (zero reward)")
    void receiveThenSpendMoreThanInitialBalance() {
      // SENDER_2 has 1000 ETH. Tx1 sends 500 ETH to SENDER_2, giving it 1500 ETH total.
      // Tx2 tries to spend 1200 ETH from SENDER_2, which requires the 500 ETH from Tx1.
      // Without proper conflict detection, Tx2 runs in parallel with only 1000 ETH available
      // and reverts (insufficient funds), producing a different state root.
      final Wei sendAmount = Wei.of(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)));
      final Wei spendAmount = Wei.of(BigInteger.valueOf(1200).multiply(BigInteger.TEN.pow(18)));

      final Transaction tx1 = buildLargeTransfer(SENDER_1, 0, SENDER_2_ADDRESS, sendAmount);
      final Transaction tx2 = buildLargeTransfer(SENDER_2, 0, RECIPIENT_1, spendAmount);

      assertStateRootsMatch(
          executeAndCompare(
              "0x98cbf0f3b713506c87e43064df0c9e365525ea4473c3f8d4900fbbaba0018e08",
              ZERO_BASE_FEE,
              tx1,
              tx2));
    }
  }

  @Nested
  @DisplayName("Coinbase/Mining Beneficiary Conflicts")
  class CoinbaseConflicts {

    @Test
    @DisplayName("Transfer to coinbase creates conflict (zero reward)")
    void transferToCoinbaseZeroReward() {
      // This transaction explicitly transfers to the coinbase address,
      // which triggers: addressesTouchedByTransaction.contains(miningBeneficiary) == true
      final Transaction tx1 = transfer(SENDER_1, 0, COINBASE.toHexString(), ONE_ETH);
      final Transaction tx2 = transfer(SENDER_2, 0, RECIPIENT_1, 2 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x3ef6d5b9308ef71d7538dfd5a70a427e9779f4a4ec22b82bc6e88426849420de",
              ZERO_BASE_FEE,
              tx1,
              tx2));
    }

    @Test
    @DisplayName("Transfer to coinbase creates conflict (with rewards)")
    void transferToCoinbaseWithRewards() {
      // Transfer to coinbase + rewards = double modification of coinbase balance
      final Transaction tx1 = transferWithFees(SENDER_1, 0, COINBASE.toHexString(), ONE_ETH);
      final Transaction tx2 = transferWithFees(SENDER_2, 0, RECIPIENT_1, 2 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x9c1c73f7aa392bd391638b3c0d4fc01943d97b1e723e77525c83114bc3c92a84",
              LOW_BASE_FEE,
              tx1,
              tx2));
    }

    @Test
    @DisplayName("Multiple transfers to coinbase creates conflict")
    void multipleTransfersToCoinbase() {
      // Both transactions transfer to coinbase, creating conflicts
      final Transaction tx1 = transfer(SENDER_1, 0, COINBASE.toHexString(), ONE_ETH);
      final Transaction tx2 = transfer(SENDER_2, 0, COINBASE.toHexString(), 2 * ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x160de62bd888667b1bd767803d34e78afe148a1aa9fa658485859525e3786fb6",
              ZERO_BASE_FEE,
              tx1,
              tx2));
    }

    @Test
    @DisplayName("Transfer to coinbase followed by independent transfer")
    void transferToCoinbaseThenIndependent() {
      // First tx touches coinbase, second is independent
      final Transaction tx1 = transfer(SENDER_1, 0, COINBASE.toHexString(), HALF_ETH);
      final Transaction tx2 = transfer(SENDER_2, 0, RECIPIENT_2, ONE_ETH);
      final Transaction tx3 = transfer(SENDER_1, 1, RECIPIENT_3, QUARTER_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x014c7d3647f4d1cf6d8eca8084e79e1a7200213ff022e08b1f76bcc99408e3d1",
              ZERO_BASE_FEE,
              tx1,
              tx2,
              tx3));
    }
  }

  @Nested
  @DisplayName("Storage Slot Conflicts")
  class StorageSlotConflicts {

    @Test
    @DisplayName("Write-then-read on same slot")
    void writeThenReadSameSlot() {
      final Address contract = Address.fromHexStringStrict(STORAGE_CONTRACT);

      final Transaction txWrite = storageUpdate(SENDER_1, 0, contract, "setSlot1", 999);
      final Transaction txRead = storageRead(SENDER_2, 0, contract, "getSlot1");

      assertStateRootsMatch(
          executeAndCompare(
              "0x003cfeeb8f2cd860f12f1622f9c4c91c01fdcdc3313d429e3996635c1ced72f5",
              ZERO_BASE_FEE,
              txWrite,
              txRead));
    }

    @Test
    @DisplayName("Write-then-write on same slot")
    void writeThenWriteSameSlot() {
      final Address contract = Address.fromHexStringStrict(STORAGE_CONTRACT);

      final Transaction txWrite1 = storageUpdate(SENDER_1, 0, contract, "setSlot1", 111);
      final Transaction txWrite2 = storageUpdate(SENDER_2, 0, contract, "setSlot1", 222);

      final StateRootComparisonResult result =
          executeAndCompare(
              "0xfdab06582a77b6f72ae344eb2cbfc1c001ebf7ad399ec36aaa4c996c46c8a290",
              ZERO_BASE_FEE,
              txWrite1,
              txWrite2);

      assertStateRootsMatch(result);

      // Verify final storage value
      final MutableWorldState worldState = worldStateArchive.getWorldState();
      final UInt256 finalValue =
          ((BonsaiAccount) worldState.get(contract)).getStorageValue(UInt256.ZERO);
      assertThat(finalValue).isEqualTo(UInt256.valueOf(222));
    }

    @Test
    @DisplayName("Mixed slot operations")
    void mixedSlotOperations() {
      final Address contract = Address.fromHexStringStrict(STORAGE_CONTRACT);

      final Transaction tx1 = storageUpdate(SENDER_1, 0, contract, "setSlot1", 100);
      final Transaction tx2 = storageRead(SENDER_2, 0, contract, "getSlot1");
      final Transaction tx3 = storageUpdate(SENDER_1, 1, contract, "setSlot2", 200);
      final Transaction tx4 = storageUpdate(SENDER_1, 2, contract, "setSlot1", 150);

      assertStateRootsMatch(
          executeAndCompare(
              "0x4cc4e30f4aa54525848cf11c9222e51e618241a6bb7e1b75b7829f4fba8512fa",
              ZERO_BASE_FEE,
              tx1,
              tx2,
              tx3,
              tx4));
    }

    @Test
    @DisplayName("Read-then-increment same slot: true non-commutative conflict")
    void incrementSameSlotTwice() {
      // slot1 starts at 10 (0xa) in genesis (INCREMENT_CONTRACT in PARALLEL_GENESIS_RESOURCE).
      // Tx1 calls incrementSlot1(): reads slot1=10, writes slot1=11.
      // Tx2 calls incrementSlot1(): reads slot1=10, writes slot1=11.
      // Sequential result: slot1 = 12 (incremented twice).
      // Without conflict detection: both read 10 in parallel, both write 11 → slot1 = 11.
      // The final storage value differs → different state root.
      // This test WILL fail if the for-loop in TransactionCollisionDetector is removed.
      final Address contract = Address.fromHexStringStrict(INCREMENT_CONTRACT);

      final Transaction txInc1 = storageIncrement(SENDER_1, 0, contract);
      final Transaction txInc2 = storageIncrement(SENDER_2, 0, contract);

      final StateRootComparisonResult result =
          executeAndCompare(
              "0x53f128b8f712ecf242d026c64e05aa403ed42adff0889100258311883e3b10a9",
              ZERO_BASE_FEE,
              txInc1,
              txInc2);

      // Verify transactions actually succeeded (not reverted)
      final var receipts = result.sequentialResult().getReceipts();
      assertThat(receipts).hasSize(2);
      assertThat(receipts.get(0).getStatus()).as("txInc1 must succeed (status=1)").isEqualTo(1);
      assertThat(receipts.get(1).getStatus()).as("txInc2 must succeed (status=1)").isEqualTo(1);

      assertStateRootsMatch(result);

      // Verify via the actual seq world state that slot1 = 12 (= 10 + 1 + 1).
      // If the for-loop in TransactionCollisionDetector is removed, both txs read slot1=10 in
      // parallel and both write 11 — but they would produce the same (wrong) state root since
      // the merge applies the same value twice. The difference surfaces here.
      final UInt256 seqValue =
          ((BonsaiAccount) result.seqWorldState().get(contract)).getStorageValue(UInt256.ZERO);
      final UInt256 parValue =
          ((BonsaiAccount) result.parWorldState().get(contract)).getStorageValue(UInt256.ZERO);
      assertThat(seqValue).as("sequential slot1 must be 12").isEqualTo(UInt256.valueOf(12));
      assertThat(parValue).as("parallel slot1 must match sequential").isEqualTo(seqValue);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Single transaction (zero reward)")
    void singleTransactionZeroReward() {
      final Transaction tx = transfer(SENDER_1, 0, RECIPIENT_1, ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0xffab27a45a5175400d2b18ec174f0de1b72ce6136be65cf59786c7b139e017fd",
              ZERO_BASE_FEE,
              tx));
    }

    @Test
    @DisplayName("Single transaction (with rewards)")
    void singleTransactionWithRewards() {
      final Transaction tx = transferWithFees(SENDER_1, 0, RECIPIENT_1, ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x7313019449a77883282b7c8aaafd4a7fb28f515952e0e94bb791e1b996ae7a28",
              LOW_BASE_FEE,
              tx));
    }

    @Test
    @DisplayName("Zero value transfer")
    void zeroValueTransfer() {
      final Transaction tx1 = transfer(SENDER_1, 0, RECIPIENT_1, 0L);
      final Transaction tx2 = transfer(SENDER_2, 0, RECIPIENT_2, ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0x3ae7f14bd471df973e8d2aa59f52a8335812dfc345bc89d90745d3032068fa56",
              ZERO_BASE_FEE,
              tx1,
              tx2));
    }
  }

  @Nested
  @DisplayName("Reverting Transactions")
  class RevertingTransactions {

    @Test
    @DisplayName("Contract call that reverts (zero reward)")
    void contractCallThatReverts() {
      final Address revertContract = Address.fromHexStringStrict(REVERT_CONTRACT);

      final Transaction txRevert = revert(SENDER_1, 0, revertContract);
      final Transaction txValid = transfer(SENDER_2, 0, RECIPIENT_1, ONE_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0xed6e4e8faff50c3f41e8e4aea249694990a43fdcc33cab8470b63c0a17d291fb",
              ZERO_BASE_FEE,
              txRevert,
              txValid));
    }

    @Test
    @DisplayName("Reverting transaction followed by storage update")
    void revertThenStorageUpdate() {
      final Address revertContract = Address.fromHexStringStrict(REVERT_CONTRACT);
      final Address storageContract = Address.fromHexStringStrict(STORAGE_CONTRACT);

      final Transaction txRevert = revert(SENDER_1, 0, revertContract);
      final Transaction txStorage = storageUpdate(SENDER_2, 0, storageContract, "setSlot1", 42);

      assertStateRootsMatch(
          executeAndCompare(
              "0xb2690eaf5c43d9aa50a52693216ff315d9bf37df7a7f5ba142cf8d6810e34250",
              ZERO_BASE_FEE,
              txRevert,
              txStorage));
    }

    @Test
    @DisplayName("Mixed successful and reverting transactions")
    void mixedSuccessAndRevert() {
      final Address revertContract = Address.fromHexStringStrict(REVERT_CONTRACT);

      final Transaction txSuccess1 = transfer(SENDER_1, 0, RECIPIENT_1, ONE_ETH);
      final Transaction txRevert = revert(SENDER_2, 0, revertContract);
      final Transaction txSuccess2 = transfer(SENDER_1, 1, RECIPIENT_2, HALF_ETH);

      assertStateRootsMatch(
          executeAndCompare(
              "0xd838508cc34a0b70fdd5ecfab5ec1cafcc023a3759c90d8821cd542bdbec8f97",
              ZERO_BASE_FEE,
              txSuccess1,
              txRevert,
              txSuccess2));
    }
  }

  // ==================== Transaction Builders ====================

  /** Creates a simple transfer with zero fees. */
  protected Transaction transfer(
      final KeyPair sender, final long nonce, final String to, final long valueWei) {
    return buildTransaction(sender, nonce, to, Wei.of(valueWei), Wei.ZERO, Wei.ZERO, Bytes.EMPTY);
  }

  /** Creates a simple transfer with a Wei value (for large amounts that overflow long). */
  protected Transaction buildLargeTransfer(
      final KeyPair sender, final long nonce, final String to, final Wei value) {
    return buildTransaction(sender, nonce, to, value, Wei.ZERO, Wei.ZERO, Bytes.EMPTY);
  }

  /** Creates a transfer with priority fees (for coinbase rewards). */
  protected Transaction transferWithFees(
      final KeyPair sender, final long nonce, final String to, final long valueWei) {
    return buildTransaction(
        sender, nonce, to, Wei.of(valueWei), DEFAULT_PRIORITY_FEE, DEFAULT_MAX_FEE, Bytes.EMPTY);
  }

  /** Creates a contract storage update with zero fees. */
  protected Transaction storageUpdate(
      final KeyPair sender,
      final long nonce,
      final Address contract,
      final String method,
      final int value) {
    return buildContractTransaction(
        sender, nonce, contract, encodeCall(method, Optional.of(value)), Wei.ZERO, Wei.ZERO);
  }

  /** Calls incrementSlot1() on the contract — reads then writes slot1 = slot1 + 1. */
  protected Transaction storageIncrement(
      final KeyPair sender, final long nonce, final Address contract) {
    return buildContractTransaction(
        sender,
        nonce,
        contract,
        encodeCall("incrementSlot1", Optional.empty()),
        Wei.ZERO,
        Wei.ZERO);
  }

  /** Creates a contract storage read with zero fees. */
  protected Transaction storageRead(
      final KeyPair sender, final long nonce, final Address contract, final String method) {
    return buildContractTransaction(
        sender, nonce, contract, encodeCall(method, Optional.empty()), Wei.ZERO, Wei.ZERO);
  }

  /** Creates a contract storage update with fees. */
  protected Transaction storageUpdateWithFees(
      final KeyPair sender,
      final long nonce,
      final Address contract,
      final String method,
      final int value) {
    return buildContractTransaction(
        sender,
        nonce,
        contract,
        encodeCall(method, Optional.of(value)),
        DEFAULT_PRIORITY_FEE,
        DEFAULT_MAX_FEE);
  }

  /** Creates a transaction that will revert. */
  protected Transaction revert(
      final KeyPair sender, final long nonce, final Address revertContract) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(Wei.ZERO)
        .maxFeePerGas(Wei.ZERO)
        .gasLimit(100_000L)
        .to(revertContract)
        .value(Wei.ZERO)
        .payload(Bytes.EMPTY)
        .chainId(CHAIN_ID)
        .signAndBuild(sender);
  }

  private Transaction buildTransaction(
      final KeyPair sender,
      final long nonce,
      final String to,
      final Wei value,
      final Wei priorityFee,
      final Wei maxFee,
      final Bytes payload) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(priorityFee)
        .maxFeePerGas(maxFee)
        .gasLimit(DEFAULT_GAS_LIMIT)
        .to(Address.fromHexStringStrict(to))
        .value(value)
        .payload(payload)
        .chainId(CHAIN_ID)
        .signAndBuild(sender);
  }

  private Transaction buildContractTransaction(
      final KeyPair sender,
      final long nonce,
      final Address contract,
      final Bytes payload,
      final Wei priorityFee,
      final Wei maxFee) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .nonce(nonce)
        .maxPriorityFeePerGas(priorityFee)
        .maxFeePerGas(maxFee)
        .gasLimit(CONTRACT_GAS_LIMIT)
        .to(contract)
        .value(Wei.ZERO)
        .payload(payload)
        .chainId(CHAIN_ID)
        .signAndBuild(sender);
  }

  private Bytes encodeCall(final String method, final Optional<Integer> value) {
    final List<Type> params =
        value.isPresent() ? List.of(new Uint256(value.get())) : List.of();
    return Bytes.fromHexString(FunctionEncoder.encode(new Function(method, params, List.of())));
  }

  // ==================== Execution & Comparison ====================

  protected record StateRootComparisonResult(
      Hash sequentialStateRoot,
      Hash parallelStateRoot,
      BlockProcessingResult sequentialResult,
      BlockProcessingResult parallelResult,
      MutableWorldState seqWorldState,
      MutableWorldState parWorldState) {}

  protected StateRootComparisonResult executeAndCompare(
      final String expectedRoot, final Wei baseFee, final Transaction... txs) {

    final ExecutionContextTestFixture parContext =
        ExecutionContextTestFixture.builder(GenesisConfig.fromResource(PARALLEL_GENESIS_RESOURCE))
            .dataStorageFormat(DataStorageFormat.BONSAI)
            .build();

    final MutableWorldState seqWorldState = worldStateArchive.getWorldState();
    final MutableWorldState parWorldState = parContext.getStateArchive().getWorldState();

    final Block block = createBlock(expectedRoot, baseFee, txs);
    final ProtocolSpec spec = protocolSchedule.getByBlockHeader(block.getHeader());
    final MainnetTransactionProcessor txProcessor = spec.getTransactionProcessor();

    // Sequential processing
    final BlockProcessor seqProcessor =
        new MainnetBlockProcessor(
            txProcessor,
            spec.getTransactionReceiptFactory(),
            Wei.ZERO,
            BlockHeader::getCoinbase,
            true,
            protocolSchedule,
            SEQUENTIAL_CONFIG);

    // Parallel processing
    final BlockProcessor parProcessor = createParallelBlockProcessor(txProcessor, spec);

    final BlockProcessingResult parResult =
        parProcessor.processBlock(
            parContext.getProtocolContext(),
            parContext.getBlockchain(),
            parWorldState,
            block,
            createParallelPreprocessing(txProcessor));

    final BlockProcessingResult seqResult =
        seqProcessor.processBlock(protocolContext, blockchain, seqWorldState, block);

    assertTrue(
        seqResult.isSuccessful(),
        "Sequential processing failed — "
            + seqResult.errorMessage.orElse("(no message)")
            + " — update the hardcoded expectedRoot in the test to fix this");
    assertTrue(parResult.isSuccessful(), "Parallel processing failed");
    assertThat(seqWorldState.rootHash()).isEqualTo(parWorldState.rootHash());

    return new StateRootComparisonResult(
        seqWorldState.rootHash(), parWorldState.rootHash(), seqResult, parResult,
        seqWorldState, parWorldState);
  }

  protected void assertStateRootsMatch(final StateRootComparisonResult result) {
    assertThat(result.parallelStateRoot())
        .as("Parallel state root should match sequential for " + getVariantName())
        .isEqualTo(result.sequentialStateRoot());
    assertBalHashesMatch(result);
  }

  // ==================== Hook Methods ====================

  /** Override in BAL tests to verify BAL hash consistency. */
  protected void assertBalHashesMatch(final StateRootComparisonResult result) {}

  /** Returns the BAL configuration for this variant. */
  protected BalConfiguration getBalConfiguration() {
    return BalConfiguration.DEFAULT;
  }

  // ==================== Helper Methods ====================

  protected BlockProcessor createParallelBlockProcessor(
      final MainnetTransactionProcessor txProcessor, final ProtocolSpec spec) {
    final MetricsSystem metrics = new NoOpMetricsSystem();
    return new MainnetBlockProcessor(
        txProcessor,
        spec.getTransactionReceiptFactory(),
        Wei.ZERO,
        BlockHeader::getCoinbase,
        true,
        protocolSchedule,
        getBalConfiguration(),
        metrics);
  }

  protected Optional<BlockAccessList> getBlockAccessList(final BlockProcessingResult result) {
    return result.getYield().flatMap(outputs -> outputs.getBlockAccessList());
  }

  protected Hash computeBalHash(final BlockAccessList bal) {
    return BodyValidation.balHash(bal);
  }

  protected Block createBlock(
      final String stateRoot, final Wei baseFee, final Transaction... txs) {
    final BlockHeader parent = blockchain.getChainHeadHeader();
    final BlockHeader header =
        new BlockHeaderTestFixture()
            .number(parent.getNumber() + 1L)
            .parentHash(parent.getHash())
            .stateRoot(Hash.fromHexString(stateRoot))
            .gasLimit(30_000_000L)
            .baseFeePerGas(baseFee)
            .coinbase(COINBASE)
            .buildHeader();
    return new Block(
        header, new BlockBody(Arrays.asList(txs), Collections.emptyList(), Optional.empty()));
  }

  private static KeyPair createKeyPair(final String privateKeyHex) {
    return SignatureAlgorithmFactory.getInstance()
        .createKeyPair(
            SECPPrivateKey.create(
                Bytes32.fromHexString(privateKeyHex), SignatureAlgorithm.ALGORITHM));
  }
}
