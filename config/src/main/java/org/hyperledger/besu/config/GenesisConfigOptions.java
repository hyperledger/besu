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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.apache.tuweni.units.bigints.UInt256;

public interface GenesisConfigOptions {

  boolean isEthHash();

  boolean isKeccak256();

  boolean isIbftLegacy();

  boolean isIbft2();

  boolean isQbft();

  boolean isClique();

  default boolean isConsensusMigration() {
    return (isIbft2() || isIbftLegacy()) && isQbft();
  }

  String getConsensusEngine();

  IbftLegacyConfigOptions getIbftLegacyConfigOptions();

  CheckpointConfigOptions getCheckpointOptions();

  CliqueConfigOptions getCliqueConfigOptions();

  BftConfigOptions getBftConfigOptions();

  QbftConfigOptions getQbftConfigOptions();

  DiscoveryOptions getDiscoveryOptions();

  EthashConfigOptions getEthashConfigOptions();

  Keccak256ConfigOptions getKeccak256ConfigOptions();

  OptionalLong getHomesteadBlockNumber();

  OptionalLong getDaoForkBlock();

  OptionalLong getTangerineWhistleBlockNumber();

  OptionalLong getSpuriousDragonBlockNumber();

  OptionalLong getByzantiumBlockNumber();

  OptionalLong getConstantinopleBlockNumber();

  OptionalLong getPetersburgBlockNumber();

  OptionalLong getIstanbulBlockNumber();

  OptionalLong getMuirGlacierBlockNumber();

  OptionalLong getBerlinBlockNumber();

  OptionalLong getLondonBlockNumber();

  OptionalLong getArrowGlacierBlockNumber();

  OptionalLong getGrayGlacierBlockNumber();

  OptionalLong getMergeNetSplitBlockNumber();

  Optional<Wei> getBaseFeePerGas();

  Optional<UInt256> getTerminalTotalDifficulty();

  OptionalLong getTerminalBlockNumber();

  Optional<Hash> getTerminalBlockHash();

  List<Long> getForks();

  /**
   * Block number for the Dao Fork, this value is used to tell node to connect with peer that did
   * NOT accept the Dao Fork and instead continued as what is now called the classic network
   *
   * @return block number to activate the classic fork block
   */
  OptionalLong getClassicForkBlock();

  /**
   * Block number for ECIP-1015 fork on Classic network ECIP-1015: Long-term gas cost changes for
   * IO-heavy operations to mitigate transaction spam attacks In reference to EIP-150 (ETH Tangerine
   * Whistle) Note, this fork happens after Homestead (Mainnet definition) and before DieHard fork
   *
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1015">https://ecips.ethereumclassic.org/ECIPs/ecip-1015</a>
   * @return block number to activate ECIP-1015 code
   */
  OptionalLong getEcip1015BlockNumber();

  /**
   * Block number for DieHard fork on Classic network The DieHard fork includes changes to meet
   * specification for ECIP-1010 and EIP-160 Note, this fork happens after ECIP-1015 (classic
   * tangerine whistle) and before Gotham fork ECIP-1010: Delay Difficulty Bomb Explosion
   *
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1010">https://ecips.ethereumclassic.org/ECIPs/ecip-1010</a>
   *     EIP-160: EXP cost increase
   * @see <a
   *     href="https://eips.ethereum.org/EIPS/eip-160">https://eips.ethereum.org/EIPS/eip-160</a>
   * @return block number to activate Classic DieHard fork
   */
  OptionalLong getDieHardBlockNumber();

  /**
   * Block number for Gotham fork on Classic network, the Gotham form includes changes to meet
   * specification for ECIP-1017 and ECIP-1039 both regarding Monetary Policy (rewards).
   *
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1017">https://ecips.ethereumclassic.org/ECIPs/ecip-1017</a>
   *     ECIP-1017: Monetary Policy and Final Modification to the Ethereum Classic Emission Schedule
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1039">https://ecips.ethereumclassic.org/ECIPs/ecip-1039</a>
   *     ECIP-1039: Monetary policy rounding specification
   * @return block to activate Classic Gotham fork
   */
  OptionalLong getGothamBlockNumber();

  /**
   * Block number to remove difficulty bomb, to meet specification for ECIP-1041.
   *
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1041">https://ecips.ethereumclassic.org/ECIPs/ecip-1041</a>
   *     ECIP-1041: Remove Difficulty Bomb
   * @return block number to remove difficulty bomb on classic network
   */
  OptionalLong getDefuseDifficultyBombBlockNumber();

