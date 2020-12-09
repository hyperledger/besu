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

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.google.common.collect.ImmutableMap;

public class StubGenesisConfigOptions implements GenesisConfigOptions {

  private OptionalLong homesteadBlockNumber = OptionalLong.empty();
  private OptionalLong daoForkBlock = OptionalLong.empty();
  private OptionalLong tangerineWhistleBlockNumber = OptionalLong.empty();
  private OptionalLong spuriousDragonBlockNumber = OptionalLong.empty();
  private OptionalLong byzantiumBlockNumber = OptionalLong.empty();
  private OptionalLong constantinopleBlockNumber = OptionalLong.empty();
  private OptionalLong constantinopleFixBlockNumber = OptionalLong.empty();
  private OptionalLong istanbulBlockNumber = OptionalLong.empty();
  private OptionalLong muirGlacierBlockNumber = OptionalLong.empty();
  private OptionalLong berlinBlockNumber = OptionalLong.empty();
  // TODO EIP-1559 change for the actual fork name when known
  private final OptionalLong eip1559BlockNumber = OptionalLong.empty();
  private final OptionalLong classicForkBlock = OptionalLong.empty();
  private final OptionalLong ecip1015BlockNumber = OptionalLong.empty();
  private final OptionalLong diehardBlockNumber = OptionalLong.empty();
  private final OptionalLong gothamBlockNumber = OptionalLong.empty();
  private final OptionalLong defuseDifficultyBombBlockNumber = OptionalLong.empty();
  private final OptionalLong atlantisBlockNumber = OptionalLong.empty();
  private final OptionalLong aghartaBlockNumber = OptionalLong.empty();
  private final OptionalLong phoenixBlockNumber = OptionalLong.empty();
  private final OptionalLong thanosBlockNumber = OptionalLong.empty();
  private Optional<BigInteger> chainId = Optional.empty();
  private OptionalInt contractSizeLimit = OptionalInt.empty();
  private OptionalInt stackSizeLimit = OptionalInt.empty();
  private final OptionalLong ecip1017EraRounds = OptionalLong.empty();

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
  public IbftConfigOptions getIbftLegacyConfigOptions() {
    return IbftConfigOptions.DEFAULT;
  }

  @Override
  public CliqueConfigOptions getCliqueConfigOptions() {
    return CliqueConfigOptions.DEFAULT;
  }

  @Override
  public IbftConfigOptions getIbft2ConfigOptions() {
    return IbftConfigOptions.DEFAULT;
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
  public OptionalLong getConstantinopleFixBlockNumber() {
    return constantinopleFixBlockNumber;
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
    return ExperimentalEIPs.berlinEnabled ? berlinBlockNumber : OptionalLong.empty();
  }

  @Override
  // TODO EIP-1559 change for the actual fork name when known
  public OptionalLong getEIP1559BlockNumber() {
    return ExperimentalEIPs.eip1559Enabled ? eip1559BlockNumber : OptionalLong.empty();
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
    getChainId().ifPresent(chainId -> builder.put("chainId", chainId));
    getHomesteadBlockNumber().ifPresent(l -> builder.put("homesteadBlock", l));
    getDaoForkBlock()
        .ifPresent(
            l -> {
              builder.put("daoForkBlock", l);
              builder.put("daoForkSupport", Boolean.TRUE);
            });
    getTangerineWhistleBlockNumber().ifPresent(l -> builder.put("eip150Block", l));
    getSpuriousDragonBlockNumber()
        .ifPresent(
            l -> {
              builder.put("eip155Block", l);
              builder.put("eip158Block", l);
            });
    getByzantiumBlockNumber().ifPresent(l -> builder.put("byzantiumBlock", l));
    getConstantinopleBlockNumber().ifPresent(l -> builder.put("constantinopleBlock", l));
    getConstantinopleFixBlockNumber().ifPresent(l -> builder.put("petersburgBlock", l));
    getIstanbulBlockNumber().ifPresent(l -> builder.put("istanbulBlock", l));
    getMuirGlacierBlockNumber().ifPresent(l -> builder.put("muirGlacierBlock", l));
    getBerlinBlockNumber().ifPresent(l -> builder.put("berlinBlock", l));
    // TODO EIP-1559 change for the actual fork name when known
    getEIP1559BlockNumber().ifPresent(l -> builder.put("eip1559Block", l));
    getContractSizeLimit().ifPresent(l -> builder.put("contractSizeLimit", l));
    getEvmStackSize().ifPresent(l -> builder.put("evmStackSize", l));
    if (isClique()) {
      builder.put("clique", getCliqueConfigOptions().asMap());
    }
    if (isEthHash()) {
      builder.put("ethash", getEthashConfigOptions().asMap());
    }
    if (isIbftLegacy()) {
      builder.put("ibft", getIbftLegacyConfigOptions().asMap());
    }
    if (isIbft2()) {
      builder.put("ibft2", getIbft2ConfigOptions().asMap());
    }
    return builder.build();
  }

  @Override
  public TransitionsConfigOptions getTransitions() {
    return TransitionsConfigOptions.DEFAULT;
  }

  @Override
  public boolean isQuorum() {
    return false;
  }

  @Override
  public OptionalLong getQip714BlockNumber() {
    return OptionalLong.empty();
  }

  @Override
  public List<Long> getForks() {
    return Collections.emptyList();
  }

  public StubGenesisConfigOptions homesteadBlock(final long blockNumber) {
    homesteadBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions daoForkBlock(final long blockNumber) {
    daoForkBlock = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions eip150Block(final long blockNumber) {
    tangerineWhistleBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions eip158Block(final long blockNumber) {
    spuriousDragonBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions byzantiumBlock(final long blockNumber) {
    byzantiumBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions constantinopleBlock(final long blockNumber) {
    constantinopleBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions constantinopleFixBlock(final long blockNumber) {
    constantinopleFixBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions istanbulBlock(final long blockNumber) {
    istanbulBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions muirGlacierBlock(final long blockNumber) {
    muirGlacierBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions berlinBlock(final long blockNumber) {
    berlinBlockNumber = OptionalLong.of(blockNumber);
    return this;
  }

  public StubGenesisConfigOptions chainId(final BigInteger chainId) {
    this.chainId = Optional.ofNullable(chainId);
    return this;
  }

  public StubGenesisConfigOptions contractSizeLimit(final int contractSizeLimit) {
    this.contractSizeLimit = OptionalInt.of(contractSizeLimit);
    return this;
  }

  public StubGenesisConfigOptions stackSizeLimit(final int stackSizeLimit) {
    this.stackSizeLimit = OptionalInt.of(stackSizeLimit);
    return this;
  }
}
