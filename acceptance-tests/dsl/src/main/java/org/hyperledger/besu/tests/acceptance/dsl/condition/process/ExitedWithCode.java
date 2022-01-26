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
package org.hyperledger.besu.tests.acceptance.dsl.condition.process;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExitedWithCode implements Condition {

  private static final Logger LOG = LoggerFactory.getLogger(ExitedWithCode.class);

  private final int code;

  public ExitedWithCode(final int code) {
    this.code = code;
  }

  @Override
  public void verify(final Node node) {
    assertThat(node instanceof RunnableNode).isTrue();
    Optional<Integer> exitCode = ((RunnableNode) node).exitCode();
    assertThat(exitCode.isPresent()).isTrue();
    LOG.info("Exit code was {}", exitCode.get());
    assertThat(exitCode.get()).isEqualTo(this.code);
  }
}
