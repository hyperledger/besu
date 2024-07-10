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
package org.hyperledger.besu.services.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.metrics.noop.NoOpMetricsSystem.NO_OP_COUNTER;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class CompleterStageTest {

  private final Pipe<String> pipe =
      new Pipe<>(10, NO_OP_COUNTER, NO_OP_COUNTER, NO_OP_COUNTER, "test_pipe");
  private final List<String> output = new ArrayList<>();
  private final CompleterStage<String> stage = new CompleterStage<>("name", pipe, output::add);

  @Test
  public void shouldAddItemsToOutputUntilPipeHasNoMore() {
    pipe.put("a");
    pipe.put("b");
    pipe.put("c");
    pipe.close();

    stage.run();

    assertThat(output).containsExactly("a", "b", "c");
  }
}
