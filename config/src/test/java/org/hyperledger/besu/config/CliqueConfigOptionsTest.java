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
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

public class CliqueConfigOptionsTest {

  private static final long EXPECTED_DEFAULT_EPOCH_LENGTH = 30_000;
  private static final int EXPECTED_DEFAULT_BLOCK_PERIOD = 15;

  @Test
  public void shouldGetEpochLengthFromConfig() {
    final CliqueConfigOptions config = fromConfigOptions(singletonMap("epochlength", 10_000));
    assertThat(config.getEpochLength()).isEqualTo(10_000);
  }

  @Test
  public void shouldFallbackToDefaultEpochLength() {
    final CliqueConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.getEpochLength()).isEqualTo(EXPECTED_DEFAULT_EPOCH_LENGTH);
  }

  @Test
  public void shouldGetDefaultEpochLengthFromDefaultConfig() {
    assertThat(JsonCliqueConfigOptions.DEFAULT.getEpochLength())
        .isEqualTo(EXPECTED_DEFAULT_EPOCH_LENGTH);
  }

  @Test
  public void shouldGetBlockPeriodFromConfig() {
    final CliqueConfigOptions config = fromConfigOptions(singletonMap("blockperiodseconds", 5));
    assertThat(config.getBlockPeriodSeconds()).isEqualTo(5);
  }

  @Test
  public void shouldFallbackToDefaultBlockPeriod() {
    final CliqueConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.getBlockPeriodSeconds()).isEqualTo(EXPECTED_DEFAULT_BLOCK_PERIOD);
  }

  @Test
  public void shouldGetDefaultBlockPeriodFromDefaultConfig() {
    assertThat(JsonCliqueConfigOptions.DEFAULT.getBlockPeriodSeconds())
        .isEqualTo(EXPECTED_DEFAULT_BLOCK_PERIOD);
  }

  @Test
  public void shouldThrowOnNonPositiveBlockPeriod() {
    final CliqueConfigOptions config = fromConfigOptions(singletonMap("blockperiodseconds", -1));
    assertThatThrownBy(() -> config.getBlockPeriodSeconds())
        .isInstanceOf(IllegalArgumentException.class);
  }

  private CliqueConfigOptions fromConfigOptions(final Map<String, Object> cliqueConfigOptions) {
    final ObjectNode rootNode = JsonUtil.createEmptyObjectNode();
    final ObjectNode configNode = JsonUtil.createEmptyObjectNode();
    final ObjectNode options = JsonUtil.objectNodeFromMap(cliqueConfigOptions);
    configNode.set("clique", options);
    rootNode.set("config", configNode);
    return GenesisConfig.fromConfig(rootNode).getConfigOptions().getCliqueConfigOptions();
  }
}
