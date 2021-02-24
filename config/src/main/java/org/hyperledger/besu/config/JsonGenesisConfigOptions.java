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

import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

public class JsonGenesisConfigOptions implements GenesisConfigOptions {

  private static final String ETHASH_CONFIG_KEY = "ethash";
  private static final String IBFT_LEGACY_CONFIG_KEY = "ibft";
  private static final String IBFT2_CONFIG_KEY = "ibft2";
  private static final String QBFT_CONFIG_KEY = "qbft";
  private static final String CLIQUE_CONFIG_KEY = "clique";

  private static final String TRANSITIONS_CONFIG_KEY = "transitions";
  private final ObjectNode configRoot;
  private final Map<String, String> configOverrides = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final TransitionsConfigOptions transitions;

  public static JsonGenesisConfigOptions fromJsonObject(final ObjectNode configRoot) {
    return fromJsonObjectWithOverrides(configRoot, emptyMap());
  }

  static JsonGenesisConfigOptions fromJsonObjectWithOverrides(
      final ObjectNode configRoot, final Map<String, String> configOverrides) {
    final TransitionsConfigOptions transitionsConfigOptions;
    try {
      transitionsConfigOptions = loadTransitionsFrom(configRoot);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException("Transitions section of genesis file failed to decode.", e);
    }
    return new JsonGenesisConfigOptions(configRoot, configOverrides, transitionsConfigOptions);
  }

  private static TransitionsConfigOptions loadTransitionsFrom(final ObjectNode parentNode)
      throws JsonProcessingException {

    final Optional<ObjectNode> transitionsNode =
        JsonUtil.getObjectNode(parentNode, TRANSITIONS_CONFIG_KEY);
    if (transitionsNode.isEmpty()) {
      return new TransitionsConfigOptions(JsonUtil.createEmptyObjectNode());
    }

    return new TransitionsConfigOptions(transitionsNode.get());
  }

  private JsonGenesisConfigOptions(
      final ObjectNode maybeConfig, final TransitionsConfigOptions transitionsConfig) {
    this(maybeConfig, Collections.emptyMap(), transitionsConfig);
  }

  JsonGenesisConfigOptions(
      final ObjectNode maybeConfig,
      final Map<String, String> configOverrides,
      final TransitionsConfigOptions transitionsConfig) {
    this.configRoot = isNull(maybeConfig) ? JsonUtil.createEmptyObjectNode() : maybeConfig;
    if (configOverrides != null) {
      this.configOverrides.putAll(configOverrides);
    }
    this.transitions = transitionsConfig;
  }

  @Override
  public String getConsensusEngine() {
    if (isEthHash()) {
      return ETHASH_CONFIG_KEY;
    } else if (isIbft2()) {
      return IBFT2_CONFIG_KEY;
    } else if (isIbftLegacy()) {
      return IBFT_LEGACY_CONFIG_KEY;
    } else if (isQbft()) {
      return QBFT_CONFIG_KEY;
    } else if (isClique()) {
      return CLIQUE_CONFIG_KEY;
    } else {
      return "unknown";
    }
  }

  @Override
  public boolean isEthHash() {
    return configRoot.has(ETHASH_CONFIG_KEY);
  }

  @Override
  public boolean isIbftLegacy() {
    return configRoot.has(IBFT_LEGACY_CONFIG_KEY);
  }

  @Override
  public boolean isClique() {
    return configRoot.has(CLIQUE_CONFIG_KEY);
  }

  @Override
  public boolean isIbft2() {
    return configRoot.has(IBFT2_CONFIG_KEY);
  }

  @Override
  public boolean isQbft() {
    return configRoot.has(QBFT_CONFIG_KEY);
  }

  @Override
  public BftConfigOptions getIbftLegacyConfigOptions() {
    return JsonUtil.getObjectNode(configRoot, IBFT_LEGACY_CONFIG_KEY)
        .map(BftConfigOptions::new)
        .orElse(BftConfigOptions.DEFAULT);
  }

  @Override
  public BftConfigOptions getBftConfigOptions() {
    final String fieldKey = isIbft2() ? IBFT2_CONFIG_KEY : QBFT_CONFIG_KEY;
    return JsonUtil.getObjectNode(configRoot, fieldKey)
        .map(BftConfigOptions::new)
        .orElse(BftConfigOptions.DEFAULT);
  }

