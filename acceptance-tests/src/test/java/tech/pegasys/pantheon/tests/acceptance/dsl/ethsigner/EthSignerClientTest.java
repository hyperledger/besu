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
package tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.testutil.EthSignerTestHarness;
import tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.testutil.EthSignerTestHarnessFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EthSignerClientTest {
  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();
  private static final String MOCK_RESPONSE = "mock_transaction_hash";
  private static final String MOCK_SEND_TRANSACTION_RESPONSE =
      "{\n"
          + "  \"id\":67,\n"
          + "  \"jsonrpc\":\"2.0\",\n"
          + "  \"result\": \""
          + MOCK_RESPONSE
          + "\"\n"
          + "}";
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  private static EthSignerClient ethSignerClient;

  private static EthSignerTestHarness testHarness;

  // The downstream server EthSigner should proxy requests to
  private static HttpServer mockServer;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    folder.create();

    testHarness =
        EthSignerTestHarnessFactory.create(
            folder.newFolder().toPath(),
            "ethSignerKey--fe3b557e8fb62b89f4916b721be55ceb828dbd73.json",
            1111,
            8545,
            2018);

    ethSignerClient = new EthSignerClient(testHarness.getHttpListeningUrl());

    mockServer = HttpServer.create(new InetSocketAddress(1111), 0);
    mockServer.createContext(
        "/",
        exchange -> {
          byte[] response = MOCK_SEND_TRANSACTION_RESPONSE.getBytes(UTF_8);
          exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    mockServer.start();
  }

  @Test
  public void testEthAccounts() throws IOException {
    final List<String> accounts = ethSignerClient.ethAccounts();
    assertEquals(1, accounts.size());
    assertEquals("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73", accounts.get(0));
  }

  @Test
  public void testEthSendTransaction() throws IOException {
    final String response =
        ethSignerClient.ethSendTransaction(
            "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO,
            "",
            BigInteger.ZERO);

    assertEquals(MOCK_RESPONSE, response);
  }

  @Test
  public void testEeaSendTransaction() throws IOException {
    final String response =
        ethSignerClient.eeaSendTransaction(
            "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
            BigInteger.ZERO,
            BigInteger.ZERO,
            "",
            BigInteger.ZERO,
            ENCLAVE_PUBLIC_KEY,
            Collections.emptyList(),
            "");

    assertEquals(MOCK_RESPONSE, response);
  }

  @AfterClass
  public static void bringDownOnce() {
    mockServer.stop(0);
  }
}
