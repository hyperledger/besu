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
package org.hyperledger.besu.evm.fluent;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.collections.trie.BytesTrieSet;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.errorprone.annotations.InlineMe;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** The Evm executor. */
public class EVMExecutor {

  private final EVM evm;
  private PrecompileContractRegistry precompileContractRegistry;
  private boolean commitWorldState = false;
  private WorldUpdater worldUpdater = new SimpleWorld();
  private long gas = Long.MAX_VALUE;
  private Address receiver = Address.ZERO;
  private Address sender = Address.ZERO;
  private Address contract = Address.ZERO;
  private Address coinbase = Address.ZERO;
  private Wei gasPriceGWei = Wei.ZERO;
  private Wei blobGasPrice = Wei.ZERO;
  private Bytes callData = Bytes.EMPTY;
  private Wei ethValue = Wei.ZERO;
  private Code code = CodeV0.EMPTY_CODE;
  private BlockValues blockValues = new SimpleBlockValues();
  private BlockHashLookup blockHashLookup = (__, ___) -> null;
  private Optional<List<VersionedHash>> versionedHashes = Optional.empty();
  private OperationTracer tracer = OperationTracer.NO_TRACING;
  private boolean requireDeposit = true;
  private List<ContractValidationRule> contractValidationRules =
      List.of(
          MaxCodeSizeRule.from(EvmSpecVersion.SPURIOUS_DRAGON, EvmConfiguration.DEFAULT),
          PrefixCodeRule.of());
  private long initialNonce = 1;
  private Collection<Address> forceCommitAddresses = List.of(Address.fromHexString("0x03"));
  private Set<Address> accessListWarmAddresses = new BytesTrieSet<>(Address.SIZE);
  private Multimap<Address, Bytes32> accessListWarmStorage = HashMultimap.create();
  private MessageCallProcessor messageCallProcessor = null;
  private ContractCreationProcessor contractCreationProcessor = null;
  private MessageFrame.Type messageFrameType = MessageFrame.Type.MESSAGE_CALL;

  private EVMExecutor(final EVM evm) {
    checkNotNull(evm, "evm must not be null");
    this.evm = evm;
  }

  /**
   * Create an EVM with the most current activated fork on chain ID 1.
   *
   * <p>Note, this will change across versions
   *
   * @return executor builder
   */
  public static EVMExecutor evm() {
    return evm(EvmSpecVersion.mostRecent());
  }

  /**
   * Create an EVM at the specified version with chain ID 1.
   *
   * @param fork the EVM spec version to use
   * @return executor builder
   */
  public static EVMExecutor evm(final EvmSpecVersion fork) {
    return evm(fork, BigInteger.ONE);
  }

  /**
   * Create an EVM at the specified version and chain ID
   *
   * @param fork the EVM spec version to use
   * @param chainId the chain ID to use
   * @return executor builder
   */
  public static EVMExecutor evm(final EvmSpecVersion fork, final BigInteger chainId) {
    return evm(fork, chainId, EvmConfiguration.DEFAULT);
  }

  /**
   * Create an EVM at the specified version and chain ID
   *
   * @param fork the EVM spec version to use
   * @param chainId the chain ID to use
   * @return executor builder
   */
  public static EVMExecutor evm(final EvmSpecVersion fork, final Bytes chainId) {
    return evm(fork, new BigInteger(1, chainId.toArrayUnsafe()), EvmConfiguration.DEFAULT);
  }

