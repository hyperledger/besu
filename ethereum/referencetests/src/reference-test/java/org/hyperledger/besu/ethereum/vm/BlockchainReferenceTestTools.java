/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.hyperledger.besu.consensus.merge.blockcreation.ReferenceTestMergeBlockCreator;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskRequestSender;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCache;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolFactory;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.referencetests.BlockchainReferenceTestCaseSpec;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.evm.internal.EvmConfiguration.WorldUpdaterMode;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

public class BlockchainReferenceTestTools {

  private static final List<String> NETWORKS_TO_RUN;

  static {
    final String networks =
        System.getProperty(
            "test.ethereum.blockchain.eips",
            "FrontierToHomesteadAt5,HomesteadToEIP150At5,HomesteadToDaoAt5,EIP158ToByzantiumAt5,CancunToPragueAtTime15k,"
                + "Frontier,Homestead,EIP150,EIP158,Byzantium,Constantinople,ConstantinopleFix,Istanbul,Berlin,"
                + "London,Merge,Paris,Shanghai,Cancun,Prague,Osaka,Amsterdam,Bogota,Polis,Bangkok");
    NETWORKS_TO_RUN = Arrays.asList(networks.split(","));
  }

  private static final JsonTestParameters<?, ?> params =
      JsonTestParameters.create(BlockchainReferenceTestCaseSpec.class)
          .generator(
              (testName, fullPath, spec, collector) -> {
                final String eip = spec.getNetwork();
                collector.add(
                    testName + "[" + eip + "]", fullPath, spec, NETWORKS_TO_RUN.contains(eip));
              });

  static {
    if (NETWORKS_TO_RUN.isEmpty()) {
      params.ignoreAll();
    }

    // Consumes a huge amount of memory
    params.ignore("static_Call1MB1024Calldepth");
    params.ignore("ShanghaiLove_");

    // Absurd amount of gas, doesn't run in parallel
    params.ignore("randomStatetest94_\\w+");

    // Don't do time-consuming tests
    params.ignore("CALLBlake2f_MaxRounds");
    params.ignore("loopMul_");

    // Inconclusive fork choice rule, since in merge CL should be choosing forks and setting the
    // chain head.
    // Perfectly valid test pre-merge.
    params.ignore(
        "UncleFromSideChain_(Merge|Paris|Shanghai|Cancun|Prague|Osaka|Amsterdam|Bogota|Polis|Bangkok)");

    // EOF tests don't have Prague stuff like deposits right now
    params.ignore("/stEOF/");

    // These are for the older reference tests but EIP-2537 is covered by eip2537_bls_12_381_precompiles in the execution-spec-tests
    params.ignore("/stEIP2537/");
  }

  private BlockchainReferenceTestTools() {
    // utility class
  }

