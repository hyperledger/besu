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
package tech.pegasys.pantheon.ethereum.mainnet.precompiles.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.enclave.types.SendResponse;
import tech.pegasys.pantheon.ethereum.mainnet.SpuriousDragonGasCalculator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivacyPrecompiledContractIntegrationTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private static final String PAYLOAD = "a wonderful transaction";
  private static Enclave enclave;

  private static OrionTestHarness testHarness;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    folder.create();

    testHarness = OrionTestHarness.create(folder.newFolder().toPath());

    enclave = new Enclave(testHarness.clientUrl());
  }

  @AfterClass
  public static void tearDownOnce() {
    testHarness.getOrion().stop();
  }

  @Test
  public void testUpCheck() throws IOException {
    assertTrue(enclave.upCheck());
  }

  @Test
  public void testSendAndReceive() throws IOException {
    List<String> publicKeys = testHarness.getPublicKeys();

    SendRequest sc =
        new SendRequest(PAYLOAD, publicKeys.get(0), Lists.newArrayList(publicKeys.get(1)));
    SendResponse sr = enclave.send(sc);

    PrivacyPrecompiledContract privacyPrecompiledContract =
        new PrivacyPrecompiledContract(
            new SpuriousDragonGasCalculator(), publicKeys.get(0), enclave);

    BytesValue result =
        privacyPrecompiledContract.compute(BytesValue.wrap(sr.getKey().getBytes(UTF_8)));

    String expected = new String(result.extractArray(), UTF_8);

    assertEquals(PAYLOAD, expected);
  }
}
