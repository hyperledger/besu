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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.apache.tuweni.units.bigints.UInt256;

/** The interface Genesis config options. */
public interface GenesisConfigOptions {

  /**
   * Is eth hash boolean.
   *
   * @return the boolean
   */
  boolean isEthHash();

  /**
   * Is ibft legacy boolean (NOTE this is a deprecated feature).
   *
   * @return the boolean
   */
  boolean isIbftLegacy();

  /**
   * Is ibft 2 boolean.
   *
   * @return the boolean
   */
  boolean isIbft2();

  /**
   * Is qbft boolean.
   *
   * @return the boolean
   */
  boolean isQbft();

  /**
   * Is clique boolean.
   *
   * @return the boolean
   */
  boolean isClique();

  /**
   * Is a Proof of Authority network.
   *
   * @return the boolean
   */
  boolean isPoa();

  /**
   * Is consensus migration boolean.
   *
   * @return the boolean
   */
  default boolean isConsensusMigration() {
    return isIbft2() && isQbft();
  }

  /**
   * Gets consensus engine.
   *
   * @return the consensus engine
   */
  String getConsensusEngine();

  /**
   * Gets checkpoint options.
   *
   * @return the checkpoint options
   */
  CheckpointConfigOptions getCheckpointOptions();

  /**
   * Gets clique config options.
   *
   * @return the clique config options
   */
  CliqueConfigOptions getCliqueConfigOptions();

  /**
   * Gets bft config options.
   *
   * @return the bft config options
   */
  BftConfigOptions getBftConfigOptions();

  /**
   * Gets qbft config options.
   *
   * @return the qbft config options
   */
  QbftConfigOptions getQbftConfigOptions();

  /**
   * Gets discovery options.
   *
   * @return the discovery options
   */
  DiscoveryOptions getDiscoveryOptions();

  /**
   * Gets ethash config options.
   *
   * @return the ethash config options
   */
  EthashConfigOptions getEthashConfigOptions();

  /**
   * Gets homestead block number.
   *
   * @return the homestead block number
   */
  OptionalLong getHomesteadBlockNumber();

  /**
   * Gets dao fork block.
   *
   * @return the dao fork block
   */
  OptionalLong getDaoForkBlock();

  /**
   * Gets tangerine whistle block number.
   *
   * @return the tangerine whistle block number
   */
  OptionalLong getTangerineWhistleBlockNumber();

  /**
   * Gets spurious dragon block number.
   *
   * @return the spurious dragon block number
   */
  OptionalLong getSpuriousDragonBlockNumber();

  /**
   * Gets byzantium block number.
   *
   * @return the byzantium block number
   */
  OptionalLong getByzantiumBlockNumber();

  /**
   * Gets constantinople block number.
   *
   * @return the constantinople block number
   */
  OptionalLong getConstantinopleBlockNumber();

  /**
   * Gets petersburg block number.
   *
   * @return the petersburg block number
   */
  OptionalLong getPetersburgBlockNumber();

  /**
   * Gets istanbul block number.
   *
   * @return the istanbul block number
   */
  OptionalLong getIstanbulBlockNumber();

  /**
   * Gets muir glacier block number.
   *
   * @return the muir glacier block number
   */
  OptionalLong getMuirGlacierBlockNumber();

  /**
   * Gets berlin block number.
   *
   * @return the berlin block number
   */
  OptionalLong getBerlinBlockNumber();

  /**
   * Gets london block number.
   *
   * @return the london block number
   */
  OptionalLong getLondonBlockNumber();

  /**
   * Gets arrow glacier block number.
   *
   * @return the arrow glacier block number
   */
  OptionalLong getArrowGlacierBlockNumber();

  /**
   * Gets gray glacier block number.
   *
   * @return the gray glacier block number
   */
  OptionalLong getGrayGlacierBlockNumber();

  /**
   * Gets merge net split block number.
   *
   * @return the merge net split block number
   */
  OptionalLong getMergeNetSplitBlockNumber();

  /**
   * Gets shanghai time.
   *
   * @return the shanghai time
   */
  OptionalLong getShanghaiTime();

  /**
   * Gets cancun time.
   *
   * @return the cancun time
   */
  OptionalLong getCancunTime();

  /**
   * Gets prague time.
   *
   * @return the prague time
   */
  OptionalLong getPragueTime();

  /**
   * Gets future eips time.
   *
   * @return the future eips time
   */
  OptionalLong getFutureEipsTime();

  /**
   * Gets experimental eips time.
   *
   * @return the experimental eips time
   */
  OptionalLong getExperimentalEipsTime();

  /**
   * Gets base fee per gas.
   *
   * @return the base fee per gas
   */
  Optional<Wei> getBaseFeePerGas();

  /**
   * Gets terminal total difficulty.
   *
   * @return the terminal total difficulty
   */
  Optional<UInt256> getTerminalTotalDifficulty();

