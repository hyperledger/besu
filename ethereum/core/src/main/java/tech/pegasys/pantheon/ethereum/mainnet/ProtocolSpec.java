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

import tech.pegasys.pantheon.ethereum.BlockValidator;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.TransactionFilter;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockProcessor.TransactionReceiptFactory;
import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;

/** A protocol specification. */
public class ProtocolSpec<C> {

  private final String name;
  private final EVM evm;

  private final GasCalculator gasCalculator;

  private final TransactionValidator transactionValidator;

  private final TransactionProcessor transactionProcessor;

  private final BlockHeaderValidator<C> blockHeaderValidator;

  private final BlockHeaderValidator<C> ommerHeaderValidator;

  private final BlockBodyValidator<C> blockBodyValidator;

  private final BlockImporter<C> blockImporter;

  private final BlockValidator<C> blockValidator;

  private final BlockProcessor blockProcessor;

  private final BlockHeaderFunctions blockHeaderFunctions;

  private final TransactionReceiptFactory transactionReceiptFactory;

  private final DifficultyCalculator<C> difficultyCalculator;

  private final Wei blockReward;

  private final MiningBeneficiaryCalculator miningBeneficiaryCalculator;

  private final PrecompileContractRegistry precompileContractRegistry;

  private final boolean skipZeroBlockRewards;

  /**
   * Creates a new protocol specification instance.
   *
   * @param name the protocol specification name
   * @param evm the EVM supporting the appropriate operations for this specification
   * @param transactionValidator the transaction validator to use
   * @param transactionProcessor the transaction processor to use
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
   */
  public ProtocolSpec(
      final String name,
      final EVM evm,
      final TransactionValidator transactionValidator,
      final TransactionProcessor transactionProcessor,
      final BlockHeaderValidator<C> blockHeaderValidator,
      final BlockHeaderValidator<C> ommerHeaderValidator,
      final BlockBodyValidator<C> blockBodyValidator,
      final BlockProcessor blockProcessor,
      final BlockImporter<C> blockImporter,
      final BlockValidator<C> blockValidator,
      final BlockHeaderFunctions blockHeaderFunctions,
      final TransactionReceiptFactory transactionReceiptFactory,
      final DifficultyCalculator<C> difficultyCalculator,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final PrecompileContractRegistry precompileContractRegistry,
      final boolean skipZeroBlockRewards,
      final GasCalculator gasCalculator) {
    this.name = name;
    this.evm = evm;
    this.transactionValidator = transactionValidator;
    this.transactionProcessor = transactionProcessor;
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
   * Returns the transaction validator used in this specification.
   *
   * @return the transaction validator
   */
  public TransactionValidator getTransactionValidator() {
    return transactionValidator;
  }

  /**
   * Returns the transaction processor used in this specification.
   *
   * @return the transaction processor
   */
  public TransactionProcessor getTransactionProcessor() {
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
  public BlockImporter<C> getBlockImporter() {
    return blockImporter;
  }

  /**
   * Returns the block validator used in this specification.
   *
   * @return the block validator
   */
  public BlockValidator<C> getBlockValidator() {
    return blockValidator;
  }

  /**
   * Returns the block header validator used in this specification.
   *
   * @return the block header validator
   */
  public BlockHeaderValidator<C> getBlockHeaderValidator() {
    return blockHeaderValidator;
  }

  /**
   * Returns the block ommer header validator used in this specification.
   *
   * @return the block ommer header validator
   */
  public BlockHeaderValidator<C> getOmmerHeaderValidator() {
    return ommerHeaderValidator;
  }

  /**
   * Returns the block body validator used in this specification.
   *
   * @return the block body validator
   */
  public BlockBodyValidator<C> getBlockBodyValidator() {
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
  public TransactionReceiptFactory getTransactionReceiptFactory() {
    return transactionReceiptFactory;
  }

  /**
   * Returns the DifficultyCalculator used in this specification.
   *
   * @return the difficulty calculator.
   */
  public DifficultyCalculator<C> getDifficultyCalculator() {
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

  /**
   * Returns the gasCalculator used in this specification.
   *
   * @return the gas calculator
   */
  public GasCalculator getGasCalculator() {
    return gasCalculator;
  }

  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    transactionValidator.setTransactionFilter(transactionFilter);
  }
}
