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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.units.bigints.UInt256;

/** The Stub genesis config options. */
public class StubGenesisConfigOptions implements GenesisConfigOptions, Cloneable {

  private OptionalLong homesteadBlockNumber = OptionalLong.empty();
  private OptionalLong daoForkBlock = OptionalLong.empty();
  private OptionalLong tangerineWhistleBlockNumber = OptionalLong.empty();
  private OptionalLong spuriousDragonBlockNumber = OptionalLong.empty();
  private OptionalLong byzantiumBlockNumber = OptionalLong.empty();
  private OptionalLong constantinopleBlockNumber = OptionalLong.empty();
  private OptionalLong petersburgBlockNumber = OptionalLong.empty();
  private OptionalLong istanbulBlockNumber = OptionalLong.empty();
  private OptionalLong muirGlacierBlockNumber = OptionalLong.empty();
  private OptionalLong berlinBlockNumber = OptionalLong.empty();
  private OptionalLong londonBlockNumber = OptionalLong.empty();
  private OptionalLong arrowGlacierBlockNumber = OptionalLong.empty();
  private OptionalLong grayGlacierBlockNumber = OptionalLong.empty();
  private OptionalLong mergeNetSplitBlockNumber = OptionalLong.empty();
  private OptionalLong shanghaiTime = OptionalLong.empty();
  private OptionalLong cancunTime = OptionalLong.empty();
  private OptionalLong pragueTime = OptionalLong.empty();
  private OptionalLong futureEipsTime = OptionalLong.empty();
  private OptionalLong experimentalEipsTime = OptionalLong.empty();
  private OptionalLong terminalBlockNumber = OptionalLong.empty();
  private Optional<Hash> terminalBlockHash = Optional.empty();
  private Optional<UInt256> terminalTotalDifficulty = Optional.empty();

  private Optional<Wei> baseFeePerGas = Optional.empty();
  private OptionalLong classicForkBlock = OptionalLong.empty();
  private OptionalLong ecip1015BlockNumber = OptionalLong.empty();
  private OptionalLong diehardBlockNumber = OptionalLong.empty();
  private OptionalLong gothamBlockNumber = OptionalLong.empty();
  private OptionalLong defuseDifficultyBombBlockNumber = OptionalLong.empty();
  private OptionalLong atlantisBlockNumber = OptionalLong.empty();
  private OptionalLong aghartaBlockNumber = OptionalLong.empty();
  private OptionalLong phoenixBlockNumber = OptionalLong.empty();
  private OptionalLong thanosBlockNumber = OptionalLong.empty();
  private OptionalLong magnetoBlockNumber = OptionalLong.empty();
  private OptionalLong mystiqueBlockNumber = OptionalLong.empty();
  private OptionalLong spiralBlockNumber = OptionalLong.empty();
  private Optional<BigInteger> chainId = Optional.empty();
  private OptionalInt contractSizeLimit = OptionalInt.empty();
  private OptionalInt stackSizeLimit = OptionalInt.empty();
  private final OptionalLong ecip1017EraRounds = OptionalLong.empty();
  private Optional<String> ecCurve = Optional.empty();
  private QbftConfigOptions qbftConfigOptions = JsonQbftConfigOptions.DEFAULT;
  private BftConfigOptions bftConfigOptions = JsonBftConfigOptions.DEFAULT;
  private TransitionsConfigOptions transitions = TransitionsConfigOptions.DEFAULT;
  private static final DiscoveryOptions DISCOVERY_OPTIONS = DiscoveryOptions.DEFAULT;
  private boolean zeroBaseFee = false;

