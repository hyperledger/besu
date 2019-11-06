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
package org.hyperledger.besu.crosschain.ethereum.privatenet.precompiles;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.mainnet.MainnetPrecompiledContractRegistries;
import org.hyperledger.besu.ethereum.mainnet.PrecompileContractRegistry;
import org.hyperledger.besu.ethereum.mainnet.PrecompiledContractConfiguration;

public class CrosschainPrecompiledContractRegistries {
  public static PrecompileContractRegistry crosschainPrecompiles(
      final PrecompiledContractConfiguration precompiledContractConfiguration) {
    final PrecompileContractRegistry registry =
        MainnetPrecompiledContractRegistries.istanbul(precompiledContractConfiguration);
    registry.put(
        Address.CROSSCHAIN_SUBTRANS,
        Account.DEFAULT_VERSION,
        new CrossChainSubTransPrecompiledContract(
            precompiledContractConfiguration.getGasCalculator()));
    registry.put(
        Address.CROSSCHAIN_SUBVIEW,
        Account.DEFAULT_VERSION,
        new CrossChainSubViewPrecompiledContract(
            precompiledContractConfiguration.getGasCalculator()));
    registry.put(
        Address.CROSSCHAIN_GETINFO,
        Account.DEFAULT_VERSION,
        new CrosschainGetInfoPrecompiledContract(
            precompiledContractConfiguration.getGasCalculator()));
    return registry;
  }
}
