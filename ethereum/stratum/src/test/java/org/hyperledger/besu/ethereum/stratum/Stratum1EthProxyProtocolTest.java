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
package org.hyperledger.besu.ethereum.stratum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class Stratum1EthProxyProtocolTest {

  private Stratum1EthProxyProtocol protocol;
  private StratumConnection conn;
  private List<String> receivedMessages;

  @Before
  public void setUp() {
    MiningCoordinator coordinator = mock(PoWMiningCoordinator.class);
    protocol = new Stratum1EthProxyProtocol(coordinator);
    receivedMessages = new ArrayList<>();
    conn = new StratumConnection(new StratumProtocol[0], null, receivedMessages::add);
  }

  @Test
  public void testCanHandleEmptyString() {
    assertThat(protocol.maybeHandle("", conn)).isFalse();
  }

  @Test
  public void testCanHandleMalformedJSON() {
    assertThat(protocol.maybeHandle("{[\"foo\",", conn)).isFalse();
  }

  @Test
  public void testCanHandleWrongMethod() {
    assertThat(
            protocol.maybeHandle(
                "{\"id\":0,\"method\":\"eth_byebye\",\"params\":[\"0xdeadbeefdeadbeef.worker\"]}",
                conn))
        .isFalse();
  }

  @Test
  public void testCanHandleWellFormedRequest() {
    assertThat(
            protocol.maybeHandle(
                "{\"id\":0,\"method\":\"eth_submitLogin\",\"params\":[\"0xdeadbeefdeadbeef.worker\"]}",
                conn))
        .isTrue();
  }
}
