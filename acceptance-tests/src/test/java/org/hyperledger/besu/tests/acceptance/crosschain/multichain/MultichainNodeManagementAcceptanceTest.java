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
package org.hyperledger.besu.tests.acceptance.crosschain.multichain;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.crosschain.common.CrosschainAcceptanceTestBase;

import java.math.BigInteger;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class MultichainNodeManagementAcceptanceTest extends CrosschainAcceptanceTestBase {
  @Before
  public void setUp() throws Exception {
    setUpCoordiantionChain();
    setUpBlockchain1();
    setUpBlockchain2();
  }

  @After
  public void closeDown() throws Exception {
    this.cluster.close();
    this.clusterBc1.close();
    this.clusterBc2.close();
  }

  // TODO check that invalid IP address and port fail. This test would fail at the moment as there
  // is no check for validity of IP and port.

  @Test
  public void addOneNode() {
    BigInteger bcA = BigInteger.TEN;

    this.nodeOnBlockchain1.execute(crossTransactions.getAddMultichainNode(bcA, "127.0.0.1:8545"));
    List<BigInteger> nodes =
        this.nodeOnBlockchain1.execute(crossTransactions.getListMultichainNodes());
    assertThat(nodes.size()).isEqualTo(1);
    assertThat(nodes.contains(bcA)).isTrue();
  }

  @Test
  public void addTwoNodes() {
    BigInteger bcA = BigInteger.TEN;
    BigInteger bcB = BigInteger.ONE;

    this.nodeOnBlockchain1.execute(crossTransactions.getAddMultichainNode(bcA, "127.0.0.1:8545"));
    this.nodeOnBlockchain1.execute(crossTransactions.getAddMultichainNode(bcB, "127.0.0.1:8546"));
    List<BigInteger> nodes =
        this.nodeOnBlockchain1.execute(crossTransactions.getListMultichainNodes());
    assertThat(nodes.size()).isEqualTo(2);
    assertThat(nodes.contains(bcA)).isTrue();
    assertThat(nodes.contains(bcB)).isTrue();
  }

  @Test
  public void removeNode() {
    BigInteger bcA = BigInteger.TEN;
    BigInteger bcB = BigInteger.ONE;

    this.nodeOnBlockchain1.execute(crossTransactions.getAddMultichainNode(bcA, "127.0.0.1:8545"));
    this.nodeOnBlockchain1.execute(crossTransactions.getAddMultichainNode(bcB, "127.0.0.1:8546"));
    this.nodeOnBlockchain1.execute(crossTransactions.getRemoveMultichainNode(bcA));
    List<BigInteger> nodes =
        this.nodeOnBlockchain1.execute(crossTransactions.getListMultichainNodes());
    assertThat(nodes.size()).isEqualTo(1);
    assertThat(nodes.contains(bcB)).isTrue();
  }

  @Test
  public void removeNonExistantNode() {
    BigInteger bcA = BigInteger.TEN;
    // This should not throw any error.
    this.nodeOnBlockchain1.execute(crossTransactions.getRemoveMultichainNode(bcA));
  }

  @Test
  public void listWhenEmpty() {
    List<BigInteger> nodes =
        this.nodeOnBlockchain1.execute(crossTransactions.getListMultichainNodes());
    assertThat(nodes.size()).isEqualTo(0);
  }

  @Test
  public void configureUseAcceptanceTestSystem() {
    // Check that this doesn't cause an exception.
    addMultichainNode(this.nodeOnBlockchain1, this.nodeOnBlockchain2);
  }
}
