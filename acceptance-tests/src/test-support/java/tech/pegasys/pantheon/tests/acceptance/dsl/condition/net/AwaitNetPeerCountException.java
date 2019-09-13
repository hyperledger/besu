/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.dsl.condition.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.net.NetPeerCountTransaction;

public class AwaitNetPeerCountException implements Condition {

  private final NetPeerCountTransaction transaction;

  public AwaitNetPeerCountException(final NetPeerCountTransaction transaction) {
    this.transaction = transaction;
  }

  @Override
  public void verify(final Node node) {
    final Throwable thrown = catchThrowable(() -> node.execute(transaction));
    assertThat(thrown).isInstanceOf(RuntimeException.class);
    assertThat(thrown).hasMessageContaining("P2P has been disabled.");
  }
}