  /**
   * Create an EVM at the specified version and chain ID
   *
   * @param fork the EVM spec version to use
   * @param chainId the chain ID to use
   * @param evmConfiguration system configuration options.
   * @return executor builder
   */
  public static EVMExecutor evm(
      final EvmSpecVersion fork,
      final BigInteger chainId,
      final EvmConfiguration evmConfiguration) {
    return switch (fork) {
      case FRONTIER -> frontier(evmConfiguration);
      case HOMESTEAD -> homestead(evmConfiguration);
      case TANGERINE_WHISTLE -> tangerineWhistle(evmConfiguration);
      case SPURIOUS_DRAGON -> spuriousDragon(evmConfiguration);
      case BYZANTIUM -> byzantium(evmConfiguration);
      case CONSTANTINOPLE -> constantinople(evmConfiguration);
      case PETERSBURG -> petersburg(evmConfiguration);
      case ISTANBUL -> istanbul(chainId, evmConfiguration);
      case BERLIN -> berlin(chainId, evmConfiguration);
      case LONDON -> london(chainId, evmConfiguration);
      case PARIS -> paris(chainId, evmConfiguration);
      case SHANGHAI -> shanghai(chainId, evmConfiguration);
      case CANCUN -> cancun(chainId, evmConfiguration);
      case CANCUN_EOF -> cancunEOF(chainId, evmConfiguration);
      case PRAGUE -> prague(chainId, evmConfiguration);
      case OSAKA -> osaka(chainId, evmConfiguration);
      case AMSTERDAM -> amsterdam(chainId, evmConfiguration);
      case BOGOTA -> bogota(chainId, evmConfiguration);
      case POLIS -> polis(chainId, evmConfiguration);
      case BANGKOK -> bangkok(chainId, evmConfiguration);
      case FUTURE_EIPS -> futureEips(chainId, evmConfiguration);
      case EXPERIMENTAL_EIPS -> experimentalEips(chainId, evmConfiguration);
    };
  }

  /**
   * Instantiate Evm executor.
   *
   * @param evm the evm
   * @return the evm executor
   */
  public static EVMExecutor evm(final EVM evm) {
    return new EVMExecutor(evm);
  }

