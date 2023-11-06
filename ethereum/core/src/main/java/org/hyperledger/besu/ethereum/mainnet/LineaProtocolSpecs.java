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
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalInt;

public class LineaProtocolSpecs {
  private LineaProtocolSpecs() {}

  static ProtocolSpecBuilder lineaOpCodesDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit,
      final boolean enableRevertReason,
      final GenesisConfigOptions genesisConfigOptions,
      final EvmConfiguration evmConfiguration) {

    return MainnetProtocolSpecs.londonDefinition(
            chainId,
            configContractSizeLimit,
            configStackSizeLimit,
            enableRevertReason,
            genesisConfigOptions,
            evmConfiguration)
        // some Linea evm opcodes behave differently.
        .evmBuilder(
            (gasCalculator, jdCacheConfig) ->
                MainnetEVMs.linea(
                    gasCalculator, chainId.orElse(BigInteger.ZERO), evmConfiguration));
  }
}
