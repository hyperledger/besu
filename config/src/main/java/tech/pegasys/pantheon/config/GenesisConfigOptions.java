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

import java.util.OptionalInt;
import java.util.OptionalLong;

import io.vertx.core.json.JsonObject;

public class GenesisConfigOptions {

  private static final String ETHASH_CONFIG_KEY = "ethash";
  private static final String IBFT_CONFIG_KEY = "ibft";
  private static final String CLIQUE_CONFIG_KEY = "clique";
  private final JsonObject configRoot;

  private GenesisConfigOptions(final JsonObject configRoot) {
    this.configRoot = configRoot != null ? configRoot : new JsonObject();
  }

  public static GenesisConfigOptions fromGenesisConfig(final String genesisConfig) {
    return fromGenesisConfig(new JsonObject(genesisConfig));
  }

  public static GenesisConfigOptions fromGenesisConfig(final JsonObject genesisConfig) {
    return new GenesisConfigOptions(genesisConfig.getJsonObject("config"));
  }

  public boolean isEthHash() {
    return configRoot.containsKey(ETHASH_CONFIG_KEY);
  }

  public boolean isIbft() {
    return configRoot.containsKey(IBFT_CONFIG_KEY);
  }

  public boolean isClique() {
    return configRoot.containsKey(CLIQUE_CONFIG_KEY);
  }

  public IbftConfigOptions getIbftConfigOptions() {
    return isIbft()
        ? new IbftConfigOptions(configRoot.getJsonObject(IBFT_CONFIG_KEY))
        : IbftConfigOptions.DEFAULT;
  }

  public CliqueConfigOptions getCliqueConfigOptions() {
    return isClique()
        ? new CliqueConfigOptions(configRoot.getJsonObject(CLIQUE_CONFIG_KEY))
        : CliqueConfigOptions.DEFAULT;
  }

  public OptionalLong getHomesteadBlockNumber() {
    return getOptionalLong("homesteadBlock");
  }

  public OptionalLong getDaoForkBlock() {
    return getOptionalLong("daoForkBlock");
  }

  public OptionalLong getTangerineWhistleBlockNumber() {
    return getOptionalLong("eip150Block");
  }

  public OptionalLong getSpuriousDragonBlockNumber() {
    return getOptionalLong("eip158Block");
  }

  public OptionalLong getByzantiumBlockNumber() {
    return getOptionalLong("byzantiumBlock");
  }

  public OptionalLong getConstantinopleBlockNumber() {
    return getOptionalLong("constantinopleBlock");
  }

  public OptionalInt getChainId() {
    return configRoot.containsKey("chainId")
        ? OptionalInt.of(configRoot.getInteger("chainId"))
        : OptionalInt.empty();
  }

  private OptionalLong getOptionalLong(final String key) {
    return configRoot.containsKey(key)
        ? OptionalLong.of(configRoot.getLong(key))
        : OptionalLong.empty();
  }
}
