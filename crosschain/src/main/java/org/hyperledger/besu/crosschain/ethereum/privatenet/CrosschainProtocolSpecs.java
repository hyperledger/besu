/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.ethereum.privatenet;

import org.hyperledger.besu.crosschain.ethereum.crosschain.CrosschainTransactionProcessor;
import org.hyperledger.besu.crosschain.ethereum.privatenet.precompiles.CrosschainPrecompiledContractRegistries;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalInt;

public class CrosschainProtocolSpecs {
  public static ProtocolSpecBuilder<Void> crossChainDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason) {
    final int stackSizeLimit = configStackSizeLimit.orElse(MessageFrame.DEFAULT_MAX_STACK_SIZE);
    return MainnetProtocolSpecs.istanbulDefinition(
            chainId, contractSizeLimit, configStackSizeLimit, enableRevertReason)
        .transactionProcessorBuilder(
            (gasCalculator,
                transactionValidator,
                contractCreationProcessor,
                messageCallProcessor) ->
                new CrosschainTransactionProcessor(
                    gasCalculator,
                    transactionValidator,
                    contractCreationProcessor,
                    messageCallProcessor,
                    true,
                    stackSizeLimit,
                    Account.DEFAULT_VERSION))
        .precompileContractRegistryBuilder(
            CrosschainPrecompiledContractRegistries::crosschainPrecompiles)
        .name("CrossChain");
  }
}
