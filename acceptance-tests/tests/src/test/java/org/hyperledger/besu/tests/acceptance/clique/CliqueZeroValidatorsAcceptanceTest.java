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
package org.hyperledger.besu.tests.acceptance.clique;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;

import org.junit.jupiter.api.Test;

public class CliqueZeroValidatorsAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void zeroValidatorsFormValidCluster() throws IOException {
    final String[] signers = {};
    final BesuNode node1 = besu.createCliqueNodeWithValidators("node1", signers);
    final BesuNode node2 = besu.createCliqueNodeWithValidators("node2", signers);

    cluster.start(node1, node2);

    cluster.verify(net.awaitPeerCount(1));
  }
}
