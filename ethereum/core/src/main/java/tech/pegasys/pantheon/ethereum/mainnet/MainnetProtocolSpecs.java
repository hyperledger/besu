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
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonArray;

/** Provides the various @{link ProtocolSpec}s on mainnet hard forks. */
public abstract class MainnetProtocolSpecs {

  public static final int FRONTIER_CONTRACT_SIZE_LIMIT = Integer.MAX_VALUE;

  public static final int SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT = 24576;

  private static final Address RIPEMD160_PRECOMPILE =
      Address.fromHexString("0x0000000000000000000000000000000000000003");

  // A consensus bug at Ethereum mainnet transaction 0xcf416c53
  // deleted an empty account even when the message execution scope
  // failed, but the transaction itself succeeded.
  private static final ImmutableSet<Address> SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES =
      ImmutableSet.of(RIPEMD160_PRECOMPILE);

  private static final Wei FRONTIER_BLOCK_REWARD = Wei.fromEth(5);

  private static final Wei BYZANTIUM_BLOCK_REWARD = Wei.fromEth(3);

  private static final Wei CONSTANTINOPLE_BLOCK_REWARD = Wei.fromEth(2);

  private MainnetProtocolSpecs() {}

  public static ProtocolSpecBuilder<Void> frontierDefinition() {
    return new ProtocolSpecBuilder<Void>()
        .gasCalculator(FrontierGasCalculator::new)
        .evmBuilder(MainnetEvmRegistries::frontier)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::frontier)
        .messageCallProcessorBuilder(MainnetMessageCallProcessor::new)
        .contractCreationProcessorBuilder(
            (gasCalculator, evm) ->
                new MainnetContractCreationProcessor(
                    gasCalculator, evm, false, FRONTIER_CONTRACT_SIZE_LIMIT, 0))
        .transactionValidatorBuilder(
            gasCalculator -> new MainnetTransactionValidator(gasCalculator, false))
        .transactionProcessorBuilder(
            (gasCalculator,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor) ->
                new MainnetTransactionProcessor(
                    gasCalculator,
                    transactionValidator,
                    contractCreationProcessor,
                    messageCallProcessor,
                    false))
        .difficultyCalculator(MainnetDifficultyCalculators.FRONTIER)
        .blockHeaderValidatorBuilder(MainnetBlockHeaderValidator::create)
        .blockBodyValidatorBuilder(MainnetBlockBodyValidator::new)
        .transactionReceiptFactory(MainnetProtocolSpecs::frontierTransactionReceiptFactory)
        .blockReward(FRONTIER_BLOCK_REWARD)
        .blockProcessorBuilder(MainnetBlockProcessor::new)
        .blockImporterBuilder(MainnetBlockImporter::new)
        .transactionReceiptType(TransactionReceiptType.ROOT)
        .blockHashFunction(MainnetBlockHashFunction::createHash)
        .miningBeneficiaryCalculator(BlockHeader::getCoinbase)
        .name("Frontier");
  }

  /**
   * Returns the Frontier milestone protocol spec.
   *
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return the Frontier milestone protocol spec
   */
  public static ProtocolSpec<Void> frontier(final ProtocolSchedule<Void> protocolSchedule) {
    return frontierDefinition().build(protocolSchedule);
  }

  /**
   * Returns the Homestead milestone protocol spec.
   *
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return the Homestead milestone protocol spec
   */
  public static ProtocolSpec<Void> homestead(final ProtocolSchedule<Void> protocolSchedule) {
    return homesteadDefinition().build(protocolSchedule);
  }

  public static ProtocolSpecBuilder<Void> homesteadDefinition() {
    return frontierDefinition()
        .gasCalculator(HomesteadGasCalculator::new)
        .evmBuilder(MainnetEvmRegistries::homestead)
        .contractCreationProcessorBuilder(
            (gasCalculator, evm) ->
                new MainnetContractCreationProcessor(
                    gasCalculator, evm, true, FRONTIER_CONTRACT_SIZE_LIMIT, 0))
        .transactionValidatorBuilder(
            gasCalculator -> new MainnetTransactionValidator(gasCalculator, true))
        .difficultyCalculator(MainnetDifficultyCalculators.HOMESTEAD)
        .name("Homestead");
  }

  /**
   * Returns the initial DAO block milestone protocol spec.
   *
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return the initial DAO block milestone protocol spec
   */
  public static ProtocolSpec<Void> daoRecoveryInit(final ProtocolSchedule<Void> protocolSchedule) {
    return daoRecoveryInitDefinition().build(protocolSchedule);
  }

  private static ProtocolSpecBuilder<Void> daoRecoveryInitDefinition() {
    return homesteadDefinition()
        .blockHeaderValidatorBuilder(MainnetBlockHeaderValidator::createDaoValidator)
        .blockProcessorBuilder(
            (transactionProcessor,
                transactionReceiptFactory,
                blockReward,
                miningBeneficiaryCalculator) ->
                new DaoBlockProcessor(
                    new MainnetBlockProcessor(
                        transactionProcessor,
                        transactionReceiptFactory,
                        blockReward,
                        miningBeneficiaryCalculator)))
        .name("DaoRecoveryInit");
  }

  /**
   * Returns the DAO block transition segment milestone protocol spec.
   *
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return the DAO block transition segment milestone protocol spec
   */
  public static ProtocolSpec<Void> daoRecoveryTransition(
      final ProtocolSchedule<Void> protocolSchedule) {
    return daoRecoveryInitDefinition()
        .blockProcessorBuilder(MainnetBlockProcessor::new)
        .name("DaoRecoveryTransition")
        .build(protocolSchedule);
  }

  /**
   * Returns the Tangerine Whistle milestone protocol spec.
   *
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return the Tangerine Whistle milestone protocol spec
   */
  public static ProtocolSpec<Void> tangerineWhistle(final ProtocolSchedule<Void> protocolSchedule) {
    return tangerineWhistleDefinition().build(protocolSchedule);
  }

  public static ProtocolSpecBuilder<Void> tangerineWhistleDefinition() {
    return homesteadDefinition()
        .gasCalculator(TangerineWhistleGasCalculator::new)
        .name("TangerineWhistle");
  }

  /**
   * Returns the Spurious Dragon milestone protocol spec.
   *
   * @param chainId ID of the blockchain
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return the Spurious Dragon milestone protocol spec
   */
  public static ProtocolSpec<Void> spuriousDragon(
      final int chainId, final ProtocolSchedule<Void> protocolSchedule) {
    return spuriousDragonDefinition(chainId).build(protocolSchedule);
  }

  public static ProtocolSpecBuilder<Void> spuriousDragonDefinition(final int chainId) {
    return tangerineWhistleDefinition()
        .gasCalculator(SpuriousDragonGasCalculator::new)
        .messageCallProcessorBuilder(
            (evm, precompileContractRegistry) ->
                new MainnetMessageCallProcessor(
                    evm,
                    precompileContractRegistry,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .contractCreationProcessorBuilder(
            (gasCalculator, evm) ->
                new MainnetContractCreationProcessor(
                    gasCalculator,
                    evm,
                    true,
                    SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT,
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .transactionValidatorBuilder(
            gasCalculator -> new MainnetTransactionValidator(gasCalculator, true, chainId))
        .transactionProcessorBuilder(
            (gasCalculator,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor) ->
                new MainnetTransactionProcessor(
                    gasCalculator,
                    transactionValidator,
                    contractCreationProcessor,
                    messageCallProcessor,
                    true))
        .name("SpuriousDragon");
  }

  /**
   * Returns the Byzantium milestone protocol spec.
   *
   * @param chainId ID of the blockchain
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return the Byzantium milestone protocol spec
   */
  public static ProtocolSpec<Void> byzantium(
      final int chainId, final ProtocolSchedule<Void> protocolSchedule) {
    return byzantiumDefinition(chainId).build(protocolSchedule);
  }

  public static ProtocolSpecBuilder<Void> byzantiumDefinition(final int chainId) {
    return spuriousDragonDefinition(chainId)
        .evmBuilder(MainnetEvmRegistries::byzantium)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::byzantium)
        .difficultyCalculator(MainnetDifficultyCalculators.BYZANTIUM)
        .transactionReceiptFactory(MainnetProtocolSpecs::byzantiumTransactionReceiptFactory)
        .blockReward(BYZANTIUM_BLOCK_REWARD)
        .transactionReceiptType(TransactionReceiptType.STATUS)
        .name("Byzantium");
  }

  /**
   * Returns the Constantinople milestone protocol spec.
   *
   * @param chainId ID of the blockchain
   * @param protocolSchedule the {@link ProtocolSchedule} this spec will be part of
   * @return the Constantinople milestone protocol spec
   */
  public static ProtocolSpec<Void> constantinople(
      final int chainId, final ProtocolSchedule<Void> protocolSchedule) {
    return byzantiumDefinition(chainId)
        .difficultyCalculator(MainnetDifficultyCalculators.CONSTANTINOPLE)
        .gasCalculator(ConstantinopleGasCalculator::new)
        .evmBuilder(MainnetEvmRegistries::constantinople)
        .blockReward(CONSTANTINOPLE_BLOCK_REWARD)
        .name("Constantinople")
        .build(protocolSchedule);
  }

  private static TransactionReceipt frontierTransactionReceiptFactory(
      final TransactionProcessor.Result result, final WorldState worldState, final long gasUsed) {
    return new TransactionReceipt(worldState.rootHash(), gasUsed, result.getLogs());
  }

  private static TransactionReceipt byzantiumTransactionReceiptFactory(
      final TransactionProcessor.Result result, final WorldState worldState, final long gasUsed) {
    return new TransactionReceipt(result.isSuccessful() ? 1 : 0, gasUsed, result.getLogs());
  }

  private static class DaoBlockProcessor implements BlockProcessor {

    private final BlockProcessor wrapped;

    public DaoBlockProcessor(final BlockProcessor wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public Result processBlock(
        final Blockchain blockchain,
        final MutableWorldState worldState,
        final BlockHeader blockHeader,
        final List<Transaction> transactions,
        final List<BlockHeader> ommers) {
      updateWorldStateForDao(worldState);
      return wrapped.processBlock(blockchain, worldState, blockHeader, transactions, ommers);
    }

    private static final Address DAO_REFUND_CONTRACT_ADDRESS =
        Address.fromHexString("0xbf4ed7b27f1d666546e30d74d50d173d20bca754");

    private void updateWorldStateForDao(final MutableWorldState worldState) {
      try {
        final JsonArray json =
            new JsonArray(
                Resources.toString(
                    Resources.getResource("daoAddresses.json"), StandardCharsets.UTF_8));
        final List<Address> addresses =
            IntStream.range(0, json.size())
                .mapToObj(json::getString)
                .map(Address::fromHexString)
                .collect(Collectors.toList());
        final WorldUpdater worldUpdater = worldState.updater();
        final MutableAccount daoRefundContract =
            worldUpdater.getOrCreate(DAO_REFUND_CONTRACT_ADDRESS);
        for (final Address address : addresses) {
          final MutableAccount account = worldUpdater.getOrCreate(address);
          final Wei balance = account.getBalance();
          account.decrementBalance(balance);
          daoRefundContract.incrementBalance(balance);
        }
        worldUpdater.commit();
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
