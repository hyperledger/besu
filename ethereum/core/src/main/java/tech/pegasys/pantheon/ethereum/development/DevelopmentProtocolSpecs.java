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
package tech.pegasys.pantheon.ethereum.development;

import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;

/**
 * Provides a protocol specification which is suitable for use on private, PoW networks, where block
 * mining is performed on CPUs alone.
 */
public class DevelopmentProtocolSpecs {

  /*
   * The DevelopmentProtocolSpecification is the same as the byzantium spec, but with a much reduced
   * difficulty calculator (to support CPU mining).
   */
  public static ProtocolSpec<Void> first(
      final Integer chainId, final ProtocolSchedule<Void> protocolSchedule) {
    return MainnetProtocolSpecs.byzantiumDefinition(chainId)
        .difficultyCalculator(DevelopmentDifficultyCalculators.DEVELOPER)
        .name("first")
        .build(protocolSchedule);
  }
}
