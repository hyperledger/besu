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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import org.junit.Test;

public class JsonGenesisConfigOptionsTest {

  private ObjectNode loadCompleteDataSet() {
    try {
      final String configText =
          Resources.toString(
              Resources.getResource("valid_config_with_custom_forks.json"), StandardCharsets.UTF_8);
      return JsonUtil.objectNodeFromString(configText);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to load resource", e);
    }
  }

  private ObjectNode loadConfigWithQbftTransitions() {
    final ObjectNode configNode = loadCompleteDataSet();
    final ObjectNode transitionsNode = JsonUtil.getObjectNode(configNode, "transitions").get();
    final JsonNode transitions = transitionsNode.get("ibft2");
    transitionsNode.remove("ibft2");
    transitionsNode.set("qbft", transitions);
    return configNode;
  }

  private ObjectNode loadConfigWithNoTransitions() {
    final ObjectNode configNode = loadCompleteDataSet();
    configNode.remove("transitions");
    return configNode;
  }

  private ObjectNode loadConfigWithNoIbft2Forks() {
    final ObjectNode configNode = loadCompleteDataSet();
    final ObjectNode transitionsNode = JsonUtil.getObjectNode(configNode, "transitions").get();
    transitionsNode.remove("ibft2");

    return configNode;
  }

  private ObjectNode loadConfigWithAnIbft2ForkWithMissingValidators() {
    final ObjectNode configNode = loadCompleteDataSet();
    final ObjectNode transitionsNode = JsonUtil.getObjectNode(configNode, "transitions").get();
    final ArrayNode ibftNode = JsonUtil.getArrayNode(transitionsNode, "ibft2").get();
    ((ObjectNode) ibftNode.get(0)).remove("validators");

    return configNode;
  }

  @Test
  public void ibftTransitionsDecodesCorrectlyFromFile() {
    final ObjectNode configNode = loadCompleteDataSet();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getTransitions()).isNotNull();
    assertThat(configOptions.getTransitions().getIbftForks().size()).isEqualTo(2);
    assertThat(configOptions.getTransitions().getIbftForks().get(0).getForkBlock()).isEqualTo(20);
    assertThat(configOptions.getTransitions().getIbftForks().get(0).getValidators()).isNotEmpty();
    assertThat(configOptions.getTransitions().getIbftForks().get(0).getValidators().get())
        .containsExactly(
            "0x1234567890123456789012345678901234567890",
            "0x9876543210987654321098765432109876543210");