  @Override
  public CliqueConfigOptions getCliqueConfigOptions() {
    return JsonUtil.getObjectNode(configRoot, CLIQUE_CONFIG_KEY)
        .map(CliqueConfigOptions::new)
        .orElse(CliqueConfigOptions.DEFAULT);
  }

  @Override
  public EthashConfigOptions getEthashConfigOptions() {
    return JsonUtil.getObjectNode(configRoot, ETHASH_CONFIG_KEY)
        .map(EthashConfigOptions::new)
        .orElse(EthashConfigOptions.DEFAULT);
  }

  @Override
  public TransitionsConfigOptions getTransitions() {
    return transitions;
  }

  @Override
  public OptionalLong getHomesteadBlockNumber() {
    return getOptionalLong("homesteadblock");
  }

  @Override
  public OptionalLong getDaoForkBlock() {
    final OptionalLong block = getOptionalLong("daoforkblock");
    if (block.isPresent() && block.getAsLong() <= 0) {
      return OptionalLong.empty();
    }
    return block;
  }

  @Override
  public OptionalLong getTangerineWhistleBlockNumber() {
    return getOptionalLong("eip150block");
  }

  @Override
  public OptionalLong getSpuriousDragonBlockNumber() {
    return getOptionalLong("eip158block");
  }

  @Override
  public OptionalLong getByzantiumBlockNumber() {
    return getOptionalLong("byzantiumblock");
  }

  @Override
  public OptionalLong getConstantinopleBlockNumber() {
    return getOptionalLong("constantinopleblock");
  }

  @Override
  public OptionalLong getPetersburgBlockNumber() {
    final OptionalLong petersburgBlock = getOptionalLong("petersburgblock");
    final OptionalLong constantinopleFixBlock = getOptionalLong("constantinoplefixblock");
    if (constantinopleFixBlock.isPresent()) {
      if (petersburgBlock.isPresent()) {
        throw new RuntimeException(
            "Genesis files cannot specify both petersburgBlock and constantinopleFixBlock.");
      }
      return constantinopleFixBlock;
    }
    return petersburgBlock;
  }

  @Override
  public OptionalLong getIstanbulBlockNumber() {
    return getOptionalLong("istanbulblock");
  }

  @Override
  public OptionalLong getMuirGlacierBlockNumber() {
    return getOptionalLong("muirglacierblock");
  }

  @Override
  public OptionalLong getBerlinBlockNumber() {
    final OptionalLong berlinBlock = getOptionalLong("berlinblock");
    final OptionalLong yolov3Block = getOptionalLong("yolov3block");
    if (yolov3Block.isPresent()) {
      if (berlinBlock.isPresent()) {
        throw new RuntimeException(
            "Genesis files cannot specify both berlinblock and yoloV2Block.");
      }
      return yolov3Block;
    }
    return berlinBlock;
  }

  @Override
  // TODO EIP-1559 change for the actual fork name when known
  public OptionalLong getEIP1559BlockNumber() {
    return ExperimentalEIPs.eip1559Enabled ? getOptionalLong("eip1559block") : OptionalLong.empty();
  }

  @Override
  public OptionalLong getClassicForkBlock() {
    return getOptionalLong("classicforkblock");
  }

  @Override
  public OptionalLong getEcip1015BlockNumber() {
    return getOptionalLong("ecip1015block");
  }

  @Override
  public OptionalLong getDieHardBlockNumber() {
    return getOptionalLong("diehardblock");
  }

  @Override
  public OptionalLong getGothamBlockNumber() {
    return getOptionalLong("gothamblock");
  }

  @Override
  public OptionalLong getDefuseDifficultyBombBlockNumber() {
    return getOptionalLong("ecip1041block");
  }

  @Override
  public OptionalLong getAtlantisBlockNumber() {
    return getOptionalLong("atlantisblock");
  }

  @Override
  public OptionalLong getAghartaBlockNumber() {
    return getOptionalLong("aghartablock");
  }

  @Override
  public OptionalLong getPhoenixBlockNumber() {
    return getOptionalLong("phoenixblock");
  }

  @Override
  public OptionalLong getThanosBlockNumber() {
    return getOptionalLong("thanosblock");
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return getOptionalBigInteger("chainid");
  }

  @Override
  public OptionalInt getContractSizeLimit() {
    return getOptionalInt("contractsizelimit");
  }

  @Override
  public OptionalInt getEvmStackSize() {
    return getOptionalInt("evmstacksize");
  }

  @Override
  public OptionalLong getEcip1017EraRounds() {
    return getOptionalLong("ecip1017erarounds");
  }

