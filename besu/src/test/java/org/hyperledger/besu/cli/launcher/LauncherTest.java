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
package org.hyperledger.besu.cli.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.cli.BesuCommand;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import net.consensys.quorum.mainnet.launcher.model.LauncherScript;
import net.consensys.quorum.mainnet.launcher.model.Step;
import org.junit.Test;

public class LauncherTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void launcherDefaultValuesAreAvailable() throws IOException {
    final LauncherScript script =
        MAPPER.readValue(
            (BesuCommand.class.getResourceAsStream("launcher.json")), LauncherScript.class);
    assertThat(isStepValid(List.of(script.getSteps()))).isTrue();
  }

  @SuppressWarnings("ReturnValueIgnored")
  private boolean isStepValid(final List<Step> steps) {
    for (Step step : steps) {
      if (step.getAvailableOptions() != null) {
        try {
          List<String> split = Splitter.on('$').splitToList(step.getAvailableOptions());
          if (split.size() > 1) {
            Class.forName(split.get(0)).getField(split.get(1));
          } else {
            Class.forName(step.getAvailableOptions()).getEnumConstants();
          }
        } catch (Exception exception) {
          exception.printStackTrace();
          return false;
        }
      }
      if (!isStepValid(step.getSubQuestions())) {
        return false;
      }
    }
    return true;
  }
}