    assertThat(configOptions.getTransitions().getIbftForks().get(1).getForkBlock()).isEqualTo(25);
    assertThat(configOptions.getTransitions().getIbftForks().get(1).getValidators()).isNotEmpty();
    assertThat(configOptions.getTransitions().getIbftForks().get(1).getValidators().get())
        .containsExactly("0x1234567890123456789012345678901234567890");
  }

  @Test
  public void qbftTransitionsDecodesCorrectlyFromFile() {
    final ObjectNode configNode = loadConfigWithQbftTransitions();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getTransitions()).isNotNull();
    assertThat(configOptions.getTransitions().getQbftForks().size()).isEqualTo(2);
    assertThat(configOptions.getTransitions().getQbftForks().get(0).getForkBlock()).isEqualTo(20);
    assertThat(configOptions.getTransitions().getQbftForks().get(0).getValidators()).isNotEmpty();
    assertThat(configOptions.getTransitions().getQbftForks().get(0).getValidators().get())
        .containsExactly(
            "0x1234567890123456789012345678901234567890",
            "0x9876543210987654321098765432109876543210");

    assertThat(configOptions.getTransitions().getQbftForks().get(1).getForkBlock()).isEqualTo(25);
    assertThat(configOptions.getTransitions().getQbftForks().get(1).getValidators()).isNotEmpty();
    assertThat(configOptions.getTransitions().getQbftForks().get(1).getValidators().get())
        .containsExactly("0x1234567890123456789012345678901234567890");
  }

  @Test
  public void configWithMissingTransitionsIsValid() {
    final ObjectNode configNode = loadConfigWithNoTransitions();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getTransitions()).isNotNull();
    assertThat(configOptions.getTransitions().getIbftForks().size()).isZero();
    assertThat(configOptions.getTransitions().getQbftForks().size()).isZero();
  }

  @Test
  public void configWithNoIbft2ForksIsValid() {
    final ObjectNode configNode = loadConfigWithNoIbft2Forks();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getTransitions()).isNotNull();
    assertThat(configOptions.getTransitions().getIbftForks().size()).isZero();
  }

  @Test
  public void configWithAnIbftWithNoValidatorsListedIsValid() {
    final ObjectNode configNode = loadConfigWithAnIbft2ForkWithMissingValidators();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getTransitions().getIbftForks().get(0).getValidators().isPresent())
        .isFalse();
    assertThat(configOptions.getTransitions().getIbftForks().get(1).getValidators().get().size())
        .isEqualTo(1);
  }

  @Test
  public void configWithValidIbftBlockRewardIsParsable() {
    final ObjectNode configNode = loadConfigWithNoTransitions();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);
    assertThat(configOptions.getBftConfigOptions().getMiningBeneficiary().map(Address::toHexString))
        .contains("0x1234567890123456789012345678901234567890");
    assertThat(configOptions.getBftConfigOptions().getBlockRewardWei()).isEqualTo(21);
  }

  @Test
  public void ibftConfigWithoutMiningBeneficiaryDefaultsToEmpty() {
    final ObjectNode configNode = loadConfigWithNoTransitions();
    final ObjectNode ibftNode = (ObjectNode) configNode.get("ibft2");
    ibftNode.remove("miningbeneficiary");

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getBftConfigOptions().getMiningBeneficiary()).isEmpty();
  }

  @Test
  public void ibftConfigWithoutBlockRewardsDefaultsToZero() {
    final ObjectNode configNode = loadConfigWithNoTransitions();
    final ObjectNode ibftNode = (ObjectNode) configNode.get("ibft2");
    ibftNode.remove("blockreward");

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getBftConfigOptions().getBlockRewardWei()).isEqualTo(0);
  }

  @Test
  public void ibftBlockRewardAsDecimalNumberCorrectlyDecodes() {
    final ObjectNode configNode = loadConfigWithNoTransitions();
    final ObjectNode ibftNode = (ObjectNode) configNode.get("ibft2");
    ibftNode.put("blockreward", "12");

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getBftConfigOptions().getBlockRewardWei()).isEqualTo(12);
  }

  @Test
  public void configWithoutEcCurveReturnsEmptyOptional() {
    final ObjectNode configNode = loadCompleteDataSet();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getEcCurve().isEmpty()).isTrue();
  }

  @Test
  public void configWithEcCurveIsCorrectlySet() {
    final ObjectNode configNode = loadCompleteDataSet();
    configNode.put("eccurve", "secp256k1");

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.getEcCurve().isPresent()).isTrue();
    assertThat(configOptions.getEcCurve().get()).isEqualTo("secp256k1");
  }

  @Test
  public void configWithMigrationFromIbft2ToQbft() {
    final ObjectNode configNode = loadConfigWithMigrationFromIbft2ToQbft();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.isIbft2()).isTrue();
    assertThat(configOptions.isQbft()).isTrue();
    assertThat(configOptions.isConsensusMigration()).isTrue();
  }

  @Test
  public void configWithMigrationFromIbftLegacyToQbft() {
    final ObjectNode configNode = loadConfigWithMigrationFromIbftLegacyToQbft();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.isIbftLegacy()).isTrue();
    assertThat(configOptions.isQbft()).isTrue();
    assertThat(configOptions.isConsensusMigration()).isTrue();
  }

  @Test
  public void configWithoutMigration() {
    final ObjectNode configNode = loadCompleteDataSet();

    final JsonGenesisConfigOptions configOptions =
        JsonGenesisConfigOptions.fromJsonObject(configNode);

    assertThat(configOptions.isIbftLegacy()).isFalse();
  }

  private ObjectNode loadConfigWithMigrationFromIbft2ToQbft() {
    try {
      final String configText =
          Resources.toString(
              Resources.getResource("valid_config_with_migration_to_qbft.json"),
              StandardCharsets.UTF_8);
      return JsonUtil.objectNodeFromString(configText);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to load resource", e);
    }
  }

  private ObjectNode loadConfigWithMigrationFromIbftLegacyToQbft() {
    ObjectNode configNode = loadConfigWithMigrationFromIbft2ToQbft();
    JsonNode consensusConfig = configNode.get("ibft2");
    configNode.remove("ibft2");
    configNode.set("ibft", consensusConfig);
    return configNode;
  }
}
