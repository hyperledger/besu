/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.hyperledger.besu.config.JsonGenesisConfiguration.TRANSITIONS_CONFIG_KEY;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Named;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dagger.Module;
import dagger.Provides;

/** A dagger module that provides the genesis configuration. */
@Module
public class GenesisModule {

  /** Default constructor. */
  public GenesisModule() {}

  /**
   * Provides the genesis file from the genesis config JSON.
   *
   * @param genesisConfigJson the genesis config JSON
   * @return the genesis file
   */
  @Singleton
  @Provides
  GenesisFile providesGenesisConfig(final @Named("genesisConfigJson") String genesisConfigJson) {
    return GenesisFile.fromConfig(genesisConfigJson);
  }

  /**
   * Gets config options, including any overrides.
   *
   * @return the config options
   */
  @Singleton
  @Provides
  GenesisConfiguration provideGenesisConfigOptions(
      final GenesisFile genesisConfig,
      final GenesisReader loader,
      final @Named("genesisConfigOverrides") Optional<Map<String, String>> overrides) {
    final ObjectNode config = loader.getConfig();
    // are there any overrides to apply?
    if (overrides.isEmpty()) {
      return fromJsonObjectWithOverrides(config, emptyMap());
    }
    // otherwise apply overrides
    Map<String, String> overridesRef = overrides.get();

    // if baseFeePerGas has been explicitly configured, pass it as an override:
    final var optBaseFee = genesisConfig.getBaseFeePerGas();
    if (optBaseFee.isPresent()) {
      // streams and maps cannot handle null values.
      overridesRef = new HashMap<>(overrides.get());
      overridesRef.put("baseFeePerGas", optBaseFee.get().toShortHexString());
    }

    return fromJsonObjectWithOverrides(config, overridesRef);
  }

  /**
   * From json object with overrides json genesis config options.
   *
   * @param configRoot the config root
   * @param configOverrides the config overrides
   * @return the json genesis config options
   */
  private JsonGenesisConfiguration fromJsonObjectWithOverrides(
      final ObjectNode configRoot, final Map<String, String> configOverrides) {
    final TransitionsConfigOptions transitionsConfigOptions;
    transitionsConfigOptions = loadTransitionsFrom(configRoot);
    return new JsonGenesisConfiguration(configRoot, configOverrides, transitionsConfigOptions);
  }

  /**
   * Loads transitions configuration from the parent node.
   *
   * @param parentNode the parent JSON node
   * @return the transitions configuration options
   */
  private TransitionsConfigOptions loadTransitionsFrom(final ObjectNode parentNode) {
    final Optional<ObjectNode> transitionsNode =
        JsonUtil.getObjectNode(parentNode, TRANSITIONS_CONFIG_KEY);
    if (transitionsNode.isEmpty()) {
      return new TransitionsConfigOptions(JsonUtil.createEmptyObjectNode());
    }

    return new TransitionsConfigOptions(transitionsNode.get());
  }
}
