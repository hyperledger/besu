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

import org.hyperledger.besu.ethereum.mainnet.EthHashSolverInputs;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.buffer.Buffer;
import org.junit.Test;

public class StratumConnectionTest {

  @Test
  public void testNoSuitableProtocol() {
    AtomicBoolean called = new AtomicBoolean(false);
    StratumConnection conn =
        new StratumConnection(new StratumProtocol[] {}, () -> called.set(true), bytes -> {});
    conn.handleBuffer(Buffer.buffer("{}\n"));
    assertThat(called.get()).isTrue();
  }

  @Test
  public void testStratum1WithoutMatches() {
    AtomicBoolean called = new AtomicBoolean(false);
    StratumConnection conn =
        new StratumConnection(
            new StratumProtocol[] {new Stratum1Protocol("")}, () -> called.set(true), bytes -> {});
    conn.handleBuffer(Buffer.buffer("{}\n"));
    assertThat(called.get()).isTrue();
  }

  @Test
  public void testStratum1Matches() {

    AtomicBoolean called = new AtomicBoolean(false);

    AtomicReference<String> message = new AtomicReference<>();

    StratumConnection conn =
        new StratumConnection(
            new StratumProtocol[] {new Stratum1Protocol("")}, () -> called.set(true), message::set);
    conn.handleBuffer(
        Buffer.buffer(
            "{"
                + "  \"id\": 23,"
                + "  \"method\": \"mining.subscribe\", "
                + "  \"params\": [ "
                + "    \"MinerName/1.0.0\", \"EthereumStratum/1.0.0\" "
                + "  ]"
                + "}\n"));
    assertThat(called.get()).isFalse();

    assertThat(message.get())
        .isEqualTo(
            "{\"id\":23,\"jsonrpc\":\"2.0\",\"result\":[[\"mining.notify\",\"ae6812eb4cd7735a302a8a9dd95cf71f\",\"EthereumStratum/1.0.0\"],\"080c\"],\"error\":null}\n");
  }

  @Test
  public void testStratum1SendWork() {

    AtomicBoolean called = new AtomicBoolean(false);

    AtomicReference<String> message = new AtomicReference<>();

    Stratum1Protocol protocol = new Stratum1Protocol("", () -> "abcd");

    StratumConnection conn =
        new StratumConnection(
            new StratumProtocol[] {protocol}, () -> called.set(true), message::set);
    conn.handleBuffer(
        Buffer.buffer(
            "{"
                + "  \"id\": 23,"
                + "  \"method\": \"mining.subscribe\", "
                + "  \"params\": [ "
                + "    \"MinerName/1.0.0\", \"EthereumStratum/1.0.0\" "
                + "  ]"
                + "}\n"));
    conn.handleBuffer(
        Buffer.buffer(
            "{"
                + "  \"id\": null,"
                + "  \"method\": \"mining.authorize\", "
                + "  \"params\": [ "
                + "    \"someusername\", \"password\" "
                + "  ]"
                + "}\n"));
    assertThat(called.get()).isFalse();
    // now send work without waiting.
    protocol.setCurrentWorkTask(
        new EthHashSolverInputs(
            UInt256.of(3), BytesValue.fromHexString("deadbeef").getArrayUnsafe(), 42));

    assertThat(message.get())
        .isEqualTo(
            "{\"id\":null,\"method\":\"mining.notify\",\"jsonrpc\":\"2.0\",\"params\":[\"abcd\",\"0xdeadbeef\",\"0x0000000000000000000000000000000000000000000000000000000000000000\",\"0x0000000000000000000000000000000000000000000000000000000000000003\",true]}");
  }
}
