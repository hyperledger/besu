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

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

public class IbftConfigOptionsTest {

  private static final int EXPECTED_DEFAULT_EPOCH_LENGTH = 30_000;
  private static final int EXPECTED_DEFAULT_BLOCK_PERIOD = 1;
  private static final int EXPECTED_DEFAULT_REQUEST_TIMEOUT = 1;
  private static final int EXPECTED_DEFAULT_GOSSIPED_HISTORY_LIMIT = 1000;
  private static final int EXPECTED_DEFAULT_MESSAGE_QUEUE_LIMIT = 1000;

  @Test
  public void shouldGetEpochLengthFromConfig() {
    final IbftConfigOptions config = fromConfigOptions(singletonMap("EpochLength", 10_000));
    assertThat(config.getEpochLength()).isEqualTo(10_000);
  }

  @Test
  public void shouldFallbackToDefaultEpochLength() {
    final IbftConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.getEpochLength()).isEqualTo(EXPECTED_DEFAULT_EPOCH_LENGTH);
  }

  @Test
  public void shouldGetDefaultEpochLengthFromDefaultConfig() {
    assertThat(IbftConfigOptions.DEFAULT.getEpochLength()).isEqualTo(EXPECTED_DEFAULT_EPOCH_LENGTH);
  }

  @Test
  public void shouldGetBlockPeriodFromConfig() {
    final IbftConfigOptions config = fromConfigOptions(singletonMap("BlockPeriodSeconds", 5));
    assertThat(config.getBlockPeriodSeconds()).isEqualTo(5);
  }

  @Test
  public void shouldFallbackToDefaultBlockPeriod() {
    final IbftConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.getBlockPeriodSeconds()).isEqualTo(EXPECTED_DEFAULT_BLOCK_PERIOD);
  }

  @Test
  public void shouldGetDefaultBlockPeriodFromDefaultConfig() {
    assertThat(IbftConfigOptions.DEFAULT.getBlockPeriodSeconds())
        .isEqualTo(EXPECTED_DEFAULT_BLOCK_PERIOD);
  }

  @Test
  public void shouldGetRequestTimeoutFromConfig() {
    final IbftConfigOptions config = fromConfigOptions(singletonMap("RequestTimeoutSeconds", 5));
    assertThat(config.getRequestTimeoutSeconds()).isEqualTo(5);
  }

  @Test
  public void shouldFallbackToDefaultRequestTimeout() {
    final IbftConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.getRequestTimeoutSeconds()).isEqualTo(EXPECTED_DEFAULT_REQUEST_TIMEOUT);
  }

  @Test
  public void shouldGetDefaultRequestTimeoutFromDefaultConfig() {
    assertThat(IbftConfigOptions.DEFAULT.getRequestTimeoutSeconds())
        .isEqualTo(EXPECTED_DEFAULT_REQUEST_TIMEOUT);
  }

  @Test
  public void shouldGetGossipedHistoryLimitFromConfig() {
    final IbftConfigOptions config = fromConfigOptions(singletonMap("GossipedHistoryLimit", 100));
    assertThat(config.getGossipedHistoryLimit()).isEqualTo(100);
  }

  @Test
  public void shouldFallbackToDefaultGossipedHistoryLimit() {
    final IbftConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.getGossipedHistoryLimit()).isEqualTo(EXPECTED_DEFAULT_GOSSIPED_HISTORY_LIMIT);
  }

  @Test
  public void shouldGetDefaultGossipedHistoryLimitFromDefaultConfig() {
    assertThat(IbftConfigOptions.DEFAULT.getGossipedHistoryLimit())
        .isEqualTo(EXPECTED_DEFAULT_GOSSIPED_HISTORY_LIMIT);
  }

  @Test
  public void shouldGetMessageQueueLimitFromConfig() {
    final IbftConfigOptions config = fromConfigOptions(singletonMap("MessageQueueLimit", 100));
    assertThat(config.getMessageQueueLimit()).isEqualTo(100);
  }

  @Test
  public void shouldFallbackToDefaultMessageQueueLimit() {
    final IbftConfigOptions config = fromConfigOptions(emptyMap());
    assertThat(config.getMessageQueueLimit()).isEqualTo(EXPECTED_DEFAULT_MESSAGE_QUEUE_LIMIT);
  }

  @Test
  public void shouldGetDefaultMessageQueueLimitFromDefaultConfig() {
    assertThat(IbftConfigOptions.DEFAULT.getMessageQueueLimit())
        .isEqualTo(EXPECTED_DEFAULT_MESSAGE_QUEUE_LIMIT);
  }

  private IbftConfigOptions fromConfigOptions(final Map<String, Object> ibftConfigOptions) {
    return GenesisConfigFile.fromConfig(
            new JsonObject(singletonMap("config", singletonMap("ibft", ibftConfigOptions))))
        .getConfigOptions()
        .getIbftLegacyConfigOptions();
  }
}
