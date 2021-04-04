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
 *
 */
package org.hyperledger.besu.tests.acceptance.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SECP256R1;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import org.junit.Before;
import org.junit.Test;

public class SECP256R1AcceptanceTest extends AcceptanceTestBase {
  private Node minerNode;
  private Node fullNode;

  protected static final String GENESIS_FILE = "/crypto/secp256r1.json";

  @Before
  public void setUp() throws Exception {
    minerNode = besu.createCustomGenesisNode("node1", GENESIS_FILE, true, true);
    fullNode = besu.createCustomGenesisNode("node2", GENESIS_FILE, false);
    cluster.start(minerNode, fullNode);
  }

  @Test
  public void shouldConnectToOtherPeer() {
    minerNode.verify(net.awaitPeerCount(1));
    fullNode.verify(net.awaitPeerCount(1));
  }

  @Test
  public void transactionShouldBeSuccessful() {
    final Account recipient = accounts.createAccount("recipient");

    final Hash transactionHash =
        minerNode.execute(accountTransactions.createTransfer(recipient, 5), new SECP256R1());
    assertThat(transactionHash).isNotNull();
    cluster.verify(recipient.balanceEquals(5));
  }
}
