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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;

import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.buffer.Buffer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StratumConnectionTest {

  @Mock PoWMiningCoordinator miningCoordinator;
  private EpochCalculator epochCalculator;

  @BeforeEach
  public void setup() {
    this.epochCalculator = new EpochCalculator.DefaultEpochCalculator();
  }

  @Test
  public void testNoSuitableProtocol() {
    StratumConnection conn = new StratumConnection(new StratumProtocol[] {}, notification -> {});
    assertThatThrownBy(() -> conn.handleBuffer(Buffer.buffer("{}\n"), bytes -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testStratum1WithoutMatches() {
    when(miningCoordinator.getEpochCalculator()).thenReturn(epochCalculator);
    StratumConnection conn =
        new StratumConnection(
            new StratumProtocol[] {new Stratum1Protocol("", miningCoordinator)},
            notification -> {});
    assertThatThrownBy(() -> conn.handleBuffer(Buffer.buffer("{}\n"), bytes -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testStratum1Matches() {
    when(miningCoordinator.getEpochCalculator()).thenReturn(epochCalculator);
    AtomicReference<Object> message = new AtomicReference<>();

    StratumConnection conn =
        new StratumConnection(
            new StratumProtocol[] {
              new Stratum1Protocol("", miningCoordinator, () -> "abcd", () -> "abcd")
            },
            notification -> {});
    conn.handleBuffer(
        Buffer.buffer(
            "{"
                + "  \"id\": 23,"
                + "  \"method\": \"mining.subscribe\", "
                + "  \"params\": [ "
                + "    \"MinerName/1.0.0\", \"EthereumStratum/1.0.0\" "
                + "  ]"
                + "}\n"),
        message::set);

    assertThat(message.get())
        .isEqualTo(
            "{\"jsonrpc\":\"2.0\",\"id\":23,\"result\":[[\"mining.notify\",\"abcd\",\"EthereumStratum/1.0.0\"],\"\"]}");
  }

  @Test
  public void testStratum1SendWork() {
    when(miningCoordinator.getEpochCalculator()).thenReturn(epochCalculator);
    AtomicReference<Object> message = new AtomicReference<>();

    Stratum1Protocol protocol =
        new Stratum1Protocol("", miningCoordinator, () -> "abcd", () -> "abcd");

    StratumConnection conn = new StratumConnection(new StratumProtocol[] {protocol}, message::set);
    conn.handleBuffer(
        Buffer.buffer(
            "{"
                + "  \"id\": 23,"
                + "  \"method\": \"mining.subscribe\", "
                + "  \"params\": [ "
                + "    \"MinerName/1.0.0\", \"EthereumStratum/1.0.0\" "
                + "  ]"
                + "}\n"),
        message::set);
    conn.handleBuffer(
        Buffer.buffer(
            "{"
                + "  \"id\": null,"
                + "  \"method\": \"mining.authorize\", "
                + "  \"params\": [ "
                + "    \"someusername\", \"password\" "
                + "  ]"
                + "}\n"),
        message::set);
    // now send work without waiting.
    protocol.setCurrentWorkTask(
        new PoWSolverInputs(UInt256.valueOf(3), Bytes.fromHexString("deadbeef"), 42));

    assertThat(message.get())
        .isEqualTo(
            "{\"jsonrpc\":\"2.0\",\"method\":\"mining.notify\",\"params\":[\"abcd\",\"0xdeadbeef\",\"0x0000000000000000000000000000000000000000000000000000000000000000\",\"0x0000000000000000000000000000000000000000000000000000000000000003\",true],\"id\":null}");
  }

  @Test
  public void testStratum1SubmitHashrate() {
    when(miningCoordinator.getEpochCalculator()).thenReturn(epochCalculator);
    AtomicReference<Object> message = new AtomicReference<>();

    Stratum1Protocol protocol =
        new Stratum1Protocol("", miningCoordinator, () -> "abcd", () -> "abcd");

    Mockito.when(miningCoordinator.submitHashRate("0x02", 3L)).thenReturn(true);

    StratumConnection conn =
        new StratumConnection(new StratumProtocol[] {protocol}, notification -> {});
    conn.handleBuffer(
        Buffer.buffer(
            "{"
                + "  \"id\": 23,"
                + "  \"method\": \"mining.subscribe\", "
                + "  \"params\": [ "
                + "    \"MinerName/1.0.0\", \"EthereumStratum/1.0.0\" "
                + "  ]"
                + "}\n"),
        message::set);
    conn.handleBuffer(
        Buffer.buffer(
            "{"
                + "  \"id\": 23,"
                + "  \"method\": \"eth_submitHashrate\", "
                + "  \"params\": [ "
                + "    \"0x03\",\"0x02\" "
                + "  ]"
                + "}\n"),
        message::set);
    assertThat(message.get()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":23,\"result\":true}");
  }
}
