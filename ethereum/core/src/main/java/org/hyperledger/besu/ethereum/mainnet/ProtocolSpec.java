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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.mainnet.blockhash.BlockHashProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestProcessorCoordinator;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestsValidator;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

import java.util.Optional;

/** A protocol specification. */
public class ProtocolSpec {

  private final String name;
  private final EVM evm;

  private final GasCalculator gasCalculator;

  private final GasLimitCalculator gasLimitCalculator;

  private final TransactionValidatorFactory transactionValidatorFactory;

  private final MainnetTransactionProcessor transactionProcessor;

  private final BlockHeaderValidator blockHeaderValidator;

  private final BlockHeaderValidator ommerHeaderValidator;

  private final BlockBodyValidator blockBodyValidator;

  private final BlockImporter blockImporter;

  private final BlockValidator blockValidator;

  private final BlockProcessor blockProcessor;

  private final BlockHeaderFunctions blockHeaderFunctions;

  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;

  private final DifficultyCalculator difficultyCalculator;

  private final Wei blockReward;

  private final MiningBeneficiaryCalculator miningBeneficiaryCalculator;

  private final PrecompileContractRegistry precompileContractRegistry;

  private final boolean skipZeroBlockRewards;

  private final PrivateTransactionProcessor privateTransactionProcessor;

  private final FeeMarket feeMarket;

  private final Optional<PoWHasher> powHasher;

  private final WithdrawalsValidator withdrawalsValidator;
  private final Optional<WithdrawalsProcessor> withdrawalsProcessor;
  private final RequestsValidator requestsValidator;
  private final Optional<RequestProcessorCoordinator> requestProcessorCoordinator;
  private final BlockHashProcessor blockHashProcessor;
  private final boolean isPoS;
  private final boolean isReplayProtectionSupported;

  /**
   * Creates a new protocol specification instance.
   *
   * @param name the protocol specification name
   * @param evm the EVM supporting the appropriate operations for this specification
   * @param transactionValidatorFactory the transaction validator factory to use
   * @param transactionProcessor the transaction processor to use
   * @param privateTransactionProcessor the private transaction processor to use
   * @param blockHeaderValidator the block header validator to use
   * @param ommerHeaderValidator the rules used to validate an ommer
   * @param blockBodyValidator the block body validator to use
   * @param blockProcessor the block processor to use
   * @param blockImporter the block importer to use
   * @param blockValidator the block validator to use
   * @param blockHeaderFunctions the block hash function to use
   * @param transactionReceiptFactory the transactionReceiptFactory to use
   * @param difficultyCalculator the difficultyCalculator to use
   * @param blockReward the blockReward to use.
   * @param miningBeneficiaryCalculator determines to whom mining proceeds are paid
   * @param precompileContractRegistry all the pre-compiled contracts added
   * @param skipZeroBlockRewards should rewards be skipped if it is zero
   * @param gasCalculator the gas calculator to use.
   * @param gasLimitCalculator the gas limit calculator to use.
   * @param feeMarket an {@link Optional} wrapping {@link FeeMarket} class if appropriate.
   * @param powHasher the proof-of-work hasher
   * @param withdrawalsProcessor the Withdrawals processor to use
   * @param requestsValidator the request validator to use
   * @param requestProcessorCoordinator the request processor to use
   * @param blockHashProcessor the blockHash processor to use
   * @param isPoS indicates whether the current spec is PoS
   * @param isReplayProtectionSupported indicates whether the current spec supports replay
   *     protection
   */
  public ProtocolSpec(
      final String name,
      final EVM evm,
      final TransactionValidatorFactory transactionValidatorFactory,
      final MainnetTransactionProcessor transactionProcessor,
      final PrivateTransactionProcessor privateTransactionProcessor,
      final BlockHeaderValidator blockHeaderValidator,
      final BlockHeaderValidator ommerHeaderValidator,
      final BlockBodyValidator blockBodyValidator,
      final BlockProcessor blockProcessor,
      final BlockImporter blockImporter,
      final BlockValidator blockValidator,
      final BlockHeaderFunctions blockHeaderFunctions,
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
      final DifficultyCalculator difficultyCalculator,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final PrecompileContractRegistry precompileContractRegistry,
      final boolean skipZeroBlockRewards,
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final FeeMarket feeMarket,
      final Optional<PoWHasher> powHasher,
      final WithdrawalsValidator withdrawalsValidator,
      final Optional<WithdrawalsProcessor> withdrawalsProcessor,
      final RequestsValidator requestsValidator,
      final Optional<RequestProcessorCoordinator> requestProcessorCoordinator,
      final BlockHashProcessor blockHashProcessor,
      final boolean isPoS,
      final boolean isReplayProtectionSupported) {
    this.name = name;
    this.evm = evm;
    this.transactionValidatorFactory = transactionValidatorFactory;
    this.transactionProcessor = transactionProcessor;
    this.privateTransactionProcessor = privateTransactionProcessor;
    this.blockHeaderValidator = blockHeaderValidator;
    this.ommerHeaderValidator = ommerHeaderValidator;
    this.blockBodyValidator = blockBodyValidator;
    this.blockProcessor = blockProcessor;
    this.blockImporter = blockImporter;
    this.blockValidator = blockValidator;
    this.blockHeaderFunctions = blockHeaderFunctions;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.difficultyCalculator = difficultyCalculator;
    this.blockReward = blockReward;
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
    this.precompileContractRegistry = precompileContractRegistry;
    this.skipZeroBlockRewards = skipZeroBlockRewards;
    this.gasCalculator = gasCalculator;
    this.gasLimitCalculator = gasLimitCalculator;
    this.feeMarket = feeMarket;
    this.powHasher = powHasher;
    this.withdrawalsValidator = withdrawalsValidator;
    this.withdrawalsProcessor = withdrawalsProcessor;
    this.requestsValidator = requestsValidator;
    this.requestProcessorCoordinator = requestProcessorCoordinator;
    this.blockHashProcessor = blockHashProcessor;
    this.isPoS = isPoS;
    this.isReplayProtectionSupported = isReplayProtectionSupported;
  }

