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

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalInt;

public class ClassicProtocolSpecs {

  public static ProtocolSpecBuilder<Void> classicRecoveryInitDefinition(
      final OptionalInt contractSizeLimit, final OptionalInt configStackSizeLimit) {
    return MainnetProtocolSpecs.homesteadDefinition(contractSizeLimit, configStackSizeLimit)
        .blockHeaderValidatorBuilder(MainnetBlockHeaderValidator::createClassicValidator)
        .name("ClassicRecoveryInit");
  }

  public static ProtocolSpecBuilder<Void> tangerineWhistleDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt contractSizeLimit,
      final OptionalInt configStackSizeLimit) {
    return MainnetProtocolSpecs.homesteadDefinition(contractSizeLimit, configStackSizeLimit)
        .gasCalculator(TangerineWhistleGasCalculator::new)
        .transactionValidatorBuilder(
            gasCalculator -> new MainnetTransactionValidator(gasCalculator, true, chainId))
        .name("ClassicTangerineWhistle");
  }

  public static ProtocolSpecBuilder<Void> dieHardDefinition(
      final Optional<BigInteger> chainId,
      final OptionalInt configContractSizeLimit,
      final OptionalInt configStackSizeLimit) {
    return tangerineWhistleDefinition(chainId, OptionalInt.empty(), configStackSizeLimit)
        .gasCalculator(DieHardGasCalculator::new)
        .difficultyCalculator(ClassicDifficultyCalculators.DIFFICULTY_BOMB_PAUSED)
        .name("DieHard");
  }
}