  /**
   * Block number for Atlantis fork on Classic network Note, this fork happen after Defuse
   * Difficulty Bomb fork and before Agharta fork ECIP-1054: Atlantis EVM and Protocol Upgrades
   * Enable the outstanding Ethereum Foundation Spurious Dragon and Byzantium network protocol
   * upgrades for the Ethereum Classic network.
   *
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1054">https://ecips.ethereumclassic.org/ECIPs/ecip-1054</a>
   * @return block number for Atlantis fork on Classic network
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1054">https://ecips.ethereumclassic.org/ECIPs/ecip-1054</a>
   */
  OptionalLong getAtlantisBlockNumber();

  /**
   * Block number for Agharta fork on Classic network. Enable the outstanding Ethereum Foundation
   * Constaninople and Petersburg network protocol upgrades on the Ethereum Classic network in a
   * hard-fork code-named Agharta to enable maximum compatibility across these networks.
   *
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1056">https://ecips.ethereumclassic.org/ECIPs/ecip-1056</a>
   * @return block number for Agharta fork on Classic network
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1056">https://ecips.ethereumclassic.org/ECIPs/ecip-1056</a>
   */
  OptionalLong getAghartaBlockNumber();

  /**
   * Block number for Phoenix fork on Classic networks. Enable the outstanding Ethereum Foundation
   * Istanbul network protocol upgrades on the Ethereum Classic network in a hard-fork code-named
   * Phoenix to enable maximum compatibility across these networks.
   *
   * @return block number of Phoenix fork on Classic networks
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1088">https://ecips.ethereumclassic.org/ECIPs/ecip-1088</a>
   */
  OptionalLong getPhoenixBlockNumber();

  /**
   * Block number to activate ECIP-1099 (Thanos) on Classic networks. Doubles the length of the
   * Ethash epoch, with the impact being a reduced DAG size.
   *
   * @return block number of ECIP-1099 fork on Classic networks
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1099">https://ecips.ethereumclassic.org/ECIPs/ecip-1099</a>
   */
  OptionalLong getThanosBlockNumber();

  /**
   * Block number to activate Magneto on Classic networks.
   *
   * @return block number of Magneto fork on Classic networks
   * @see <a
   *     href="https://github.com/ethereumclassic/ECIPs/issues/424">https://github.com/ethereumclassic/ECIPs/issues/424</a>
   */
  OptionalLong getMagnetoBlockNumber();

  /**
   * Block number to activate Mystique on Classic networks.
   *
   * @return block number of Mystique fork on Classic networks
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1104">https://ecips.ethereumclassic.org/ECIPs/ecip-1104</a>
   */
  OptionalLong getMystiqueBlockNumber();

  /**
   * Block number to activate ECIP-1049 on Classic networks. Changes the hashing algorithm to
   * keccak-256.
   *
   * @return block number of ECIP-1049 fork on Classic networks
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1049">https://ecips.ethereumclassic.org/ECIPs/ecip-1049</a>
   */
  OptionalLong getEcip1049BlockNumber();

  Optional<BigInteger> getChainId();

  OptionalInt getContractSizeLimit();

  OptionalInt getEvmStackSize();

  /**
   * Number of rounds contained within an Era for calculating Ethereum Classic Emission Schedule,
   * ECIP defines this as 5,000,000 however this config option allows for adjusting (for using with
   * other networks, for example Mordor testnet uses 2,000,000). The values defaults to 5,000,000 if
   * not set.
   *
   * @return number of rounds pre Era
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1017">https://ecips.ethereumclassic.org/ECIPs/ecip-1017</a>
   */
  OptionalLong getEcip1017EraRounds();

  Map<String, Object> asMap();

  TransitionsConfigOptions getTransitions();

  /**
   * Set Besu in Quorum-compatibility mode
   *
   * @return true, if Besu is running on Quorum-compatibility mode, false, otherwise.
   */
  boolean isQuorum();

  /**
   * Block number to activate Quorum Permissioning. This option is used on Quorum-compatibility
   * mode.
   *
   * @return block number to activate Quorum Permissioning
   */
  OptionalLong getQip714BlockNumber();

  /**
   * The PoW algorithm associated with the genesis file.
   *
   * @return the PoW algorithm in use.
   */
  PowAlgorithm getPowAlgorithm();

  /**
   * The elliptic curve which should be used in SignatureAlgorithm.
   *
   * @return the name of the elliptic curve.
   */
  Optional<String> getEcCurve();
}
