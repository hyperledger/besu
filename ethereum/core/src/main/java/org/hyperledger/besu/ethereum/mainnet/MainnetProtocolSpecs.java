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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.MainnetBlockValidator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.fees.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.TransactionGasBudgetCalculator;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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

  public static ProtocolSpecBuilder<Void> frontierDefinition(
      final OptionalInt configContractSizeLimit, final OptionalInt configStackSizeLimit) {
    final int contractSizeLimit = configContractSizeLimit.orElse(FRONTIER_CONTRACT_SIZE_LIMIT);
    final int stackSizeLimit = configStackSizeLimit.orElse(MessageFrame.DEFAULT_MAX_STACK_SIZE);
    return new ProtocolSpecBuilder<Void>()
        .gasCalculator(FrontierGasCalculator::new)
        .evmBuilder(MainnetEvmRegistries::frontier)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::frontier)
        .messageCallProcessorBuilder(MainnetMessageCallProcessor::new)
        .contractCreationProcessorBuilder(
            (gasCalculator, evm) ->
                new MainnetContractCreationProcessor(
                    gasCalculator,
                    evm,
                    false,
                    Collections.singletonList(MaxCodeSizeRule.of(contractSizeLimit)),
                    0))
        .transactionValidatorBuilder(
            gasCalculator ->
                new MainnetTransactionValidator(gasCalculator, false, Optional.empty()))
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
                    false,
                    stackSizeLimit,
                    Account.DEFAULT_VERSION,
                    TransactionPriceCalculator.frontier(),
                    CoinbaseFeePriceCalculator.frontier()))
        .privateTransactionProcessorBuilder(
            (gasCalculator,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor,
                privateTransactionValidator) ->
                new PrivateTransactionProcessor(
                    gasCalculator,
                    transactionValidator,
                    contractCreationProcessor,
                    messageCallProcessor,
                    false,
                    stackSizeLimit,
                    Account.DEFAULT_VERSION,
                    new PrivateTransactionValidator(Optional.empty())))
        .difficultyCalculator(MainnetDifficultyCalculators.FRONTIER)
        .blockHeaderValidatorBuilder(MainnetBlockHeaderValidator.create())
        .ommerHeaderValidatorBuilder(MainnetBlockHeaderValidator.createOmmerValidator())
        .blockBodyValidatorBuilder(MainnetBlockBodyValidator::new)
        .transactionReceiptFactory(MainnetProtocolSpecs::frontierTransactionReceiptFactory)
        .blockReward(FRONTIER_BLOCK_REWARD)
        .skipZeroBlockRewards(false)
        .blockProcessorBuilder(MainnetBlockProcessor::new)
        .blockValidatorBuilder(MainnetBlockValidator::new)
        .blockImporterBuilder(MainnetBlockImporter::new)
        .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
        .miningBeneficiaryCalculator(BlockHeader::getCoinbase)
        .name("Frontier");
  }

  public static ProtocolSpecBuilder<Void> homesteadDefinition(
      final OptionalInt configContractSizeLimit, final OptionalInt configStackSizeLimit) {
    final int contractSizeLimit = configContractSizeLimit.orElse(FRONTIER_CONTRACT_SIZE_LIMIT);
    return frontierDefinition(configContractSizeLimit, configStackSizeLimit)
        .gasCalculator(HomesteadGasCalculator::new)
        .evmBuilder(MainnetEvmRegistries::homestead)
        .contractCreationProcessorBuilder(
            (gasCalculator, evm) ->
                new MainnetContractCreationProcessor(
                    gasCalculator,
                    evm,
                    true,
                    Collections.singletonList(MaxCodeSizeRule.of(contractSizeLimit)),
                    0))
        .transactionValidatorBuilder(
            gasCalculator -> new MainnetTransactionValidator(gasCalculator, true, Optional.empty()))
        .difficultyCalculator(MainnetDifficultyCalculators.HOMESTEAD)
        .name("Homestead");
  }

  public static ProtocolSpecBuilder<Void> daoRecoveryInitDefinition(
      final OptionalInt contractSizeLimit, final OptionalInt configStackSizeLimit) {
    return homesteadDefinition(contractSizeLimit, configStackSizeLimit)
        .blockHeaderValidatorBuilder(MainnetBlockHeaderValidator.createDaoValidator())
        .blockProcessorBuilder(
            (transactionProcessor,
                transactionReceiptFactory,
                blockReward,
                miningBeneficiaryCalculator,
                skipZeroBlockRewards,
                gasBudgetCalculator) ->
                new DaoBlockProcessor(
                    new MainnetBlockProcessor(
                        transactionProcessor,
                        transactionReceiptFactory,
                        blockReward,
                        miningBeneficiaryCalculator,
                        skipZeroBlockRewards,
                        gasBudgetCalculator)))
        .name("DaoRecoveryInit");
  }

  public static ProtocolSpecBuilder<Void> daoRecoveryTransitionDefinition(
      final OptionalInt contractSizeLimit, final OptionalInt configStackSizeLimit) {
    return daoRecoveryInitDefinition(contractSizeLimit, configStackSizeLimit)
        .blockProcessorBuilder(MainnetBlockProcessor::new)
        .name("DaoRecoveryTransition");
  }

  public static ProtocolSpecBuilder<Void> tangerineWhistleDefinition(
      final OptionalInt contractSizeLimit, final OptionalInt configStackSizeLimit) {
    return homesteadDefinition(contractSizeLimit, configStackSizeLimit)
        .gasCalculator(TangerineWhistleGasCalculator::new)
        .name("TangerineWhistle");
  }

  public static ProtocolSpecBuilder<Void> spuriousDragonDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit) {
    final int contractSizeLimit =
        configContractSizeLimit.orElse(SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT);
    final int stackSizeLimit = configStackSizeLimit.orElse(MessageFrame.DEFAULT_MAX_STACK_SIZE);

    return tangerineWhistleDefinition(OptionalInt.empty(), configStackSizeLimit)
        .gasCalculator(SpuriousDragonGasCalculator::new)
        .skipZeroBlockRewards(true)
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
                    Collections.singletonList(MaxCodeSizeRule.of(contractSizeLimit)),
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
                    true,
                    stackSizeLimit,
                    Account.DEFAULT_VERSION,
                    TransactionPriceCalculator.frontier(),
                    CoinbaseFeePriceCalculator.frontier()))
        .name("SpuriousDragon");
  }

  public static ProtocolSpecBuilder<Void> byzantiumDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    final int stackSizeLimit = configStackSizeLimit.orElse(MessageFrame.DEFAULT_MAX_STACK_SIZE);
    return spuriousDragonDefinition(chainId, contractSizeLimit, configStackSizeLimit)
        .evmBuilder(MainnetEvmRegistries::byzantium)
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::byzantium)
        .difficultyCalculator(MainnetDifficultyCalculators.BYZANTIUM)
        .transactionReceiptFactory(
            enableRevertReason
                ? MainnetProtocolSpecs::byzantiumTransactionReceiptFactoryWithReasonEnabled
                : MainnetProtocolSpecs::byzantiumTransactionReceiptFactory)
        .blockReward(BYZANTIUM_BLOCK_REWARD)
        .privateTransactionValidatorBuilder(() -> new PrivateTransactionValidator(chainId))
        .privateTransactionProcessorBuilder(
            (gasCalculator,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor,
                privateTransactionValidator) ->
                new PrivateTransactionProcessor(
                    gasCalculator,
                    transactionValidator,
                    contractCreationProcessor,
                    messageCallProcessor,
                    false,
                    stackSizeLimit,
                    Account.DEFAULT_VERSION,
                    privateTransactionValidator))
        .name("Byzantium");
  }

  public static ProtocolSpecBuilder<Void> constantinopleDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    return byzantiumDefinition(chainId, contractSizeLimit, configStackSizeLimit, enableRevertReason)
        .difficultyCalculator(MainnetDifficultyCalculators.CONSTANTINOPLE)
        .gasCalculator(ConstantinopleGasCalculator::new)
        .evmBuilder(MainnetEvmRegistries::constantinople)
        .blockReward(CONSTANTINOPLE_BLOCK_REWARD)
        .name("Constantinople");
  }

  public static ProtocolSpecBuilder<Void> constantinopleFixDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    return constantinopleDefinition(
            chainId, contractSizeLimit, configStackSizeLimit, enableRevertReason)
        .gasCalculator(ConstantinopleFixGasCalculator::new)
        .name("ConstantinopleFix");
  }

  public static ProtocolSpecBuilder<Void> istanbulDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    final int contractSizeLimit =
        configContractSizeLimit.orElse(SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT);
    return constantinopleFixDefinition(
            chainId, configContractSizeLimit, configStackSizeLimit, enableRevertReason)
        .gasCalculator(IstanbulGasCalculator::new)
        .evmBuilder(
            gasCalculator ->
                MainnetEvmRegistries.istanbul(gasCalculator, chainId.orElse(BigInteger.ZERO)))
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::istanbul)
        .contractCreationProcessorBuilder(
            (gasCalculator, evm) ->
                new MainnetContractCreationProcessor(
                    gasCalculator,
                    evm,
                    true,
                    Collections.singletonList(MaxCodeSizeRule.of(contractSizeLimit)),
                    1,
                    SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES))
        .name("Istanbul");
  }

  static ProtocolSpecBuilder<Void> muirGlacierDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    return istanbulDefinition(chainId, contractSizeLimit, configStackSizeLimit, enableRevertReason)
        .difficultyCalculator(MainnetDifficultyCalculators.MUIR_GLACIER)
        .name("MuirGlacier");
  }

  static ProtocolSpecBuilder<Void> berlinDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    if (!ExperimentalEIPs.berlinEnabled) {
      throw new RuntimeException("Berlin feature flag must be enabled --Xberlin-enabled");
    }
    return muirGlacierDefinition(
            chainId, contractSizeLimit, configStackSizeLimit, enableRevertReason)
        .evmBuilder(
            gasCalculator ->
                MainnetEvmRegistries.berlin(gasCalculator, chainId.orElse(BigInteger.ZERO)))
        .precompileContractRegistryBuilder(MainnetPrecompiledContractRegistries::berlin)
        .name("Berlin");
  }

  // TODO EIP-1559 change for the actual fork name when known
  static ProtocolSpecBuilder<Void> eip1559Definition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions) {
    ExperimentalEIPs.eip1559MustBeEnabled();
    final int stackSizeLimit = configStackSizeLimit.orElse(MessageFrame.DEFAULT_MAX_STACK_SIZE);
    final TransactionPriceCalculator transactionPriceCalculator =
        TransactionPriceCalculator.eip1559();
    final EIP1559 eip1559 = new EIP1559(genesisConfigOptions.getEIP1559BlockNumber().orElse(0));
    return muirGlacierDefinition(
            chainId, contractSizeLimit, configStackSizeLimit, enableRevertReason)
        .transactionValidatorBuilder(
            gasCalculator ->
                new MainnetTransactionValidator(
                    gasCalculator,
                    true,
                    chainId,
                    Optional.of(eip1559),
                    AcceptedTransactionTypes.FEE_MARKET_TRANSITIONAL_TRANSACTIONS))
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
                    true,
                    stackSizeLimit,
                    Account.DEFAULT_VERSION,
                    transactionPriceCalculator,
                    CoinbaseFeePriceCalculator.eip1559()))
        .name("EIP-1559")
        .transactionPriceCalculator(transactionPriceCalculator)
        .eip1559(Optional.of(eip1559))
        .gasBudgetCalculator(TransactionGasBudgetCalculator.eip1559(eip1559))
        .blockHeaderValidatorBuilder(MainnetBlockHeaderValidator.createEip1559Validator(eip1559))
        .ommerHeaderValidatorBuilder(
            MainnetBlockHeaderValidator.createEip1559OmmerValidator(eip1559));
  }

  // TODO EIP-1559 change for the actual fork name when known
  static ProtocolSpecBuilder<Void> eip1559FinalizedDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions) {
    return eip1559Definition(
            chainId,
            contractSizeLimit,
            configStackSizeLimit,
            enableRevertReason,
            genesisConfigOptions)
        .transactionValidatorBuilder(
            gasCalculator ->
                new MainnetTransactionValidator(
                    gasCalculator,
                    true,
                    chainId,
                    Optional.of(
                        new EIP1559(genesisConfigOptions.getEIP1559BlockNumber().orElse(0))),
                    AcceptedTransactionTypes.FEE_MARKET_TRANSACTIONS));
  }

  private static TransactionReceipt frontierTransactionReceiptFactory(
      final TransactionProcessor.Result result, final WorldState worldState, final long gasUsed) {
    return new TransactionReceipt(
        worldState.rootHash(),
        gasUsed,
        result.getLogs(),
        Optional.empty()); // No revert reason in frontier
  }

  private static TransactionReceipt byzantiumTransactionReceiptFactory(
      final TransactionProcessor.Result result, final WorldState worldState, final long gasUsed) {
    return new TransactionReceipt(
        result.isSuccessful() ? 1 : 0, gasUsed, result.getLogs(), Optional.empty());
  }

  private static TransactionReceipt byzantiumTransactionReceiptFactoryWithReasonEnabled(
      final TransactionProcessor.Result result, final WorldState worldState, final long gasUsed) {
    return new TransactionReceipt(
        result.isSuccessful() ? 1 : 0, gasUsed, result.getLogs(), result.getRevertReason());
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
                    this.getClass().getResource("/daoAddresses.json"), StandardCharsets.UTF_8));
        final List<Address> addresses =
            IntStream.range(0, json.size())
                .mapToObj(json::getString)
                .map(Address::fromHexString)
                .collect(Collectors.toList());
        final WorldUpdater worldUpdater = worldState.updater();
        final MutableAccount daoRefundContract =
            worldUpdater.getOrCreate(DAO_REFUND_CONTRACT_ADDRESS).getMutable();
        for (final Address address : addresses) {
          final MutableAccount account = worldUpdater.getOrCreate(address).getMutable();
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