  public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath) {
    return params.generate(filePath);
  }

  @SuppressWarnings("java:S5960") // this is actually test code
  public static void executeTest(final String name, final BlockchainReferenceTestCaseSpec spec) {
    final BlockHeader genesisBlockHeader = spec.getGenesisBlockHeader();
    final MutableWorldState worldState =
        spec.getWorldStateArchive()
            .getWorldState(WorldStateQueryParams.withStateRootAndBlockHashAndUpdateNodeHead(genesisBlockHeader.getStateRoot(), genesisBlockHeader.getHash()))
            .orElseThrow();

    final ProtocolSchedule schedule =
        ReferenceTestProtocolSchedules.getInstance().getByName(spec.getNetwork());

    final MutableBlockchain blockchain = spec.getBlockchain();
    final ProtocolContext context = spec.getProtocolContext();

    try (BlockCreationFixture blockCreation =
        BlockCreationFixture.create(schedule, context, blockchain)) {
      for (final BlockchainReferenceTestCaseSpec.CandidateBlock candidateBlock :
          spec.getCandidateBlocks()) {
        if (!candidateBlock.isExecutable()) {
          return;
        }

        try {
          final Block blockFromReference = candidateBlock.getBlock();

          final ProtocolSpec protocolSpec = schedule.getByBlockHeader(blockFromReference.getHeader());

          verifyJournaledEVMAccountCompatability(worldState, protocolSpec);

          final boolean supportsBlockBuilding =
              ReferenceTestProtocolSchedules.supportsBlockBuilding(spec.getNetwork());
          final boolean shouldBuildBlock = supportsBlockBuilding && candidateBlock.isValid() && !name.contains("eip7934");
          final Block block =
              shouldBuildBlock
                  ? buildBlock(
                      schedule,
                      context,
                      blockchain,
                      blockCreation.transactionPool(),
                      blockCreation.ethScheduler(),
                      blockFromReference)
                  : blockFromReference;

          assertThat(block).isEqualTo(blockFromReference);

          final HeaderValidationMode validationMode =
              "NoProof".equalsIgnoreCase(spec.getSealEngine())
                  ? HeaderValidationMode.LIGHT
                  : HeaderValidationMode.FULL;
          final BlockImporter blockImporter = protocolSpec.getBlockImporter();
          final BlockImportResult importResult =
              blockImporter.importBlock(context, block, validationMode, validationMode);

          assertThat(importResult.isImported()).isEqualTo(candidateBlock.isValid());
        } catch (final RLPException e) {
          assertThat(candidateBlock.isValid()).isFalse();
        }
      }
    }

    Assertions.assertThat(blockchain.getChainHeadHash()).isEqualTo(spec.getLastBlockHash());
  }

  private static Block buildBlock(
      final ProtocolSchedule schedule,
      final ProtocolContext context,
      final MutableBlockchain blockchain,
      final TransactionPool transactionPool,
      final EthScheduler ethScheduler,
      final Block blockFromReference) {

    final MiningConfiguration miningConfiguration = MiningConfiguration.newDefault();
    miningConfiguration.setMiningEnabled(true);
    miningConfiguration.setCoinbase(blockFromReference.getHeader().getCoinbase());
    miningConfiguration.setExtraData(blockFromReference.getHeader().getExtraData());
    miningConfiguration.setMinTransactionGasPrice(Wei.ZERO);
    miningConfiguration.setMinPriorityFeePerGas(Wei.ZERO);
    miningConfiguration.setTargetGasLimit(blockFromReference.getHeader().getGasLimit());

    final List<Transaction> transactions = blockFromReference.getBody().getTransactions();
    final Optional<List<Withdrawal>> withdrawals = blockFromReference.getBody().getWithdrawals();
    final List<BlockHeader> ommers = blockFromReference.getBody().getOmmers();
    final BlockHeader parentHeader =
        blockchain
            .getBlockHeader(blockFromReference.getHeader().getParentHash())
            .orElseThrow();
    return ReferenceTestMergeBlockCreator.createBlock(
        miningConfiguration,
        parent -> blockFromReference.getHeader().getExtraData(),
        transactionPool,
        context,
        schedule,
        parentHeader,
        ethScheduler,
        Optional.of(transactions),
        Optional.of(ommers),
        blockFromReference.getHeader().getMixHashOrPrevRandao(),
        blockFromReference.getHeader().getTimestamp(),
        withdrawals,
        blockFromReference.getHeader().getParentBeaconBlockRoot());
  }

  static void verifyJournaledEVMAccountCompatability(
          final MutableWorldState worldState, final ProtocolSpec protocolSpec) {
    EVM evm = protocolSpec.getEvm();
    if (evm.getEvmConfiguration().worldUpdaterMode() == WorldUpdaterMode.JOURNALED) {
      assumeFalse(
              worldState
                      .streamAccounts(Bytes32.ZERO, Integer.MAX_VALUE).anyMatch(AccountState::isEmpty),
              "Journaled account configured and empty account detected");
      assumeFalse(EvmSpecVersion.SPURIOUS_DRAGON.compareTo(evm.getEvmVersion()) > 0,
              "Journaled account configured and fork prior to the merge specified");
    }
  }

  private static final class BlockCreationFixture implements AutoCloseable {
    private final EthScheduler ethScheduler;
    private final TransactionPool transactionPool;
    private final MutableBlockchain blockchain;

    private BlockCreationFixture(
        final EthScheduler ethScheduler,
        final TransactionPool transactionPool,
        final MutableBlockchain blockchain) {
      this.ethScheduler = ethScheduler;
      this.transactionPool = transactionPool;
      this.blockchain = blockchain;
    }

    static BlockCreationFixture create(
        final ProtocolSchedule schedule,
        final ProtocolContext context,
        final MutableBlockchain blockchain) {
      final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
      final EthScheduler ethScheduler = new EthScheduler(1, 1, 1, metricsSystem);
      final EthPeers ethPeers =
          new EthPeers(
              () -> schedule.getByBlockHeader(blockchain.getChainHeadHeader()),
              Clock.systemUTC(),
              metricsSystem,
              EthProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE,
              Collections.emptyList(),
              Bytes.random(EthPeers.NODE_ID_LENGTH),
              1,
              1,
              false,
              SyncMode.FULL,
              new ForkIdManager(blockchain, Collections.emptyList(), Collections.emptyList()));
      final EthContext ethContext =
          new EthContext(
              ethPeers,
              new EthMessages(),
              ethScheduler,
              new PeerTaskExecutor(ethPeers, new PeerTaskRequestSender(), metricsSystem));
      final SyncState syncState = new SyncState(blockchain, ethPeers);
      final TransactionPool transactionPool =
          TransactionPoolFactory.createTransactionPool(
              schedule,
              context,
              ethContext,
              Clock.systemUTC(),
              metricsSystem,
              syncState,
              TransactionPoolConfiguration.DEFAULT,
              new BlobCache(),
              MiningConfiguration.newDefault());

      return new BlockCreationFixture(ethScheduler, transactionPool, blockchain);
    }

    TransactionPool transactionPool() {
      return transactionPool;
    }

    EthScheduler ethScheduler() {
      return ethScheduler;
    }

    @Override
    public void close() {
      ethScheduler.stop();
      blockchain.removeAllBlockAddedObservers();
    }
  }
}
