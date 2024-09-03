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
package org.hyperledger.besu.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hyperledger.besu.cli.config.NetworkName.EPHEMERY;
import static org.hyperledger.besu.config.GenesisConfigFile.fromConfig;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.config.GenesisConfigFile;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.vertx.core.json.JsonObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EphemeryGenesisFileTest {
  private EphemeryGenesisFile ephemeryGenesisFile;
  private static final int GENESIS_CONFIG_TEST_CHAINID = 39438135;
  private static final long GENESIS_TEST_TIMESTAMP = 1720119600;
  private static final long CURRENT_TIMESTAMP = 1712041200;
  private static final long CURRENT_TIMESTAMP_HIGHER = 1922041200;
  private static final long PERIOD_IN_SECONDS = 28 * 24 * 60 * 60;
  private static final long PERIOD_SINCE_GENESIS = 3;

  private static final JsonObject VALID_GENESIS_JSON =
      (new JsonObject())
          .put("config", (new JsonObject()).put("chainId", GENESIS_CONFIG_TEST_CHAINID))
          .put("timestamp", GENESIS_TEST_TIMESTAMP);

  private static final GenesisConfigFile INVALID_GENESIS_JSON = fromConfig("{}");
  private static final JsonObject INVALID_GENESIS_JSON_WITHOUT_CHAINID =
      (new JsonObject()).put("timestamp", GENESIS_TEST_TIMESTAMP);

  private static final JsonObject INVALID_GENESIS_JSON_WITHOUT_TIMESTAMP =
      new JsonObject()
          .put("config", (new JsonObject()).put("chainId", GENESIS_CONFIG_TEST_CHAINID));

  @BeforeEach
  void setUp() {
    ephemeryGenesisFile = new EphemeryGenesisFile();
  }

  @Test
  public void testEphemeryWhenChainIdIsAbsent() {
    final GenesisConfigFile config =
        GenesisConfigFile.fromConfig(INVALID_GENESIS_JSON_WITHOUT_CHAINID.toString());
    Optional<BigInteger> chainId = config.getConfigOptions().getChainId();
    assertThat(chainId).isNotPresent();
  }

  @Test
  public void testShouldDefaultTimestampToZero() {
    final GenesisConfigFile config =
        GenesisConfigFile.fromConfig(INVALID_GENESIS_JSON_WITHOUT_TIMESTAMP.toString());
    assertThat(config.getTimestamp()).isZero();
  }

  @Test
  public void testEphemeryWhenGenesisJsonIsInvalid() {
    Assertions.assertThatThrownBy(INVALID_GENESIS_JSON::getDifficulty)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid genesis block configuration");
  }

  @Test
  public void testEphemeryWhenGenesisJsonIsValid() {
    final GenesisConfigFile config = GenesisConfigFile.fromConfig(VALID_GENESIS_JSON.toString());
    assertThat(String.valueOf(config.getTimestamp())).isNotNull();
    assertThat(String.valueOf(config.getTimestamp())).isNotEmpty();
    assertThat(String.valueOf(config.getConfigOptions().getChainId())).isNotNull();
    assertThat(String.valueOf(config.getConfigOptions().getChainId())).isNotEmpty();
  }

  @Test
  public void testEphemeryNotYetDueForUpdate() {
    final GenesisConfigFile config = GenesisConfigFile.fromConfig(VALID_GENESIS_JSON.toString());
    assertThat(CURRENT_TIMESTAMP).isLessThan(config.getTimestamp() + PERIOD_IN_SECONDS);
  }

  @Test
  void testOverrideWithUpdatedChainIdAndTimeStamp() {
    BigInteger expectedChainId =
        BigInteger.valueOf(GENESIS_CONFIG_TEST_CHAINID)
            .add(BigInteger.valueOf(PERIOD_SINCE_GENESIS));

    long expectedGenesisTimestamp =
        GENESIS_TEST_TIMESTAMP + (PERIOD_SINCE_GENESIS * PERIOD_IN_SECONDS);

    final GenesisConfigFile config = GenesisConfigFile.fromResource("/ephemery.json");

    final Map<String, String> override = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    override.put("chainId", String.valueOf(expectedChainId));
    override.put("timestamp", String.valueOf(expectedGenesisTimestamp));

    assertThat(config.withOverrides(override).getConfigOptions().getChainId()).isPresent();
    assertThat(config.withOverrides(override).getTimestamp()).isNotNull();
  }

  @Test
  public void testEphemeryWhenSuccessful() {
    final GenesisConfigFile config = GenesisConfigFile.fromConfig(VALID_GENESIS_JSON.toString());

    BigInteger expectedChainId =
        BigInteger.valueOf(GENESIS_CONFIG_TEST_CHAINID)
            .add(BigInteger.valueOf(PERIOD_SINCE_GENESIS));

    long expectedGenesisTimestamp =
        GENESIS_TEST_TIMESTAMP + (PERIOD_SINCE_GENESIS * PERIOD_IN_SECONDS);

    EPHEMERY.setNetworkId(expectedChainId);

    final Map<String, String> override = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    override.put("chainId", String.valueOf(expectedChainId));
    override.put("timestamp", String.valueOf(expectedGenesisTimestamp));
    config.withOverrides(override);

    assertThat(CURRENT_TIMESTAMP_HIGHER)
        .isGreaterThan(Long.parseLong(String.valueOf(GENESIS_TEST_TIMESTAMP + PERIOD_IN_SECONDS)));

    assertThat(override.get("timestamp")).isEqualTo(String.valueOf(expectedGenesisTimestamp));
    assertThat(override.get("chainId")).isEqualTo(expectedChainId.toString());
  }

  @Test
  public void testEphemeryThrowIOException() {
    EphemeryGenesisFile spyEphemeryGenesisFile = spy(ephemeryGenesisFile);

    doThrow(new RuntimeException("Unable to update ephemery genesis file."))
        .when(spyEphemeryGenesisFile)
        .updateGenesis();

    assertThatThrownBy(spyEphemeryGenesisFile::updateGenesis)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Unable to update ephemery genesis file.");
    verify(spyEphemeryGenesisFile).updateGenesis();
  }
}
