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
package org.hyperledger.besu.consensus.clique.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.jsonrpc.AbstractVoteProposerMethod;
import org.hyperledger.besu.consensus.common.jsonrpc.AbstractVoteProposerMethodTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CliqueProposalsTest extends AbstractVoteProposerMethodTest {

  private CliqueProposals method;

  @Override
  protected AbstractVoteProposerMethod getMethod() {
    return method;
  }

  @Override
  protected String getMethodName() {
    return "clique_proposals";
  }

  @BeforeEach
  public void setup() {
    method = new CliqueProposals(getValidatorProvider());
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(getMethodName());
  }
}
