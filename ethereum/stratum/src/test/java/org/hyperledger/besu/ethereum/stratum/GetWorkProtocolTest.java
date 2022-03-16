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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.NoopMiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class GetWorkProtocolTest {

  @Test
  public void testCanHandleGetWorkMessage() {
    String message =
        "POST / HTTP/1.1\r\nHost: localhost:8000\r\nContent-Type:application/json\r\n\r\n {\"method\":\"eth_getWork\",\"id\":1}";
    MiningCoordinator coordinator = mock(PoWMiningCoordinator.class);
    GetWorkProtocol protocol = new GetWorkProtocol(coordinator);
    assertThat(
            protocol.maybeHandle(
                message, new StratumConnection(new StratumProtocol[0], () -> {}, (msg) -> {})))
        .isTrue();
  }

  @Test
  public void testCanHandleSubmitWorkMessage() {
    String message =
        "POST / HTTP/1.1\r\nHost: localhost:8000\r\nContent-Type:application/json\r\n\r\n {\"method\":\"eth_submitWork\",\"id\":1}";
    MiningCoordinator coordinator = mock(PoWMiningCoordinator.class);
    GetWorkProtocol protocol = new GetWorkProtocol(coordinator);
    assertThat(
            protocol.maybeHandle(
                message, new StratumConnection(new StratumProtocol[0], () -> {}, (msg) -> {})))
        .isTrue();
  }

  @Test
  public void testCanHandleNotPost() {
    String message =
        "DELETE / HTTP/1.1\r\nHost: localhost:8000\r\nContent-Type:application/json\r\n\r\n {\"method\":\"eth_getWork\",\"id\":1}";
    MiningCoordinator coordinator = mock(PoWMiningCoordinator.class);
    GetWorkProtocol protocol = new GetWorkProtocol(coordinator);
    assertThat(
            protocol.maybeHandle(
                message, new StratumConnection(new StratumProtocol[0], () -> {}, (msg) -> {})))
        .isFalse();
  }

  @Test
  public void testCanHandleBadGet() {
    String message = "GET / HTTP/1.1\r\nHost: localhost:8000\r\nContent-Type:text/plain\r\n\r\n";
    MiningCoordinator coordinator = mock(PoWMiningCoordinator.class);
    GetWorkProtocol protocol = new GetWorkProtocol(coordinator);
    assertThat(
            protocol.maybeHandle(
                message, new StratumConnection(new StratumProtocol[0], () -> {}, (msg) -> {})))
        .isFalse();
  }

  @Test
  public void testCanHandleNotHTTP() {
    String message = "{\"method\":\"eth_getWork\",\"id\":1}";
    MiningCoordinator coordinator = mock(PoWMiningCoordinator.class);
    GetWorkProtocol protocol = new GetWorkProtocol(coordinator);
    assertThat(
            protocol.maybeHandle(
                message, new StratumConnection(new StratumProtocol[0], () -> {}, (msg) -> {})))
        .isFalse();
  }

  @Test
  public void testCanGetSolutions() {
    MiningCoordinator coordinator =
        new NoopMiningCoordinator(
            new MiningParameters.Builder()
                .coinbase(Address.wrap(Bytes.random(20)))
                .minTransactionGasPrice(Wei.of(1))
                .extraData(Bytes.fromHexString("0xc0ffee"))
                .miningEnabled(true)
                .build());
    AtomicReference<String> messageRef = new AtomicReference<>();
    StratumConnection connection =
        new StratumConnection(new StratumProtocol[0], () -> {}, messageRef::set);

    GetWorkProtocol protocol = new GetWorkProtocol(coordinator);
    AtomicReference<PoWSolution> solutionFound = new AtomicReference<>();
    protocol.setSubmitCallback(
        (sol) -> {
          solutionFound.set(sol);
          return true;
        });
    protocol.setCurrentWorkTask(
        new PoWSolverInputs(UInt256.ZERO, Bytes.fromHexString("0xdeadbeef"), 123L));
    String requestWork =
        "POST / HTTP/1.1\r\nHost: localhost:8000\r\nContent-Type:application/json\r\n\r\n {\"method\":\"eth_getWork\",\"id\":1}";
    protocol.handle(connection, requestWork);
    assertThat(messageRef.get())
        .isEqualTo(
            "HTTP/1.1 200 OK\r\n"
                + "Connection: Keep-Alive\r\n"
                + "Keep-Alive: timeout=5, max=1000\r\n"
                + "Content-Length: 193\r\n"
                + "\r\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[\"0xdeadbeef\",\"0x0000000000000000000000000000000000000000000000000000000000000000\",\"0x0000000000000000000000000000000000000000000000000000000000000000\",\"0x7b\"]}");

    String submitWork =
        "POST / HTTP/1.1\r\nHost: localhost:8000\r\nContent-Type:application/json\r\n\r\n {\"method\":\"eth_submitWork\",\"id\":1,\"params\":[\"0xdeadbeefdeadbeef\", \"0x0000000000000000000000000000000000000000000000000000000000000000\", \"0x0000000000000000000000000000000000000000000000000000000000000000\"]}";
    assertThat(
            protocol.maybeHandle(
                submitWork, new StratumConnection(new StratumProtocol[0], () -> {}, (msg) -> {})))
        .isTrue();
    protocol.handle(connection, submitWork);
    assertThat(messageRef.get())
        .isEqualTo(
            "HTTP/1.1 200 OK\r\n"
                + "Connection: Keep-Alive\r\n"
                + "Keep-Alive: timeout=5, max=1000\r\n"
                + "Content-Length: 38\r\n\r\n{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":true}");
  }
}
