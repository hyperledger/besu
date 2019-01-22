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
package tech.pegasys.pantheon.orion;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.pantheon.orion.types.ReceiveContent;
import tech.pegasys.pantheon.orion.types.ReceiveResponse;
import tech.pegasys.pantheon.orion.types.SendContent;
import tech.pegasys.pantheon.orion.types.SendResponse;

import java.io.IOException;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OrionTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private static final String PAYLOAD = "a wonderful transaction";
  private static Orion orion;

  private static OrionTestHarness testHarness;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    folder.create();

    testHarness = OrionTestHarness.create(folder.newFolder().toPath());

    OrionConfiguration orionConfiguration = OrionConfiguration.createDefault();
    orionConfiguration.setUrl(testHarness.getConfig().clientUrl().toString());

    orion = new Orion(orionConfiguration);
  }

  @AfterClass
  public static void tearDownOnce() {
    testHarness.getOrion().stop();
  }

  @Test
  public void testUpCheck() throws IOException {
    assertTrue(orion.upCheck());
  }

  @Test
  public void testSendAndReceive() throws IOException {
    List<String> publicKeys = testHarness.getPublicKeys();

    SendContent sc = new SendContent(PAYLOAD, publicKeys.get(0), new String[] {publicKeys.get(1)});
    SendResponse sr = orion.send(sc);

    ReceiveContent rc = new ReceiveContent(sr.getKey(), publicKeys.get(1));
    ReceiveResponse rr = orion.receive(rc);

    assertEquals(PAYLOAD, new String(rr.getPayload(), UTF_8));
  }

  @Test(expected = IOException.class)
  public void whenUpCheckFailsThrows() throws IOException {
    OrionConfiguration configuration = OrionConfiguration.createDefault();
    configuration.setUrl("http:");

    Orion broken = new Orion(configuration);

    broken.upCheck();
  }
}