  /**
   * Returns the protocol specification name.
   *
   * @return the protocol specification name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the transaction validator factory used in this specification.
   *
   * @return the transaction validator factory
   */
  public TransactionValidatorFactory getTransactionValidatorFactory() {
    return transactionValidatorFactory;
  }

  public boolean isReplayProtectionSupported() {
    return isReplayProtectionSupported;
  }

  /**
   * Returns the transaction processor used in this specification.
   *
   * @return the transaction processor
   */
  public MainnetTransactionProcessor getTransactionProcessor() {
    return transactionProcessor;
  }

  /**
   * Returns the block processor used in this specification.
   *
   * @return the block processor
   */
  public BlockProcessor getBlockProcessor() {
    return blockProcessor;
  }

  /**
   * Returns the block importer used in this specification.
   *
   * @return the block importer
   */
  public BlockImporter getBlockImporter() {
    return blockImporter;
  }

  /**
   * Returns the block validator used in this specification.
   *
   * @return the block validator
   */
  public BlockValidator getBlockValidator() {
    return blockValidator;
  }

  /**
   * Returns the block header validator used in this specification.
   *
   * @return the block header validator
   */
  public BlockHeaderValidator getBlockHeaderValidator() {
    return blockHeaderValidator;
  }

  /**
   * Returns the block ommer header validator used in this specification.
   *
   * @return the block ommer header validator
   */
  public BlockHeaderValidator getOmmerHeaderValidator() {
    return ommerHeaderValidator;
  }

  /**
   * Returns the block body validator used in this specification.
   *
   * @return the block body validator
   */
  public BlockBodyValidator getBlockBodyValidator() {
    return blockBodyValidator;
  }

  /**
   * Returns the block hash function used in this specification.
   *
   * @return the block hash function
   */
  public BlockHeaderFunctions getBlockHeaderFunctions() {
    return blockHeaderFunctions;
  }

  /**
   * Returns the EVM for this specification.
   *
   * @return the EVM
   */
  public EVM getEvm() {
    return evm;
  }

  /**
   * Returns the TransctionReceiptFactory used in this specification
   *
   * @return the transaction receipt factory
   */
  public AbstractBlockProcessor.TransactionReceiptFactory getTransactionReceiptFactory() {
    return transactionReceiptFactory;
  }

  /**
   * Returns the DifficultyCalculator used in this specification.
   *
   * @return the difficulty calculator.
   */
  public DifficultyCalculator getDifficultyCalculator() {
    return difficultyCalculator;
  }

  /**
   * Returns the blockReward used in this specification.
   *
   * @return the amount to be rewarded for block mining.
   */
  public Wei getBlockReward() {
    return blockReward;
  }

  /**
   * Sometimes we apply zero block rewards to the Trie (pre EIP158) sometimes we don't (post EIP158
   * and all clique nets). The initial behavior was to never apply zero rewards. If a zero reward is
   * applied it could affect the state tree with a "empty" account.
   *
   * @return If we skip block rewards when the reward is zero.
   */
  public boolean isSkipZeroBlockRewards() {
    return skipZeroBlockRewards;
  }

  public MiningBeneficiaryCalculator getMiningBeneficiaryCalculator() {
    return miningBeneficiaryCalculator;
  }

  public PrecompileContractRegistry getPrecompileContractRegistry() {
    return precompileContractRegistry;
  }

  public PrivateTransactionProcessor getPrivateTransactionProcessor() {
    return privateTransactionProcessor;
  }

  /**
   * Returns the gasCalculator used in this specification.
   *
   * @return the gas calculator
   */
  public GasCalculator getGasCalculator() {
    return gasCalculator;
  }

  /**
   * Returns the gasLimitCalculator used in this specification.
   *
   * @return the gas limit calculator
   */
  public GasLimitCalculator getGasLimitCalculator() {
    return gasLimitCalculator;
  }

  /**
   * Returns the Fee Market used in this specification.
   *
   * @return the {@link FeeMarket} implementation.
   */
  public FeeMarket getFeeMarket() {
    return feeMarket;
  }

  /**
   * Returns the Proof-of-Work hasher
   *
   * @return the Proof-of-Work hasher
   */
  public Optional<PoWHasher> getPoWHasher() {
    return powHasher;
  }

  public WithdrawalsValidator getWithdrawalsValidator() {
    return withdrawalsValidator;
  }

  public Optional<WithdrawalsProcessor> getWithdrawalsProcessor() {
    return withdrawalsProcessor;
  }

  public RequestsValidator getRequestsValidator() {
    return requestsValidator;
  }

  public Optional<RequestProcessorCoordinator> getRequestProcessorCoordinator() {
    return requestProcessorCoordinator;
  }

  public BlockHashProcessor getBlockHashProcessor() {
    return blockHashProcessor;
  }

  /**
   * Returns true if the network is running Proof of Stake
   *
   * @return true if the network is running Proof of Stake
   */
  public boolean isPoS() {
    return isPoS;
  }
}
