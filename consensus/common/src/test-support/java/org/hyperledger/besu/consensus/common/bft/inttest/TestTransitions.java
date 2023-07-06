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
package org.hyperledger.besu.consensus.common.bft.inttest;

import org.hyperledger.besu.config.BftFork;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.TransitionsConfigOptions;

import java.util.Collections;
import java.util.List;

public class TestTransitions extends TransitionsConfigOptions {

  private final List<QbftFork> qbftForks;
  private final List<BftFork> ibftForks;

  public static TestTransitions createQbftTestTransitions(final List<QbftFork> qbftForks) {
    return new TestTransitions(Collections.emptyList(), qbftForks);
  }

  public static TestTransitions createIbftTestTransitions(final List<BftFork> bftForks) {
    return new TestTransitions(bftForks, Collections.emptyList());
  }

  public TestTransitions(final List<BftFork> ibftForks, final List<QbftFork> qbftForks) {
    super(JsonUtil.createEmptyObjectNode());
    this.ibftForks = ibftForks;
    this.qbftForks = qbftForks;
  }

  @Override
  public List<QbftFork> getQbftForks() {
    return qbftForks;
  }

  @Override
  public List<BftFork> getIbftForks() {
    return ibftForks;
  }
}
