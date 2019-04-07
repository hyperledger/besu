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

import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.google.common.collect.ImmutableMap;
import io.vertx.core.json.JsonObject;

public class JsonGenesisConfigOptions implements GenesisConfigOptions {

  private static final String ETHASH_CONFIG_KEY = "ethash";
  private static final String IBFT_LEGACY_CONFIG_KEY = "ibft";
  private static final String IBFT2_CONFIG_KEY = "ibft2";
  private static final String CLIQUE_CONFIG_KEY = "clique";
  private final JsonObject configRoot;

  JsonGenesisConfigOptions(final JsonObject configRoot) {
    this.configRoot = configRoot != null ? configRoot : new JsonObject();
  }

  @Override
  public boolean isEthHash() {
    return configRoot.containsKey(ETHASH_CONFIG_KEY);
  }

  @Override
  public boolean isIbftLegacy() {
    return configRoot.containsKey(IBFT_LEGACY_CONFIG_KEY);
  }

  @Override
  public boolean isClique() {
    return configRoot.containsKey(CLIQUE_CONFIG_KEY);
  }

  @Override
  public boolean isIbft2() {
    return configRoot.containsKey(IBFT2_CONFIG_KEY);
  }

  @Override
  public IbftConfigOptions getIbftLegacyConfigOptions() {
    return isIbftLegacy()
        ? new IbftConfigOptions(configRoot.getJsonObject(IBFT_LEGACY_CONFIG_KEY))
        : IbftConfigOptions.DEFAULT;
  }

  @Override
  public IbftConfigOptions getIbft2ConfigOptions() {
    return isIbft2()
        ? new IbftConfigOptions(configRoot.getJsonObject(IBFT2_CONFIG_KEY))
        : IbftConfigOptions.DEFAULT;
  }

  @Override
  public CliqueConfigOptions getCliqueConfigOptions() {
    return isClique()
        ? new CliqueConfigOptions(configRoot.getJsonObject(CLIQUE_CONFIG_KEY))
        : CliqueConfigOptions.DEFAULT;
  }

  @Override
  public EthashConfigOptions getEthashConfigOptions() {
    return isEthHash()
        ? new EthashConfigOptions(configRoot.getJsonObject(ETHASH_CONFIG_KEY))
        : EthashConfigOptions.DEFAULT;
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

  @Override
  public OptionalInt getChainId() {
    return configRoot.containsKey("chainid")
        ? OptionalInt.of(configRoot.getInteger("chainid"))
        : OptionalInt.empty();
  }

  @Override
  public OptionalInt getContractSizeLimit() {
    return configRoot.containsKey("contractsizelimit")
        ? OptionalInt.of(configRoot.getInteger("contractsizelimit"))
        : OptionalInt.empty();
  }

  @Override
  public Map<String, Object> asMap() {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    builder.put("chainId", getChainId().getAsInt());
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
              if (configRoot.containsKey("eip150hash")) {
                builder.put("eip150Hash", configRoot.getString("eip150hash"));
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
    getContractSizeLimit().ifPresent(l -> builder.put("contractSizeLimit", l));
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
    return ConfigUtil.getOptionalLong(configRoot, key);
  }
}
