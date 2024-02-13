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

package org.hyperledger.besu.cli.options.stable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.MINIMUM_BONSAI_TRIE_LOG_RETENTION_LIMIT;

import org.hyperledger.besu.cli.options.AbstractCLIOptionsTest;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;

import org.junit.jupiter.api.Test;

public class DataStorageOptionsTest
    extends AbstractCLIOptionsTest<DataStorageConfiguration, DataStorageOptions> {

  @Test
  public void bonsaiTrieLogPruningLimitOption() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(dataStorageConfiguration.getUnstable().getBonsaiTrieLogPruningWindowSize())
                .isEqualTo(600),
        "--Xbonsai-limit-trie-logs-enabled",
        "--Xbonsai-trie-logs-pruning-window-size",
        "600");
  }

  @Test
  public void bonsaiTrieLogsEnabled_explicitlySetToFalse() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(dataStorageConfiguration.getUnstable().getBonsaiLimitTrieLogsEnabled())
                .isEqualTo(false),
        "--Xbonsai-limit-trie-logs-enabled=false");
  }

  @Test
  public void bonsaiTrieLogPruningWindowSizeShouldBePositive() {
    internalTestFailure(
        "--Xbonsai-trie-logs-pruning-window-size=0 must be greater than 0",
        "--Xbonsai-limit-trie-logs-enabled",
        "--Xbonsai-trie-logs-pruning-window-size",
        "0");
  }

  @Test
  public void bonsaiTrieLogPruningWindowSizeShouldBeAboveRetentionLimit() {
    internalTestFailure(
        "--Xbonsai-trie-logs-pruning-window-size=512 must be greater than --bonsai-historical-block-limit=512",
        "--Xbonsai-limit-trie-logs-enabled",
        "--Xbonsai-trie-logs-pruning-window-size",
        "512");
  }

  @Test
  public void bonsaiTrieLogRetentionLimitOption() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(dataStorageConfiguration.getBonsaiMaxLayersToLoad())
                .isEqualTo(MINIMUM_BONSAI_TRIE_LOG_RETENTION_LIMIT + 1),
        "--Xbonsai-limit-trie-logs-enabled",
        "--bonsai-historical-block-limit",
        "513");
  }

  @Test
  public void bonsaiTrieLogRetentionLimitOption_boundaryTest() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(dataStorageConfiguration.getBonsaiMaxLayersToLoad())
                .isEqualTo(MINIMUM_BONSAI_TRIE_LOG_RETENTION_LIMIT),
        "--Xbonsai-limit-trie-logs-enabled",
        "--bonsai-historical-block-limit",
        "512");
  }

  @Test
  public void bonsaiTrieLogRetentionLimitShouldBeAboveMinimum() {
    internalTestFailure(
        "--bonsai-historical-block-limit minimum value is 512",
        "--Xbonsai-limit-trie-logs-enabled",
        "--bonsai-historical-block-limit",
        "511");
  }

  @Test
  public void bonsaiCodeUsingCodeHashEnabledCanBeEnabled() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(
                    dataStorageConfiguration.getUnstable().getBonsaiCodeStoredByCodeHashEnabled())
                .isEqualTo(true),
        "--Xbonsai-code-using-code-hash-enabled",
        "true");
  }

  @Test
  public void bonsaiCodeUsingCodeHashEnabledCanBeDisabled() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(
                    dataStorageConfiguration.getUnstable().getBonsaiCodeStoredByCodeHashEnabled())
                .isEqualTo(false),
        "--Xbonsai-code-using-code-hash-enabled",
        "false");
  }

  @Override
  protected DataStorageConfiguration createDefaultDomainObject() {
    return DataStorageConfiguration.DEFAULT_CONFIG;
  }

  @Override
  protected DataStorageConfiguration createCustomizedDomainObject() {
    return ImmutableDataStorageConfiguration.builder()
        .dataStorageFormat(DataStorageFormat.BONSAI)
        .bonsaiMaxLayersToLoad(513L)
        .unstable(
            ImmutableDataStorageConfiguration.Unstable.builder()
                .bonsaiLimitTrieLogsEnabled(true)
                .bonsaiTrieLogPruningWindowSize(514)
                .build())
        .build();
  }

  @Override
  protected DataStorageOptions optionsFromDomainObject(
      final DataStorageConfiguration domainObject) {
    return DataStorageOptions.fromConfig(domainObject);
  }

  @Override
  protected DataStorageOptions getOptionsFromBesuCommand(final TestBesuCommand besuCommand) {
    return besuCommand.getDataStorageOptions();
  }
}
