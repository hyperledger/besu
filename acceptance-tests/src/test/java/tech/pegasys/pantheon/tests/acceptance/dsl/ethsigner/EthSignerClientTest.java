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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.testutil.EthSignerTestHarness;
import tech.pegasys.pantheon.tests.acceptance.dsl.ethsigner.testutil.EthSignerTestHarnessFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EthSignerClientTest {
  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  @ClassRule
  public static final WireMockRule wireMockRule =
      new WireMockRule(wireMockConfig().dynamicPort().dynamicPort());

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

  @BeforeClass
  public static void setUpOnce() throws Exception {
    stubFor(post("/").willReturn(aResponse().withBody(MOCK_SEND_TRANSACTION_RESPONSE)));

    folder.create();

    testHarness =
        EthSignerTestHarnessFactory.create(
            folder.newFolder().toPath(),
            "ethSignerKey--fe3b557e8fb62b89f4916b721be55ceb828dbd73.json",
            wireMockRule.port(),
            2018);

    ethSignerClient = new EthSignerClient(testHarness.getHttpListeningUrl());
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
            "restricted");

    assertEquals(MOCK_RESPONSE, response);
  }
}
