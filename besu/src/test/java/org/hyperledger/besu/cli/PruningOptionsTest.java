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
 */
package org.hyperledger.besu.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.trie.forest.pruner.PrunerConfiguration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class PruningOptionsTest extends CommandTestAbstract {

  @Disabled
  public void pruningIsEnabledIfSyncModeIsFast() {
    parseCommand("--sync-mode", "FAST");

    verify(mockControllerBuilder).isPruningEnabled(true);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Disabled
  public void pruningIsDisabledIfSyncModeIsFull() {
    parseCommand("--sync-mode", "FULL");

    verify(mockControllerBuilder).isPruningEnabled(false);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void pruningEnabledExplicitly() {
    parseCommand("--pruning-enabled", "--sync-mode=FULL");

    verify(mockControllerBuilder).isPruningEnabled(true);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Disabled
  public void pruningDisabledExplicitly() {
    parseCommand("--pruning-enabled=false", "--sync-mode=FAST");

    verify(mockControllerBuilder).isPruningEnabled(false);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void pruningDisabledByDefault() {
    parseCommand();

    verify(mockControllerBuilder).isPruningEnabled(false);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void pruningParametersAreCaptured() throws Exception {
    parseCommand(
        "--pruning-enabled", "--pruning-blocks-retained=15", "--pruning-block-confirmations=4");

    final ArgumentCaptor<PrunerConfiguration> pruningArg =
        ArgumentCaptor.forClass(PrunerConfiguration.class);

    verify(mockControllerBuilder).pruningConfiguration(pruningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    assertThat(pruningArg.getValue().getBlocksRetained()).isEqualTo(15);
    assertThat(pruningArg.getValue().getBlockConfirmations()).isEqualTo(4);
  }

  @Test
  public void pruningLogsDeprecationWarningWithForest() {
    parseCommand("--pruning-enabled", "--data-storage-format=FOREST");

    verify(mockControllerBuilder).isPruningEnabled(true);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockLogger)
        .warn(
            contains(
                "Forest pruning is deprecated and will be removed soon."
                    + " To save disk space consider switching to Bonsai data storage format."));
  }

  @Test
  public void pruningLogsIgnoredWarningWithBonsai() {
    parseCommand("--pruning-enabled", "--data-storage-format=BONSAI");

    verify(mockControllerBuilder).isPruningEnabled(true);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    verify(mockLogger).warn(contains("Forest pruning is ignored with Bonsai data storage format."));
  }
}
