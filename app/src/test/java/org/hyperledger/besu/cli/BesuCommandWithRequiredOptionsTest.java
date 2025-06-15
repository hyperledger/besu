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
package org.hyperledger.besu.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class BesuCommandWithRequiredOptionsTest extends CommandTestAbstract {

  @Test
  public void presentRequiredOptionShouldPass() {
    parseCommandWithRequiredOption("--accept-terms-and-conditions", "true");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void missingRequiredOptionShouldFail() {
    parseCommandWithRequiredOption();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Missing required option: '--accept-terms-and-conditions=<acceptTermsAndConditions>'");
  }

  @Test
  public void havingRequiredOptionInEnvVarShouldFail() {
    setEnvironmentVariable("BESU_ACCEPT_TERMS_AND_CONDITIONS", "true");
    parseCommandWithRequiredOption();
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Missing required option: '--accept-terms-and-conditions=<acceptTermsAndConditions>'");
  }

  @Test
  public void havingRequiredOptionInConfigShouldFail() throws IOException {
    final Path toml = createTempFile("toml", "accept-terms-and-conditions=true\n");
    parseCommandWithRequiredOption("--config-file", toml.toString());
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith(
            "Missing required option: '--accept-terms-and-conditions=<acceptTermsAndConditions>'");
  }
}