  /**
   * Gets terminal block number.
   *
   * @return the terminal block number
   */
  OptionalLong getTerminalBlockNumber();

  /**
   * Gets terminal block hash.
   *
   * @return the terminal block hash
   */
  Optional<Hash> getTerminalBlockHash();

  /**
   * Gets fork block numbers.
   *
   * @return the fork block numbers
   */
  List<Long> getForkBlockNumbers();

  /**
   * Gets fork block timestamps.
   *
   * @return the fork block timestamps
   */
  List<Long> getForkBlockTimestamps();

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
   * @return block number to activate ECIP-1015 code
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1015">https://ecips.ethereumclassic.org/ECIPs/ecip-1015</a>
   */
  OptionalLong getEcip1015BlockNumber();

  /**
   * Block number for DieHard fork on Classic network The DieHard fork includes changes to meet
   * specification for ECIP-1010 and EIP-160 Note, this fork happens after ECIP-1015 (classic
   * tangerine whistle) and before Gotham fork ECIP-1010: Delay Difficulty Bomb Explosion
   *
   * @return block number to activate Classic DieHard fork
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1010">https://ecips.ethereumclassic.org/ECIPs/ecip-1010</a>
   *     EIP-160: EXP cost increase
   * @see <a
   *     href="https://eips.ethereum.org/EIPS/eip-160">https://eips.ethereum.org/EIPS/eip-160</a>
   */
  OptionalLong getDieHardBlockNumber();

  /**
   * Block number for Gotham fork on Classic network, the Gotham form includes changes to meet
   * specification for ECIP-1017 and ECIP-1039 both regarding Monetary Policy (rewards).
   *
   * @return block to activate Classic Gotham fork
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1017">https://ecips.ethereumclassic.org/ECIPs/ecip-1017</a>
   *     ECIP-1017: Monetary Policy and Final Modification to the Ethereum Classic Emission Schedule
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1039">https://ecips.ethereumclassic.org/ECIPs/ecip-1039</a>
   *     ECIP-1039: Monetary policy rounding specification
   */
  OptionalLong getGothamBlockNumber();

  /**
   * Block number to remove difficulty bomb, to meet specification for ECIP-1041.
   *
   * @return block number to remove difficulty bomb on classic network
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1041">https://ecips.ethereumclassic.org/ECIPs/ecip-1041</a>
   *     ECIP-1041: Remove Difficulty Bomb
   */
  OptionalLong getDefuseDifficultyBombBlockNumber();

  /**
   * Block number for Atlantis fork on Classic network Note, this fork happen after Defuse
   * Difficulty Bomb fork and before Agharta fork ECIP-1054: Atlantis EVM and Protocol Upgrades
   * Enable the outstanding Ethereum Foundation Spurious Dragon and Byzantium network protocol
   * upgrades for the Ethereum Classic network.
   *
   * @return block number for Atlantis fork on Classic network
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1054">https://ecips.ethereumclassic.org/ECIPs/ecip-1054</a>
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1054">https://ecips.ethereumclassic.org/ECIPs/ecip-1054</a>
   */
  OptionalLong getAtlantisBlockNumber();

  /**
   * Block number for Agharta fork on Classic network. Enable the outstanding Ethereum Foundation
   * Constaninople and Petersburg network protocol upgrades on the Ethereum Classic network in a
   * hard-fork code-named Agharta to enable maximum compatibility across these networks.
   *
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
   * Block number to activate Spiral on Classic networks.
   *
   * @return block number of Spiral fork on Classic networks
   * @see <a
   *     href="https://ecips.ethereumclassic.org/ECIPs/ecip-1109">https://ecips.ethereumclassic.org/ECIPs/ecip-1109</a>
   */
  OptionalLong getSpiralBlockNumber();

  /**
   * Gets chain id.
   *
   * @return the chain id
   */
  Optional<BigInteger> getChainId();

  /**
   * Gets contract size limit.
   *
   * @return the contract size limit
   */
  OptionalInt getContractSizeLimit();

  /**
   * Gets evm stack size.
   *
   * @return the evm stack size
   */
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

  /**
   * As map map.
   *
   * @return the map
   */
  Map<String, Object> asMap();

  /**
   * Gets transitions.
   *
   * @return the transitions
   */
  TransitionsConfigOptions getTransitions();

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

  /**
   * Set a Zero Base Fee network so that free gas can be used with London/EIP-1559. Once the chain
   * has a zero base fee, you cannot go back to a non-zero base fee.
   *
   * @return true, if you want the next block to use zero for the base fee.
   */
  boolean isZeroBaseFee();

  /**
   * The deposit contract address that should be in the logger field in Receipt of Deposit
   * transaction
   *
   * @return the deposit address
   */
  Optional<Address> getDepositContractAddress();
}