  /**
   * Instantiate Frontier evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor frontier(final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.frontier(evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.frontier(executor.evm.getGasCalculator());
    executor.contractValidationRules = List.of();
    executor.requireDeposit = false;
    executor.forceCommitAddresses = List.of();
    executor.initialNonce = 0;
    return executor;
  }

  /**
   * Instantiate Homestead evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor homestead(final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.homestead(evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.frontier(executor.evm.getGasCalculator());
    executor.contractValidationRules = List.of();
    executor.forceCommitAddresses = List.of();
    executor.initialNonce = 0;
    return executor;
  }

  /**
   * Instantiate Tangerine whistle evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor tangerineWhistle(final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.tangerineWhistle(evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.frontier(executor.evm.getGasCalculator());
    executor.contractValidationRules = List.of();
    executor.initialNonce = 0;
    return executor;
  }

  /**
   * Instantiate Spurious dragon evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor spuriousDragon(final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.spuriousDragon(evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.frontier(executor.evm.getGasCalculator());
    executor.contractValidationRules = List.of(MaxCodeSizeRule.from(executor.evm));
    return executor;
  }

  /**
   * Instantiate Byzantium evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor byzantium(final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.byzantium(evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.byzantium(executor.evm.getGasCalculator());
    executor.contractValidationRules = List.of(MaxCodeSizeRule.from(executor.evm));
    return executor;
  }

  /**
   * Instantiate Constantinople evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor constantinople(final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.constantinople(evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.byzantium(executor.evm.getGasCalculator());
    executor.contractValidationRules = List.of(MaxCodeSizeRule.from(executor.evm));
    return executor;
  }

  /**
   * Instantiate Petersburg evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor petersburg(final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.petersburg(evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.byzantium(executor.evm.getGasCalculator());
    executor.contractValidationRules = List.of(MaxCodeSizeRule.from(executor.evm));
    return executor;
  }

  /**
   * Instantiate Istanbul evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   * @deprecated Migrate to use {@link EVMExecutor#evm(EvmSpecVersion)}.
   */
  @InlineMe(
      replacement = "EVMExecutor.evm(EvmSpecVersion.ISTANBUL, BigInteger.ONE, evmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.evm.EvmSpecVersion",
        "org.hyperledger.besu.evm.fluent.EVMExecutor"
      })
  @Deprecated(forRemoval = true)
  public static EVMExecutor istanbul(final EvmConfiguration evmConfiguration) {
    return evm(EvmSpecVersion.ISTANBUL, BigInteger.ONE, evmConfiguration);
  }

  /**
   * Instantiate Istanbul evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor istanbul(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.istanbul(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.istanbul(executor.evm.getGasCalculator());
    executor.contractValidationRules = List.of(MaxCodeSizeRule.from(executor.evm));
    return executor;
  }

  /**
   * Instantiate Berlin evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   * @deprecated Migrate to use {@link EVMExecutor#evm(EvmSpecVersion)}.
   */
  @InlineMe(
      replacement = "EVMExecutor.evm(EvmSpecVersion.BERLIN, BigInteger.ONE, evmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.evm.EvmSpecVersion",
        "org.hyperledger.besu.evm.fluent.EVMExecutor"
      })
  @Deprecated(forRemoval = true)
  public static EVMExecutor berlin(final EvmConfiguration evmConfiguration) {
    return evm(EvmSpecVersion.BERLIN, BigInteger.ONE, evmConfiguration);
  }

  /**
   * Instantiate berlin evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor berlin(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.berlin(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.istanbul(executor.evm.getGasCalculator());
    executor.contractValidationRules = List.of(MaxCodeSizeRule.from(executor.evm));
    return executor;
  }

  /**
   * Instantiate London evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   * @deprecated Migrate to use {@link EVMExecutor#evm(EvmSpecVersion)}.
   */
  @InlineMe(
      replacement = "EVMExecutor.evm(EvmSpecVersion.LONDON, BigInteger.ONE, evmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.evm.EvmSpecVersion",
        "org.hyperledger.besu.evm.fluent.EVMExecutor"
      })
  @Deprecated(forRemoval = true)
  public static EVMExecutor london(final EvmConfiguration evmConfiguration) {
    return evm(EvmSpecVersion.LONDON, BigInteger.ONE, evmConfiguration);
  }

  /**
   * Instantiate London evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor london(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.london(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.istanbul(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Paris evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   * @deprecated Migrate to use {@link EVMExecutor#evm(EvmSpecVersion)}.
   */
  @InlineMe(
      replacement = "EVMExecutor.evm(EvmSpecVersion.PARIS, BigInteger.ONE, evmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.evm.EvmSpecVersion",
        "org.hyperledger.besu.evm.fluent.EVMExecutor"
      })
  @Deprecated(forRemoval = true)
  public static EVMExecutor paris(final EvmConfiguration evmConfiguration) {
    return evm(EvmSpecVersion.PARIS, BigInteger.ONE, evmConfiguration);
  }

  /**
   * Instantiate Paris evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor paris(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.paris(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.istanbul(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Shanghai evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   * @deprecated Migrate to use {@link EVMExecutor#evm(EvmSpecVersion)}.
   */
  @InlineMe(
      replacement = "EVMExecutor.evm(EvmSpecVersion.SHANGHAI, BigInteger.ONE, evmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.evm.EvmSpecVersion",
        "org.hyperledger.besu.evm.fluent.EVMExecutor"
      })
  @Deprecated(forRemoval = true)
  public static EVMExecutor shanghai(final EvmConfiguration evmConfiguration) {
    return evm(EvmSpecVersion.SHANGHAI, BigInteger.ONE, evmConfiguration);
  }

  /**
   * Instantiate Shanghai evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor shanghai(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.shanghai(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.istanbul(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Cancun evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   * @deprecated Migrate to use {@link EVMExecutor#evm(EvmSpecVersion)}.
   */
  @InlineMe(
      replacement = "EVMExecutor.evm(EvmSpecVersion.CANCUN, BigInteger.ONE, evmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.evm.EvmSpecVersion",
        "org.hyperledger.besu.evm.fluent.EVMExecutor"
      })
  @Deprecated(forRemoval = true)
  public static EVMExecutor cancun(final EvmConfiguration evmConfiguration) {
    return evm(EvmSpecVersion.CANCUN, BigInteger.ONE, evmConfiguration);
  }

  /**
   * Instantiate Cancun evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor cancun(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.cancun(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.cancun(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Cancun EOF evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor cancunEOF(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.cancunEOF(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.cancun(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Prague evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor prague(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.prague(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.prague(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Osaka evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor osaka(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.osaka(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.prague(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Amsterdam evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor amsterdam(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.amsterdam(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.prague(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Bogota evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor bogota(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.bogota(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.prague(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Polis evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor polis(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.polis(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.prague(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Bangkok evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor bangkok(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.bangkok(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.prague(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Future EIPs evm executor.
   *
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   * @deprecated Migrate to use {@link EVMExecutor#evm(EvmSpecVersion)}.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @InlineMe(
      replacement = "EVMExecutor.evm(EvmSpecVersion.FUTURE_EIPS, BigInteger.ONE, evmConfiguration)",
      imports = {
        "java.math.BigInteger",
        "org.hyperledger.besu.evm.EvmSpecVersion",
        "org.hyperledger.besu.evm.fluent.EVMExecutor"
      })
  @Deprecated(forRemoval = true)
  public static EVMExecutor futureEips(final EvmConfiguration evmConfiguration) {
    return evm(EvmSpecVersion.FUTURE_EIPS, BigInteger.ONE, evmConfiguration);
  }

  /**
   * Instantiate Future EIPs evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor futureEips(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor = new EVMExecutor(MainnetEVMs.futureEips(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.futureEIPs(executor.evm.getGasCalculator());
    return executor;
  }

  /**
   * Instantiate Experimental EIPs evm executor.
   *
   * @param chainId the chain ID
   * @param evmConfiguration the evm configuration
   * @return the evm executor
   */
  public static EVMExecutor experimentalEips(
      final BigInteger chainId, final EvmConfiguration evmConfiguration) {
    final EVMExecutor executor =
        new EVMExecutor(MainnetEVMs.experimentalEips(chainId, evmConfiguration));
    executor.precompileContractRegistry =
        MainnetPrecompiledContracts.futureEIPs(executor.evm.getGasCalculator());
    return executor;
  }

  private MessageCallProcessor thisMessageCallProcessor() {
    return Objects.requireNonNullElseGet(
        messageCallProcessor, () -> new MessageCallProcessor(evm, precompileContractRegistry));
  }

  private ContractCreationProcessor thisContractCreationProcessor() {
    return Objects.requireNonNullElseGet(
        contractCreationProcessor,
        () ->
            new ContractCreationProcessor(
                evm, requireDeposit, contractValidationRules, initialNonce, forceCommitAddresses));
  }

  /**
   * Execute code.
   *
   * @param code the code
   * @param inputData the input data
   * @param value the value
   * @param receiver the receiver
   * @return the bytes
   */
  public Bytes execute(
      final Code code, final Bytes inputData, final Wei value, final Address receiver) {
    this.code = code;
    this.callData = inputData;
    this.ethValue = value;
    this.receiver = receiver;
    return execute();
  }

  /**
   * Execute code.
   *
   * @param codeBytes the code bytes
   * @param inputData the input data
   * @param value the value
   * @param receiver the receiver
   * @return the bytes
   */
  public Bytes execute(
      final Bytes codeBytes, final Bytes inputData, final Wei value, final Address receiver) {
    this.code = evm.getCode(Hash.hash(codeBytes), codeBytes);
    this.callData = inputData;
    this.ethValue = value;
    this.receiver = receiver;
    return execute();
  }

  /**
   * Execute.
   *
   * @return the bytes
   */
  public Bytes execute() {
    final MessageCallProcessor mcp = thisMessageCallProcessor();
    final ContractCreationProcessor ccp = thisContractCreationProcessor();
    final MessageFrame initialMessageFrame =
        MessageFrame.builder()
            .type(messageFrameType)
            .worldUpdater(worldUpdater.updater())
            .initialGas(gas)
            .contract(contract)
            .address(receiver)
            .originator(sender)
            .sender(sender)
            .gasPrice(gasPriceGWei)
            .blobGasPrice(blobGasPrice)
            .inputData(callData)
            .value(ethValue)
            .apparentValue(ethValue)
            .code(code)
            .blockValues(blockValues)
            .miningBeneficiary(coinbase)
            .blockHashLookup(blockHashLookup)
            .accessListWarmAddresses(accessListWarmAddresses)
            .accessListWarmStorage(accessListWarmStorage)
            .versionedHashes(versionedHashes)
            .completer(c -> {})
            .build();

    final Deque<MessageFrame> messageFrameStack = initialMessageFrame.getMessageFrameStack();
    while (!messageFrameStack.isEmpty()) {
      final MessageFrame messageFrame = messageFrameStack.peek();
      (switch (messageFrame.getType()) {
            case CONTRACT_CREATION -> ccp;
            case MESSAGE_CALL -> mcp;
          })
          .process(messageFrame, tracer);
    }
    initialMessageFrame.getSelfDestructs().forEach(worldUpdater::deleteAccount);
    if (commitWorldState) {
      worldUpdater.commit();
    }
    return initialMessageFrame.getReturnData();
  }

  /**
   * MArk Commit world state to true.
   *
   * @return the evm executor
   */
  public EVMExecutor commitWorldState() {
    return commitWorldState(true);
  }

  /**
   * Sets Commit world state.
   *
   * @param commitWorldState the commit world state
   * @return the evm executor
   */
  public EVMExecutor commitWorldState(final boolean commitWorldState) {
    this.commitWorldState = commitWorldState;
    return this;
  }

  /**
   * Sets World updater.
   *
   * @param worldUpdater the world updater
   * @return the evm executor
   */
  public EVMExecutor worldUpdater(final WorldUpdater worldUpdater) {
    this.worldUpdater = worldUpdater;
    return this;
  }

  /**
   * Sets Gas.
   *
   * @param gas the gas
   * @return the evm executor
   */
  public EVMExecutor gas(final long gas) {
    this.gas = gas;
    return this;
  }

  /**
   * Sets Receiver address.
   *
   * @param receiver the receiver
   * @return the evm executor
   */
  public EVMExecutor receiver(final Address receiver) {
    this.receiver = receiver;
    return this;
  }

  /**
   * Sets Sender address.
   *
   * @param sender the sender
   * @return the evm executor
   */
  public EVMExecutor sender(final Address sender) {
    this.sender = sender;
    return this;
  }

  /**
   * Sets the address of the executing contract
   *
   * @param contract the contract
   * @return the evm executor
   */
  public EVMExecutor contract(final Address contract) {
    this.contract = contract;
    return this;
  }

  /**
   * Sets the address of the coinbase aka mining beneficiary
   *
   * @param coinbase the coinbase
   * @return the evm executor
   */
  public EVMExecutor coinbase(final Address coinbase) {
    this.coinbase = coinbase;
    // EIP-3651
    if (EvmSpecVersion.SHANGHAI.compareTo(evm.getEvmVersion()) <= 0) {
      this.warmAddress(coinbase);
    }
    return this;
  }

  /**
   * Sets Gas price GWei.
   *
   * @param gasPriceGWei the gas price g wei
   * @return the evm executor
   */
  public EVMExecutor gasPriceGWei(final Wei gasPriceGWei) {
    this.gasPriceGWei = gasPriceGWei;
    return this;
  }

  /**
   * Sets Blob Gas price.
   *
   * @param blobGasPrice the blob gas price g wei
   * @return the evm executor
   */
  public EVMExecutor blobGasPrice(final Wei blobGasPrice) {
    this.blobGasPrice = blobGasPrice;
    return this;
  }

  /**
   * Sets Call data.
   *
   * @param callData the call data
   * @return the evm executor
   */
  public EVMExecutor callData(final Bytes callData) {
    this.callData = callData;
    return this;
  }

  /**
   * Sets Eth value.
   *
   * @param ethValue the eth value
   * @return the evm executor
   */
  public EVMExecutor ethValue(final Wei ethValue) {
    this.ethValue = ethValue;
    return this;
  }

  /**
   * Sets Code.
   *
   * @param code the code
   * @return the evm executor
   */
  public EVMExecutor code(final Code code) {
    this.code = code;
    return this;
  }

  /**
   * Sets Code.
   *
   * @param codeBytes the code bytes
   * @return the evm executor
   */
  public EVMExecutor code(final Bytes codeBytes) {
    return code(codeBytes, Hash.hash(codeBytes));
  }

  /**
   * Sets Code.
   *
   * @param codeBytes the code bytes
   * @param hash the hash
   * @return the evm executor
   */
  public EVMExecutor code(final Bytes codeBytes, final Hash hash) {
    this.code = evm.getCode(hash, codeBytes);
    return this;
  }

  /**
   * Sets Block values.
   *
   * @param blockValues the block values
   * @return the evm executor
   */
  public EVMExecutor blockValues(final BlockValues blockValues) {
    this.blockValues = blockValues;
    return this;
  }

  /**
   * Sets the difficulty bytes on a SimpleBlockValues object
   *
   * @param difficulty the difficulty
   * @return the evm executor
   * @throws ClassCastException if the blockValues was set with a value that is not a {@link
   *     SimpleBlockValues}
   */
  public EVMExecutor difficulty(final Bytes difficulty) {
    ((SimpleBlockValues) this.blockValues).setDifficultyBytes(difficulty);
    return this;
  }

  /**
   * Sets the mix hash bytes on a SimpleBlockValues object
   *
   * @param mixHash the mix hash
   * @return the evm executor
   * @throws ClassCastException if the blockValues was set with a value that is not a {@link
   *     SimpleBlockValues}
   */
  public EVMExecutor mixHash(final Bytes32 mixHash) {
    ((SimpleBlockValues) this.blockValues).setMixHashOrPrevRandao(mixHash);
    return this;
  }

  /**
   * Sets the prev randao bytes on a SimpleBlockValues object
   *
   * @param prevRandao the prev randao
   * @return the evm executor
   * @throws ClassCastException if the blockValues was set with a value that is not a {@link
   *     SimpleBlockValues}
   */
  public EVMExecutor prevRandao(final Bytes32 prevRandao) {
    ((SimpleBlockValues) this.blockValues).setMixHashOrPrevRandao(prevRandao);
    return this;
  }

  /**
   * Sets the baseFee for the block, directly.
   *
   * @param baseFee the baseFee
   * @return the evm executor
   * @throws ClassCastException if the blockValues was set with a value that is not a {@link
   *     SimpleBlockValues}
   */
  public EVMExecutor baseFee(final Wei baseFee) {
    return baseFee(Optional.ofNullable(baseFee));
  }

  /**
   * Sets the baseFee for the block, as an Optional.
   *
   * @param baseFee the baseFee
   * @return the evm executor
   * @throws ClassCastException if the blockValues was set with a value that is not a {@link
   *     SimpleBlockValues}
   */
  public EVMExecutor baseFee(final Optional<Wei> baseFee) {
    ((SimpleBlockValues) this.blockValues).setBaseFee(baseFee);
    return this;
  }

  /**
   * Sets the block number for the block.
   *
   * @param number the block number
   * @return the evm executor
   * @throws ClassCastException if the blockValues was set with a value that is not a {@link
   *     SimpleBlockValues}
   */
  public EVMExecutor number(final long number) {
    ((SimpleBlockValues) this.blockValues).setNumber(number);
    return this;
  }

  /**
   * Sets the timestamp for the block.
   *
   * @param timestamp the block timestamp
   * @return the evm executor
   * @throws ClassCastException if the blockValues was set with a value that is not a {@link
   *     SimpleBlockValues}
   */
  public EVMExecutor timestamp(final long timestamp) {
    ((SimpleBlockValues) this.blockValues).setTimestamp(timestamp);
    return this;
  }

  /**
   * Sets the gas limit for the block.
   *
   * @param gasLimit the block gas limit
   * @return the evm executor
   * @throws ClassCastException if the blockValues was set with a value that is not a {@link
   *     SimpleBlockValues}
   */
  public EVMExecutor gasLimit(final long gasLimit) {
    ((SimpleBlockValues) this.blockValues).setGasLimit(gasLimit);
    return this;
  }

  /**
   * Sets the block hash lookup function
   *
   * @param blockHashLookup the block hash lookup function
   * @return the evm executor
   */
  public EVMExecutor blockHashLookup(final BlockHashLookup blockHashLookup) {
    this.blockHashLookup = blockHashLookup;
    return this;
  }

  /**
   * Sets Version Hashes for blobs. The blobs themselves are not accessible.
   *
   * @param versionedHashes the versioned hashes
   * @return the evm executor
   */
  public EVMExecutor versionedHashes(final Optional<List<VersionedHash>> versionedHashes) {
    this.versionedHashes = versionedHashes;
    return this;
  }

  /**
   * Sets Operation Tracer.
   *
   * @param tracer the tracer
   * @return the evm executor
   */
  public EVMExecutor tracer(final OperationTracer tracer) {
    this.tracer = tracer;
    return this;
  }

  /**
   * Sets Precompile contract registry.
   *
   * @param precompileContractRegistry the precompile contract registry
   * @return the evm executor
   */
  public EVMExecutor precompileContractRegistry(
      final PrecompileContractRegistry precompileContractRegistry) {
    this.precompileContractRegistry = precompileContractRegistry;
    return this;
  }

  /**
   * Sets Require deposit.
   *
   * @param requireDeposit the require deposit
   * @return the evm executor
   */
  public EVMExecutor requireDeposit(final boolean requireDeposit) {
    this.requireDeposit = requireDeposit;
    return this;
  }

  /**
   * Sets Initial nonce.
   *
   * @param initialNonce the initial nonce
   * @return the evm executor
   */
  public EVMExecutor initialNonce(final long initialNonce) {
    this.initialNonce = initialNonce;
    return this;
  }

  /**
   * Sets Contract validation rules.
   *
   * @param contractValidationRules the contract validation rules
   * @return the evm executor
   */
  public EVMExecutor contractValidationRules(
      final List<ContractValidationRule> contractValidationRules) {
    this.contractValidationRules = contractValidationRules;
    return this;
  }

  /**
   * List of EIP-718 contracts that require special delete handling. By default, this is only the
   * RIPEMD precompile contract.
   *
   * @param forceCommitAddresses collection of addresses for special handling
   * @return fluent executor
   * @see <a
   *     href="https://github.com/ethereum/EIPs/issues/716">https://github.com/ethereum/EIPs/issues/716</a>
   */
  public EVMExecutor forceCommitAddresses(final Collection<Address> forceCommitAddresses) {
    this.forceCommitAddresses = forceCommitAddresses;
    return this;
  }

  /**
   * Sets Access list warm addresses.
   *
   * @param accessListWarmAddresses the access list warm addresses
   * @return the evm executor
   */
  public EVMExecutor accessListWarmAddresses(final Set<Address> accessListWarmAddresses) {
    this.accessListWarmAddresses = accessListWarmAddresses;
    return this;
  }

  /**
   * Sets Warm addresses.
   *
   * @param addresses the addresses
   * @return the evm executor
   */
  public EVMExecutor warmAddress(final Address... addresses) {
    this.accessListWarmAddresses.addAll(List.of(addresses));
    return this;
  }

  /**
   * Sets Access list warm storage map.
   *
   * @param accessListWarmStorage the access list warm storage
   * @return the evm executor
   */
  public EVMExecutor accessListWarmStorage(final Multimap<Address, Bytes32> accessListWarmStorage) {
    this.accessListWarmStorage = accessListWarmStorage;
    return this;
  }

  /**
   * Sets Access list warm storage.
   *
   * @param address the address
   * @param slots the slots
   * @return the evm executor
   */
  public EVMExecutor accessListWarmStorage(final Address address, final Bytes32... slots) {
    this.accessListWarmStorage.putAll(address, List.of(slots));
    return this;
  }

  /**
   * Sets Message call processor.
   *
   * @param messageCallProcessor the message call processor
   * @return the evm executor
   */
  public EVMExecutor messageCallProcessor(final MessageCallProcessor messageCallProcessor) {
    this.messageCallProcessor = messageCallProcessor;
    return this;
  }

  /**
   * Sets Contract call processor.
   *
   * @param contractCreationProcessor the contract creation processor
   * @return the evm executor
   */
  public EVMExecutor contractCallProcessor(
      final ContractCreationProcessor contractCreationProcessor) {
    this.contractCreationProcessor = contractCreationProcessor;
    return this;
  }

  /**
   * Sets the message frame type
   *
   * @param messageFrameType message frame type
   * @return the builder
   */
  public EVMExecutor messageFrameType(final MessageFrame.Type messageFrameType) {
    this.messageFrameType = messageFrameType;
    return this;
  }

  /**
   * Returns the EVM version this executor is using
   *
   * @return the current EVM version
   */
  public EvmSpecVersion getEVMVersion() {
    return evm.getEvmVersion();
  }

  /**
   * Returns the ChainID this executor is using
   *
   * @return the current chain ID
   */
  public Optional<Bytes> getChainId() {
    return evm.getChainId();
  }
}
