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

package org.hyperledger.besu.cli.options.unstable;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.ImmutableSnapSyncConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncConfiguration;

import java.util.List;

import picocli.CommandLine;

public class SnapSyncOptions implements CLIOptions<SnapSyncConfiguration> {

  private final String SNAP_ENABLED = "--Xsnapsync-enabled";

  @CommandLine.Option(
      hidden = true,
      names = {SNAP_ENABLED},
      description = "Enabled Snapsync",
      arity = "1")
  private Boolean snapsyncEnabled = Boolean.FALSE;

  public static SnapSyncOptions create() {
    return new SnapSyncOptions();
  }

  public static SnapSyncOptions fromConfig(final SnapSyncOptions snapSyncOptions) {
    final SnapSyncOptions cliOptions = new SnapSyncOptions();
    cliOptions.snapsyncEnabled = snapSyncOptions.snapsyncEnabled;
    return cliOptions;
  }

  @Override
  public SnapSyncConfiguration toDomainObject() {
    return ImmutableSnapSyncConfiguration.builder().snapSyncEnabled(snapsyncEnabled).build();
  }

  @Override
  public List<String> getCLIOptions() {
    return List.of(SNAP_ENABLED, String.valueOf(snapsyncEnabled));
  }
}
