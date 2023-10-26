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
 *
 */

package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/** The Data storage CLI options. */
public class DataStorageOptions implements CLIOptions<DataStorageConfiguration> {

  private static final String DATA_STORAGE_FORMAT = "--data-storage-format";

  private static final String BONSAI_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD =
      "--bonsai-historical-block-limit";

  // Use Bonsai DB
  @Option(
      names = {DATA_STORAGE_FORMAT},
      description =
          "Format to store trie data in.  Either FOREST or BONSAI (default: ${DEFAULT-VALUE}).",
      arity = "1")
  private final DataStorageFormat dataStorageFormat = DataStorageFormat.FOREST;

  @Option(
      names = {BONSAI_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD, "--bonsai-maximum-back-layers-to-load"},
      paramLabel = "<LONG>",
      description =
          "Limit of historical layers that can be loaded with BONSAI (default: ${DEFAULT-VALUE}).",
      arity = "1")
  private final Long bonsaiMaxLayersToLoad = DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD;

  @CommandLine.ArgGroup(validate = false)
  private final DataStorageOptions.Unstable unstableOptions = new Unstable();

  static class Unstable {
    @CommandLine.Option(
        hidden = true,
        names = {"--Xbonsai-trie-log-retention-threshold"},
        description =
            "The number of blocks for which to retain trie logs. 0 is unlimited. (default: ${DEFAULT-VALUE})")
    private long bonsaiTrieLogRententionThreshold = DEFAULT_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;
  }

  /**
   * Create data storage options.
   *
   * @return the data storage options
   */
  public static DataStorageOptions create() {
    return new DataStorageOptions();
  }

  @Override
  public DataStorageConfiguration toDomainObject() {
    return ImmutableDataStorageConfiguration.builder()
        .dataStorageFormat(dataStorageFormat)
        .bonsaiMaxLayersToLoad(bonsaiMaxLayersToLoad)
        .unstable(
            ImmutableDataStorageConfiguration.Unstable.builder()
                .bonsaiTrieLogRetentionThreshold(unstableOptions.bonsaiTrieLogRententionThreshold)
                .build())
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return List.of(
        DATA_STORAGE_FORMAT,
        dataStorageFormat.toString(),
        BONSAI_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD,
        bonsaiMaxLayersToLoad.toString());
  }

  /**
   * Normalize data storage format string.
   *
   * @return the normalized string
   */
  public String normalizeDataStorageFormat() {
    return StringUtils.capitalize(dataStorageFormat.toString().toLowerCase());
  }
}
