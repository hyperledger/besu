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
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;

import org.hyperledger.besu.cli.options.AbstractCLIOptionsTest;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;

import org.junit.Test;

public class DataStorageOptionsTest
    extends AbstractCLIOptionsTest<DataStorageConfiguration, DataStorageOptions> {

  @Test
  public void bonsaiTrieLogPruningLimitOption() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(dataStorageConfiguration.getUnstable().getBonsaiTrieLogPruningLimit())
                .isEqualTo(1),
        "--Xbonsai-trie-log-pruning-enabled",
        "--Xbonsai-trie-log-pruning-limit",
        "1");
  }

  @Test
  public void bonsaiTrieLogPruningLimitShouldBePositive() {
    internalTestFailure(
        "--Xbonsai-trie-log-pruning-limit=0 must be greater than 0",
        "--Xbonsai-trie-log-pruning-enabled",
        "--Xbonsai-trie-log-pruning-limit",
        "0");
  }

  @Test
  public void bonsaiTrieLogRetentionThresholdOption() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(dataStorageConfiguration.getUnstable().getBonsaiTrieLogRetentionThreshold())
                .isEqualTo(MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD + 1),
        "--Xbonsai-trie-log-pruning-enabled",
        "--Xbonsai-trie-log-retention-threshold",
        "513");
  }

  @Test
  public void bonsaiTrieLogRetentionThresholdOption_boundaryTest() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(dataStorageConfiguration.getUnstable().getBonsaiTrieLogRetentionThreshold())
                .isEqualTo(MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD),
        "--Xbonsai-trie-log-pruning-enabled",
        "--Xbonsai-trie-log-retention-threshold",
        "512");
  }

  @Test
  public void bonsaiTrieLogRetentionThresholdShouldBeAboveMinimum() {
    internalTestFailure(
        "--Xbonsai-trie-log-retention-threshold minimum value is 512",
        "--Xbonsai-trie-log-pruning-enabled",
        "--Xbonsai-trie-log-retention-threshold",
        "511");
  }

  @Test
  public void bonsaiCodeUsingCodeHashEnabledOption() {
    internalTestSuccess(
        dataStorageConfiguration ->
            assertThat(
                    dataStorageConfiguration.getUnstable().getBonsaiCodeStoredByCodeHashEnabled())
                .isEqualTo(true),
        "--Xbonsai-code-using-code-hash-enabled");
  }

  @Override
  protected DataStorageConfiguration createDefaultDomainObject() {
    return DataStorageConfiguration.DEFAULT_CONFIG;
  }

  @Override
  protected DataStorageConfiguration createCustomizedDomainObject() {
    return ImmutableDataStorageConfiguration.builder()
        .dataStorageFormat(DataStorageFormat.BONSAI)
        .bonsaiMaxLayersToLoad(100L)
        .unstable(
            ImmutableDataStorageConfiguration.Unstable.builder()
                .bonsaiTrieLogPruningEnabled(true)
                .bonsaiTrieLogRetentionThreshold(1000L)
                .bonsaiTrieLogPruningLimit(20)
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
