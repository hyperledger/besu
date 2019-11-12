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
package org.hyperledger.besu.config;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public interface GenesisConfigOptions {

  boolean isEthHash();

  boolean isIbftLegacy();

  boolean isIbft2();

  boolean isClique();

  String getConsensusEngine();

  IbftConfigOptions getIbftLegacyConfigOptions();

  CliqueConfigOptions getCliqueConfigOptions();

  IbftConfigOptions getIbft2ConfigOptions();

  EthashConfigOptions getEthashConfigOptions();

  OptionalLong getHomesteadBlockNumber();

  OptionalLong getDaoForkBlock();

  OptionalLong getTangerineWhistleBlockNumber();

  OptionalLong getSpuriousDragonBlockNumber();

  OptionalLong getByzantiumBlockNumber();

  OptionalLong getConstantinopleBlockNumber();

  OptionalLong getConstantinopleFixBlockNumber();

  OptionalLong getIstanbulBlockNumber();

  /**
   * Block number for the Dao Fork, this value is used to tell node to connect with peer that did
   * NOT accept the Dao Fork and instead continued as what is now called the classic network
   *
   * @return block number to activate the classic fork block
   */
  OptionalLong getClassicForkBlock();

  Optional<BigInteger> getChainId();

  OptionalInt getContractSizeLimit();

  OptionalInt getEvmStackSize();

  Map<String, Object> asMap();

  CustomForksConfigOptions getCustomForks();
}
