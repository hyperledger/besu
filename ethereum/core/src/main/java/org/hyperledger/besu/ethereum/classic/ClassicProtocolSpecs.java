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
package org.hyperledger.besu.ethereum.classic;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.mainnet.IstanbulGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetContractCreationProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetMessageCallProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetPrecompiledContractRegistries;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.mainnet.SpuriousDragonGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;

import com.google.common.collect.ImmutableSet;

public class ClassicProtocolSpecs {

  public static final int SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT = 24576;

  private static final Address RIPEMD160_PRECOMPILE =
      Address.fromHexString("0x0000000000000000000000000000000000000003");

  // A consensus bug at Ethereum mainnet transaction 0xcf416c53
  // deleted an empty account even when the message execution scope
  // failed, but the transaction itself succeeded.
  private static final ImmutableSet<Address> SPURIOUS_DRAGON_FORCE_DELETE_WHEN_EMPTY_ADDRESSES =
      ImmutableSet.of(RIPEMD160_PRECOMPILE);

  // TODO edwardmack, this is just a place holder definiton, REPLACE with real definition
  public static ProtocolSpecBuilder<Void> atlantisDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit) {
    final int contractSizeLimit =
        configContractSizeLimit.orElse(SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT);
    final int stackSizeLimit = configStackSizeLimit.orElse(MessageFrame.DEFAULT_MAX_STACK_SIZE);

    return MainnetProtocolSpecs.tangerineWhistleDefinition(
            OptionalInt.empty(), configStackSizeLimit)
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
                    Account.DEFAULT_VERSION))
        .name("Atlantis");
  }

  // TODO edwardmack, this is just a place holder definiton, REPLACE with real definition
  public static ProtocolSpecBuilder<Void> aghartaDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    final int contractSizeLimit =
        configContractSizeLimit.orElse(SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT);
    return MainnetProtocolSpecs.constantinopleFixDefinition(
            chainId, configContractSizeLimit, configStackSizeLimit, enableRevertReason)
        .gasCalculator(IstanbulGasCalculator::new)
        //                .evmBuilder(
        //                        gasCalculator ->
        //                                MainnetEvmRegistries.istanbul(gasCalculator,
        // chainId.orElse(BigInteger.ZERO)))
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
        .name("Agharta");
  }
}
