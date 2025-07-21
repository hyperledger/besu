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
package org.hyperledger.besu.cli.options.storage;

import static org.hyperledger.besu.ethereum.worldstate.VerkleSubStorageConfiguration.VerkleUnstable.DEFAULT_STEM_FLAT_DB_ENABLED;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.worldstate.ImmutableVerkleSubStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.VerkleSubStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.List;

import picocli.CommandLine;
import picocli.CommandLine.Option;

/** The Data storage CLI options. */
public class VerkleSubStorageOptions implements CLIOptions<VerkleSubStorageConfiguration> {

  @CommandLine.ArgGroup(validate = false)
  private final VerkleSubStorageOptions.Unstable unstableOptions = new Unstable();

  /** Default Constructor. */
  VerkleSubStorageOptions() {}

  /** The unstable options for data storage. */
  public static class Unstable {

    @Option(
        hidden = true,
        names = {
          "--Xverkle-stem-flat-db-enabled",
        },
        arity = "1",
        description = "Enables stem flat database strategy for verkle. (default: ${DEFAULT-VALUE})")
    private Boolean stemFlatDbEnabled = DEFAULT_STEM_FLAT_DB_ENABLED;

    /** Default Constructor. */
    Unstable() {}
  }

  /**
   * Create data storage options.
   *
   * @return the data storage options
   */
  public static VerkleSubStorageOptions create() {
    return new VerkleSubStorageOptions();
  }

  /**
   * Validates the data storage options
   *
   * @param commandLine the full commandLine to check all the options specified by the user
   * @param dataStorageFormat the selected data storage format which determines the validation rules
   *     to apply.
   */
  public void validate(final CommandLine commandLine, final DataStorageFormat dataStorageFormat) {
    // no op
  }

  /**
   * Converts to options from the configuration
   *
   * @param domainObject to be reversed
   * @return the options that correspond to the configuration
   */
  public static VerkleSubStorageOptions fromConfig(
      final VerkleSubStorageConfiguration domainObject) {
    final VerkleSubStorageOptions dataStorageOptions = VerkleSubStorageOptions.create();
    dataStorageOptions.unstableOptions.stemFlatDbEnabled =
        domainObject.getUnstable().getStemFlatDbEnabled();
    return dataStorageOptions;
  }

  @Override
  public final VerkleSubStorageConfiguration toDomainObject() {
    return ImmutableVerkleSubStorageConfiguration.builder()
        .unstable(
            ImmutableVerkleSubStorageConfiguration.VerkleUnstable.builder()
                .stemFlatDbEnabled(unstableOptions.stemFlatDbEnabled)
                .build())
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new VerkleSubStorageOptions());
  }
}