  @Override
  public boolean isQuorum() {
    return getOptionalBoolean("isquorum").orElse(false);
  }

  @Override
  public OptionalLong getQip714BlockNumber() {
    return getOptionalLong("qip714block");
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
            });
    getTangerineWhistleBlockNumber()
        .ifPresent(
            l -> {
              builder.put("eip150Block", l);
            });
    getSpuriousDragonBlockNumber()
        .ifPresent(
            l -> {
              builder.put("eip158Block", l);
            });
    getByzantiumBlockNumber().ifPresent(l -> builder.put("byzantiumBlock", l));
    getConstantinopleBlockNumber().ifPresent(l -> builder.put("constantinopleBlock", l));
    getPetersburgBlockNumber().ifPresent(l -> builder.put("petersburgBlock", l));
    getIstanbulBlockNumber().ifPresent(l -> builder.put("istanbulBlock", l));
    getMuirGlacierBlockNumber().ifPresent(l -> builder.put("muirGlacierBlock", l));
    getBerlinBlockNumber().ifPresent(l -> builder.put("berlinBlock", l));
    getEIP1559BlockNumber().ifPresent(l -> builder.put("eip1559Block", l));
    getContractSizeLimit().ifPresent(l -> builder.put("contractSizeLimit", l));
    getEvmStackSize().ifPresent(l -> builder.put("evmstacksize", l));
    getEcip1017EraRounds().ifPresent(l -> builder.put("ecip1017EraRounds", l));
    getThanosBlockNumber().ifPresent(l -> builder.put("thanosBlock", l));

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
      builder.put("ibft2", getBftConfigOptions().asMap());
    }

    if (isQuorum()) {
      builder.put("isQuorum", true);
      getQip714BlockNumber().ifPresent(blockNumber -> builder.put("qip714block", blockNumber));
    }

    return builder.build();
  }

  private OptionalLong getOptionalLong(final String key) {
    if (configOverrides.containsKey(key)) {
      final String value = configOverrides.get(key);
      return value == null || value.isEmpty()
          ? OptionalLong.empty()
          : OptionalLong.of(Long.valueOf(configOverrides.get(key), 10));
    } else {
      return JsonUtil.getLong(configRoot, key);
    }
  }

  private OptionalInt getOptionalInt(final String key) {
    if (configOverrides.containsKey(key)) {
      final String value = configOverrides.get(key);
      return value == null || value.isEmpty()
          ? OptionalInt.empty()
          : OptionalInt.of(Integer.valueOf(configOverrides.get(key), 10));
    } else {
      return JsonUtil.getInt(configRoot, key);
    }
  }

  private Optional<BigInteger> getOptionalBigInteger(final String key) {
    if (configOverrides.containsKey(key)) {
      final String value = configOverrides.get(key);
      return value == null || value.isEmpty()
          ? Optional.empty()
          : Optional.of(new BigInteger(value));
    } else {
      return JsonUtil.getValueAsString(configRoot, key).map(s -> new BigInteger(s, 10));
    }
  }

  private Optional<Boolean> getOptionalBoolean(final String key) {
    if (configOverrides.containsKey(key)) {
      final String value = configOverrides.get(key);
      return value == null || value.isEmpty()
          ? Optional.empty()
          : Optional.of(Boolean.valueOf(configOverrides.get(key)));
    } else {
      return JsonUtil.getBoolean(configRoot, key);
    }
  }

  @Override
  public List<Long> getForks() {
    Stream<OptionalLong> forkBlockNumbers =
        Stream.of(
            getHomesteadBlockNumber(),
            getDaoForkBlock(),
            getTangerineWhistleBlockNumber(),
            getSpuriousDragonBlockNumber(),
            getByzantiumBlockNumber(),
            getConstantinopleBlockNumber(),
            getPetersburgBlockNumber(),
            getIstanbulBlockNumber(),
            getMuirGlacierBlockNumber(),
            getBerlinBlockNumber(),
            getEIP1559BlockNumber(),
            getEcip1015BlockNumber(),
            getDieHardBlockNumber(),
            getGothamBlockNumber(),
            getDefuseDifficultyBombBlockNumber(),
            getAtlantisBlockNumber(),
            getAghartaBlockNumber(),
            getPhoenixBlockNumber(),
            getThanosBlockNumber());

    return forkBlockNumbers
        .filter(OptionalLong::isPresent)
        .map(OptionalLong::getAsLong)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }
}
