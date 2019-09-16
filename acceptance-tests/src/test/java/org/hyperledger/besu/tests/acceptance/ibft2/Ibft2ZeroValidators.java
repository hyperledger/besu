/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.ibft2;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.io.IOException;

import org.junit.Test;

public class Ibft2ZeroValidators extends AcceptanceTestBase {

  @Test
  public void zeroValidatorsFormValidCluster() throws IOException {
    final String[] validators = {};
    final BesuNode node1 = besu.createIbft2NodeWithValidators("node1", validators);
    final BesuNode node2 = besu.createIbft2NodeWithValidators("node2", validators);

    cluster.start(node1, node2);

    cluster.verify(net.awaitPeerCount(1));
  }
}
