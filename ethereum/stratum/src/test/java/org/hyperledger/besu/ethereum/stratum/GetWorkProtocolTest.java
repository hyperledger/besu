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

import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.buffer.Buffer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

public class GetWorkProtocolTest {

  @Test
  public void testCanHandleGetWorkMessage() {
    String message = "{\"method\":\"eth_getWork\",\"id\":1}";
    EpochCalculator epochCalculator = mock(EpochCalculator.class);
    GetWorkProtocol protocol = new GetWorkProtocol(epochCalculator);
    assertThat(
            protocol.maybeHandle(
                Buffer.buffer(message),
                new StratumConnection(new StratumProtocol[0], null),
                (msg) -> {}))
        .isTrue();
  }

  @Test
  public void testCanHandleSubmitWorkMessage() {
    String message = "{\"method\":\"eth_submitWork\",\"id\":1}";
    EpochCalculator epochCalculator = mock(EpochCalculator.class);
    GetWorkProtocol protocol = new GetWorkProtocol(epochCalculator);
    assertThat(
            protocol.maybeHandle(
                Buffer.buffer(message),
                new StratumConnection(new StratumProtocol[0], null),
                (msg) -> {}))
        .isTrue();
  }

  @Test
  public void testCanHandleBadRequest() {
    String message = "bad-request";
    EpochCalculator epochCalculator = mock(EpochCalculator.class);
    GetWorkProtocol protocol = new GetWorkProtocol(epochCalculator);
    assertThat(
            protocol.maybeHandle(
                Buffer.buffer(message),
                new StratumConnection(new StratumProtocol[0], null),
                (msg) -> {}))
        .isFalse();
  }

  @Test
  public void testCanGetSolutions() {
    AtomicReference<Object> messageRef = new AtomicReference<>();
    StratumConnection connection = new StratumConnection(new StratumProtocol[0], null);

    GetWorkProtocol protocol = new GetWorkProtocol(new EpochCalculator.DefaultEpochCalculator());
    AtomicReference<PoWSolution> solutionFound = new AtomicReference<>();
    protocol.setSubmitCallback(
        (sol) -> {
          solutionFound.set(sol);
          return true;
        });
    protocol.setCurrentWorkTask(
        new PoWSolverInputs(UInt256.ZERO, Bytes.fromHexString("0xdeadbeef"), 123L));
    String requestWork = "{\"method\":\"eth_getWork\",\"id\":1}";
    protocol.handle(connection, Buffer.buffer(requestWork), messageRef::set);
    assertThat(messageRef.get())
        .isEqualTo(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[\"0xdeadbeef\",\"0x0000000000000000000000000000000000000000000000000000000000000000\",\"0x0000000000000000000000000000000000000000000000000000000000000000\",\"0x7b\"]}");

    String submitWork =
        "{\"method\":\"eth_submitWork\",\"id\":1,\"params\":[\"0xdeadbeefdeadbeef\", \"0x0000000000000000000000000000000000000000000000000000000000000000\", \"0x0000000000000000000000000000000000000000000000000000000000000000\"]}";
    assertThat(
            protocol.maybeHandle(
                Buffer.buffer(submitWork),
                new StratumConnection(new StratumProtocol[0], null),
                messageRef::set))
        .isTrue();
    protocol.handle(connection, Buffer.buffer(submitWork), messageRef::set);
    assertThat(messageRef.get()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":true}");
  }
}
