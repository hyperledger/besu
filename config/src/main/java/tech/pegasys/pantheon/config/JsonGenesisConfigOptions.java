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
package tech.pegasys.pantheon.config;

import static java.util.Objects.isNull;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JsonGenesisConfigOptions implements GenesisConfigOptions {

  private final Logger LOG = LogManager.getLogger();

  private static final String ETHASH_CONFIG_KEY = "ethash";
  private static final String IBFT_LEGACY_CONFIG_KEY = "ibft";
  private static final String IBFT2_CONFIG_KEY = "ibft2";
  private static final String CLIQUE_CONFIG_KEY = "clique";
  private final ObjectNode configRoot;

  public static JsonGenesisConfigOptions fromJsonObject(final ObjectNode configRoot) {
    return new JsonGenesisConfigOptions(configRoot);
  }

  JsonGenesisConfigOptions(final ObjectNode maybeConfig) {
    this.configRoot = isNull(maybeConfig) ? JsonUtil.createEmptyObjectNode() : maybeConfig;
  }

  @Override
  public String getConsensusEngine() {
    if (isEthHash()) {
      return ETHASH_CONFIG_KEY;
    } else if (isIbft2()) {
      return IBFT2_CONFIG_KEY;
    } else if (isIbftLegacy()) {
      return IBFT_LEGACY_CONFIG_KEY;
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
  public IbftConfigOptions getIbftLegacyConfigOptions() {
    return JsonUtil.getObjectNode(configRoot, IBFT_LEGACY_CONFIG_KEY)
        .map(IbftConfigOptions::new)
        .orElse(IbftConfigOptions.DEFAULT);
  }

  @Override
  public IbftConfigOptions getIbft2ConfigOptions() {
    return JsonUtil.getObjectNode(configRoot, IBFT2_CONFIG_KEY)
        .map(IbftConfigOptions::new)
        .orElse(IbftConfigOptions.DEFAULT);
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
  public OptionalLong getConstantinopleFixBlockNumber() {
    return getOptionalLong("constantinoplefixblock");
  }

  // Disabled until Istanbul is settled
  @Override
  public OptionalLong getIstanbulBlockNumber() {
    if (configRoot.has("istanbulblock")) {
      LOG.warn("IstanbulBlock was requested but configuration is not enabled");
    }
    return OptionalLong.empty();
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return JsonUtil.getValueAsString(configRoot, "chainid").map(BigInteger::new);
  }

  @Override
  public OptionalInt getContractSizeLimit() {
    return JsonUtil.getInt(configRoot, "contractsizelimit");
  }

  @Override
  public OptionalInt getEvmStackSize() {
    return JsonUtil.getInt(configRoot, "evmstacksize");
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
    getTangerineWhistleBlockNumber()
        .ifPresent(
            l -> {
              builder.put("eip150Block", l);
              if (configRoot.has("eip150hash")) {
                builder.put("eip150Hash", configRoot.get("eip150hash").asText());
              }
            });
    getSpuriousDragonBlockNumber()
        .ifPresent(
            l -> {
              builder.put("eip155Block", l);
              builder.put("eip158Block", l);
            });
    getByzantiumBlockNumber().ifPresent(l -> builder.put("byzantiumBlock", l));
    getConstantinopleBlockNumber().ifPresent(l -> builder.put("constantinopleBlock", l));
    getConstantinopleFixBlockNumber().ifPresent(l -> builder.put("constantinopleFixBlock", l));
    // Disabled until Istanbul is settled
    // getIstanbulBlockNumber().ifPresent(l -> builder.put("istanbulBlock", l));
    getContractSizeLimit().ifPresent(l -> builder.put("contractSizeLimit", l));
    getEvmStackSize().ifPresent(l -> builder.put("evmstacksize", l));
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

  private OptionalLong getOptionalLong(final String key) {
    return JsonUtil.getLong(configRoot, key);
  }
}