  @Override
  public StubGenesisConfigOptions clone() {
    try {
      return (StubGenesisConfigOptions) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getConsensusEngine() {
    return "ethash";
  }

  @Override
  public boolean isEthHash() {
    return true;
  }

  @Override
  public boolean isIbftLegacy() {
    return false;
  }

  @Override
  public boolean isClique() {
    return false;
  }

  @Override
  public boolean isIbft2() {
    return false;
  }

  @Override
  public boolean isQbft() {
    return false;
  }

  @Override
  public boolean isPoa() {
    return false;
  }

  @Override
  public CheckpointConfigOptions getCheckpointOptions() {
    return CheckpointConfigOptions.DEFAULT;
  }

  @Override
  public CliqueConfigOptions getCliqueConfigOptions() {
    return CliqueConfigOptions.DEFAULT;
  }

  @Override
  public BftConfigOptions getBftConfigOptions() {
    return bftConfigOptions;
  }

  @Override
  public QbftConfigOptions getQbftConfigOptions() {
    return qbftConfigOptions;
  }

  @Override
  public DiscoveryOptions getDiscoveryOptions() {
    return DISCOVERY_OPTIONS;
  }

  @Override
  public EthashConfigOptions getEthashConfigOptions() {
    return EthashConfigOptions.DEFAULT;
  }

  @Override
  public OptionalLong getHomesteadBlockNumber() {
    return homesteadBlockNumber;
  }

  @Override
  public OptionalLong getDaoForkBlock() {
    return daoForkBlock;
  }

  @Override
  public OptionalLong getTangerineWhistleBlockNumber() {
    return tangerineWhistleBlockNumber;
  }

  @Override
  public OptionalLong getSpuriousDragonBlockNumber() {
    return spuriousDragonBlockNumber;
  }

  @Override
  public OptionalLong getByzantiumBlockNumber() {
    return byzantiumBlockNumber;
  }

  @Override
  public OptionalLong getConstantinopleBlockNumber() {
    return constantinopleBlockNumber;
  }

  @Override
  public OptionalLong getPetersburgBlockNumber() {
    return petersburgBlockNumber;
  }

  @Override
  public OptionalLong getIstanbulBlockNumber() {
    return istanbulBlockNumber;
  }

  @Override
  public OptionalLong getMuirGlacierBlockNumber() {
    return muirGlacierBlockNumber;
  }

  @Override
  public OptionalLong getBerlinBlockNumber() {
    return berlinBlockNumber;
  }

  @Override
  public OptionalLong getLondonBlockNumber() {
    return londonBlockNumber;
  }

  @Override
  public OptionalLong getArrowGlacierBlockNumber() {
    return arrowGlacierBlockNumber;
  }

  @Override
  public OptionalLong getGrayGlacierBlockNumber() {
    return grayGlacierBlockNumber;
  }

  @Override
  public OptionalLong getMergeNetSplitBlockNumber() {
    return mergeNetSplitBlockNumber;
  }

  @Override
  public OptionalLong getShanghaiTime() {
    return shanghaiTime;
  }

  @Override
  public OptionalLong getCancunTime() {
    return cancunTime;
  }

  @Override
  public OptionalLong getPragueTime() {
    return pragueTime;
  }

  @Override
  public OptionalLong getFutureEipsTime() {
    return futureEipsTime;
  }

  @Override
  public OptionalLong getExperimentalEipsTime() {
    return experimentalEipsTime;
  }

  @Override
  public Optional<Wei> getBaseFeePerGas() {
    return baseFeePerGas;
  }

  @Override
  public Optional<UInt256> getTerminalTotalDifficulty() {
    return terminalTotalDifficulty;
  }

  @Override
  public OptionalLong getTerminalBlockNumber() {
    return terminalBlockNumber;
  }

  @Override
  public Optional<Hash> getTerminalBlockHash() {
    return terminalBlockHash;
  }

  @Override
  public OptionalLong getClassicForkBlock() {
    return classicForkBlock;
  }

  @Override
  public OptionalLong getEcip1015BlockNumber() {
    return ecip1015BlockNumber;
  }

  @Override
  public OptionalLong getDieHardBlockNumber() {
    return diehardBlockNumber;
  }

  @Override
  public OptionalLong getGothamBlockNumber() {
    return gothamBlockNumber;
  }

  @Override
  public OptionalLong getDefuseDifficultyBombBlockNumber() {
    return defuseDifficultyBombBlockNumber;
  }

  @Override
  public OptionalLong getAtlantisBlockNumber() {
    return atlantisBlockNumber;
  }

  @Override
  public OptionalLong getAghartaBlockNumber() {
    return aghartaBlockNumber;
  }

  @Override
  public OptionalLong getPhoenixBlockNumber() {
    return phoenixBlockNumber;
  }

  @Override
  public OptionalLong getThanosBlockNumber() {
    return thanosBlockNumber;
  }

  @Override
  public OptionalLong getMagnetoBlockNumber() {
    return magnetoBlockNumber;
  }

  @Override
  public OptionalLong getMystiqueBlockNumber() {
    return mystiqueBlockNumber;
  }

  @Override
  public OptionalLong getSpiralBlockNumber() {
    return spiralBlockNumber;
  }

  @Override
  public OptionalInt getContractSizeLimit() {
    return contractSizeLimit;
  }

  @Override
  public OptionalInt getEvmStackSize() {
    return stackSizeLimit;
  }

  @Override
  public OptionalLong getEcip1017EraRounds() {
    return ecip1017EraRounds;
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return chainId;
  }

  @Override
  public Map<String, Object> asMap() {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    getChainId().ifPresent(id -> builder.put("chainId", id));

    // mainnet fork blocks
    getHomesteadBlockNumber().ifPresent(l -> builder.put("homesteadBlock", l));
    getDaoForkBlock().ifPresent(l -> builder.put("daoForkBlock", l));
    getTangerineWhistleBlockNumber().ifPresent(l -> builder.put("eip150Block", l));
    getSpuriousDragonBlockNumber().ifPresent(l -> builder.put("eip158Block", l));
    getByzantiumBlockNumber().ifPresent(l -> builder.put("byzantiumBlock", l));
    getConstantinopleBlockNumber().ifPresent(l -> builder.put("constantinopleBlock", l));
    getPetersburgBlockNumber().ifPresent(l -> builder.put("petersburgBlock", l));
    getIstanbulBlockNumber().ifPresent(l -> builder.put("istanbulBlock", l));
    getMuirGlacierBlockNumber().ifPresent(l -> builder.put("muirGlacierBlock", l));
    getBerlinBlockNumber().ifPresent(l -> builder.put("berlinBlock", l));
    getLondonBlockNumber().ifPresent(l -> builder.put("londonBlock", l));
    getArrowGlacierBlockNumber().ifPresent(l -> builder.put("arrowGlacierBlock", l));
    getGrayGlacierBlockNumber().ifPresent(l -> builder.put("grayGlacierBlock", l));
    getMergeNetSplitBlockNumber().ifPresent(l -> builder.put("mergeNetSplitBlock", l));
    getShanghaiTime().ifPresent(l -> builder.put("shanghaiTime", l));
    getCancunTime().ifPresent(l -> builder.put("cancunTime", l));
    getPragueTime().ifPresent(l -> builder.put("pragueTime", l));
    getFutureEipsTime().ifPresent(l -> builder.put("futureEipsTime", l));
    getExperimentalEipsTime().ifPresent(l -> builder.put("experimentalEipsTime", l));
    getTerminalBlockNumber().ifPresent(l -> builder.put("terminalBlockNumber", l));
    getTerminalBlockHash().ifPresent(h -> builder.put("terminalBlockHash", h));
    // classic fork blocks
    getClassicForkBlock().ifPresent(l -> builder.put("classicForkBlock", l));
    getEcip1015BlockNumber().ifPresent(l -> builder.put("ecip1015Block", l));
    getDieHardBlockNumber().ifPresent(l -> builder.put("dieHardBlock", l));
    getGothamBlockNumber().ifPresent(l -> builder.put("gothamBlock", l));
    getDefuseDifficultyBombBlockNumber().ifPresent(l -> builder.put("ecip1041Block", l));
    getAtlantisBlockNumber().ifPresent(l -> builder.put("atlantisBlock", l));
    getAghartaBlockNumber().ifPresent(l -> builder.put("aghartaBlock", l));
    getPhoenixBlockNumber().ifPresent(l -> builder.put("phoenixBlock", l));
    getThanosBlockNumber().ifPresent(l -> builder.put("thanosBlock", l));
    getMagnetoBlockNumber().ifPresent(l -> builder.put("magnetoBlock", l));
    getMystiqueBlockNumber().ifPresent(l -> builder.put("mystiqueBlock", l));
    getSpiralBlockNumber().ifPresent(l -> builder.put("spiralBlock", l));

    getContractSizeLimit().ifPresent(l -> builder.put("contractSizeLimit", l));
    getEvmStackSize().ifPresent(l -> builder.put("evmStackSize", l));
    getDepositContractAddress().ifPresent(l -> builder.put("depositContractAddress", l));
    if (isClique()) {
      builder.put("clique", getCliqueConfigOptions().asMap());
    }
    if (isEthHash()) {
      builder.put("ethash", getEthashConfigOptions().asMap());
    }
    if (isIbft2()) {
      builder.put("ibft2", getBftConfigOptions().asMap());
    }
    return builder.build();
  }

  @Override
  public TransitionsConfigOptions getTransitions() {
    return transitions;
  }

  @Override
  public PowAlgorithm getPowAlgorithm() {
    return isEthHash() ? PowAlgorithm.ETHASH : PowAlgorithm.UNSUPPORTED;
  }

  @Override
  public Optional<String> getEcCurve() {
    return ecCurve;
  }

  @Override
  public boolean isZeroBaseFee() {
    return zeroBaseFee;
  }

  @Override
  public List<Long> getForkBlockNumbers() {
    return Collections.emptyList();
  }

  @Override
  public List<Long> getForkBlockTimestamps() {
    return Collections.emptyList();
  }

  @Override
  public Optional<Address> getDepositContractAddress() {
    return Optional.empty();
  }

  /**
   * Homestead block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions homesteadBlock(final long blockNumber) {
    homesteadBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Dao fork block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions daoForkBlock(final long blockNumber) {
    daoForkBlock = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Eip 150 block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions eip150Block(final long blockNumber) {
    tangerineWhistleBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Eip 158 block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions eip158Block(final long blockNumber) {
    spuriousDragonBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Byzantium block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions byzantiumBlock(final long blockNumber) {
    byzantiumBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Constantinople block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions constantinopleBlock(final long blockNumber) {
    constantinopleBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Petersburg block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions petersburgBlock(final long blockNumber) {
    petersburgBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Istanbul block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions istanbulBlock(final long blockNumber) {
    istanbulBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Muir glacier block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions muirGlacierBlock(final long blockNumber) {
    muirGlacierBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Berlin block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions berlinBlock(final long blockNumber) {
    berlinBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * London block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions londonBlock(final long blockNumber) {
    londonBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Arrow glacier block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions arrowGlacierBlock(final long blockNumber) {
    arrowGlacierBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Gray glacier block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions grayGlacierBlock(final long blockNumber) {
    grayGlacierBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Merge net split block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions mergeNetSplitBlock(final long blockNumber) {
    mergeNetSplitBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Shanghai time stub genesis config options.
   *
   * @param timestamp the timestamp
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions shanghaiTime(final long timestamp) {
    shanghaiTime = OptionalLong.of(timestamp);
    return this;
  }

  /**
   * Cancun time.
   *
   * @param timestamp the timestamp
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions cancunTime(final long timestamp) {
    cancunTime = OptionalLong.of(timestamp);
    return this;
  }

  /**
   * Prague time.
   *
   * @param timestamp the timestamp
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions pragueTime(final long timestamp) {
    pragueTime = OptionalLong.of(timestamp);
    return this;
  }

  /**
   * Future EIPs Time block.
   *
   * @param timestamp the block timestamp
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions futureEipsTime(final long timestamp) {
    futureEipsTime = OptionalLong.of(timestamp);
    return this;
  }

  /**
   * Experimental EIPs Time block.
   *
   * @param timestamp the block timestamp
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions experimentalEipsTime(final long timestamp) {
    experimentalEipsTime = OptionalLong.of(timestamp);
    return this;
  }

  /**
   * Terminal total difficulty stub genesis config options.
   *
   * @param updatedTerminalTotalDifficulty the updated terminal total difficulty
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions terminalTotalDifficulty(
      final UInt256 updatedTerminalTotalDifficulty) {
    terminalTotalDifficulty = Optional.of(updatedTerminalTotalDifficulty);
    return this;
  }

  /**
   * Terminal block number stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions terminalBlockNumber(final long blockNumber) {
    terminalBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Terminal block hash stub genesis config options.
   *
   * @param blockHash the block hash
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions terminalBlockHash(final Hash blockHash) {
    terminalBlockHash = Optional.of(blockHash);
    return this;
  }

  /**
   * Base fee per gas stub genesis config options.
   *
   * @param baseFeeOverride the base fee override
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions baseFeePerGas(final long baseFeeOverride) {
    baseFeePerGas = Optional.of(Wei.of(baseFeeOverride));
    return this;
  }

  /**
   * Zero base fee per gas stub genesis config options.
   *
   * @param zeroBaseFee the zero base fee override
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions zeroBaseFee(final boolean zeroBaseFee) {
    this.zeroBaseFee = zeroBaseFee;
    return this;
  }

  /**
   * Classic fork block stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions classicForkBlock(final long blockNumber) {
    classicForkBlock = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Ecip 1015 stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions ecip1015(final long blockNumber) {
    ecip1015BlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Die hard stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions dieHard(final long blockNumber) {
    diehardBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Gotham stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions gotham(final long blockNumber) {
    gothamBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Defuse difficulty bomb stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions defuseDifficultyBomb(final long blockNumber) {
    defuseDifficultyBombBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Atlantis stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions atlantis(final long blockNumber) {
    atlantisBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Agharta stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions agharta(final long blockNumber) {
    aghartaBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Phoenix stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions phoenix(final long blockNumber) {
    phoenixBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Thanos stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions thanos(final long blockNumber) {
    thanosBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Magneto stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions magneto(final long blockNumber) {
    magnetoBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Mystique stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions mystique(final long blockNumber) {
    mystiqueBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Spiral stub genesis config options.
   *
   * @param blockNumber the block number
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions spiral(final long blockNumber) {
    spiralBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  /**
   * Chain id stub genesis config options.
   *
   * @param chainId the chain id
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions chainId(final BigInteger chainId) {
    this.chainId = Optional.ofNullable(chainId);
    return this;
  }

  /**
   * Contract size limit stub genesis config options.
   *
   * @param contractSizeLimit the contract size limit
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions contractSizeLimit(final int contractSizeLimit) {
    this.contractSizeLimit = OptionalInt.of(contractSizeLimit);
    return this;
  }

  /**
   * Stack size limit stub genesis config options.
   *
   * @param stackSizeLimit the stack size limit
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions stackSizeLimit(final int stackSizeLimit) {
    this.stackSizeLimit = OptionalInt.of(stackSizeLimit);
    return this;
  }

  /**
   * Ec curve stub genesis config options.
   *
   * @param ecCurve the ec curve
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions ecCurve(final Optional<String> ecCurve) {
    this.ecCurve = ecCurve;
    return this;
  }

  /**
   * Qbft config options stub genesis config options.
   *
   * @param qbftConfigOptions the qbft config options
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions qbftConfigOptions(final QbftConfigOptions qbftConfigOptions) {
    this.qbftConfigOptions = qbftConfigOptions;
    return this;
  }

  /**
   * Bft config options stub genesis config options.
   *
   * @param bftConfigOptions the bft config options
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions bftConfigOptions(final BftConfigOptions bftConfigOptions) {
    this.bftConfigOptions = bftConfigOptions;
    return this;
  }

  /**
   * Transitions stub genesis config options.
   *
   * @param transitions the transitions
   * @return the stub genesis config options
   */
  public StubGenesisConfigOptions transitions(final TransitionsConfigOptions transitions) {
    this.transitions = transitions;
    return this;
  }
}
