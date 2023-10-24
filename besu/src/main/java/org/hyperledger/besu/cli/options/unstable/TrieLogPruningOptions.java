/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.cli.options.unstable;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogPrunerConfiguration;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

/** The trie log pruning CLI options. */
public class TrieLogPruningOptions implements CLIOptions<TrieLogPrunerConfiguration> {

  private static final String TRIE_LOG_PRUNING_ENABLED_FLAG = "--Xtrie-log-pruning-enabled";

  @CommandLine.Option(
      hidden = true,
      names = {TRIE_LOG_PRUNING_ENABLED_FLAG},
      description = "Enable trie log pruning (default: ${DEFAULT-VALUE})")
  private final Boolean trieLogPruningEnabled = Boolean.FALSE;

  /**
   * Create trie log pruning options.
   *
   * @return the trie log pruning options
   */
  public static TrieLogPruningOptions create() {
    return new TrieLogPruningOptions();
  }

  /**
   * Gets trie log pruning enabled.
   *
   * @return the trie log pruning enabled
   */
  public Boolean getTrieLogPruningEnabled() {
    return trieLogPruningEnabled;
  }

  @Override
  public TrieLogPrunerConfiguration toDomainObject() {
    return new TrieLogPrunerConfiguration(trieLogPruningEnabled);
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(TRIE_LOG_PRUNING_ENABLED_FLAG, trieLogPruningEnabled.toString());
  }
}
