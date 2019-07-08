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
package tech.pegasys.pantheon.enclave;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.orion.testutil.OrionTestHarnessFactory;
import tech.pegasys.pantheon.enclave.types.CreatePrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.DeletePrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.enclave.types.SendRequestLegacy;
import tech.pegasys.pantheon.enclave.types.SendRequestPantheon;
import tech.pegasys.pantheon.enclave.types.SendResponse;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EnclaveTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private static final String PAYLOAD = "a wonderful transaction";
  private static Enclave enclave;

  private static OrionTestHarness testHarness;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    folder.create();

    testHarness =
        OrionTestHarnessFactory.create(
            folder.newFolder().toPath(), "orion_key_0.pub", "orion_key_0.key");

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
  public void testSendAndReceive() throws Exception {
    List<String> publicKeys = testHarness.getPublicKeys();

    SendRequest sc =
        new SendRequestLegacy(PAYLOAD, publicKeys.get(0), Lists.newArrayList(publicKeys.get(0)));
    SendResponse sr = enclave.send(sc);

    ReceiveRequest rc = new ReceiveRequest(sr.getKey(), publicKeys.get(0));
    ReceiveResponse rr = enclave.receive(rc);

    assertEquals(PAYLOAD, new String(rr.getPayload(), UTF_8));
    assertNotNull(rr.getPrivacyGroupId());
  }

  @Test
  public void testSendWithPrivacyGroupAndReceive() throws Exception {
    List<String> publicKeys = testHarness.getPublicKeys();

    CreatePrivacyGroupRequest privacyGroupRequest =
        new CreatePrivacyGroupRequest(publicKeys.toArray(new String[0]), publicKeys.get(0), "", "");

    PrivacyGroup privacyGroupResponse = enclave.createPrivacyGroup(privacyGroupRequest);

    SendRequest sc =
        new SendRequestPantheon(
            PAYLOAD, publicKeys.get(0), privacyGroupResponse.getPrivacyGroupId());
    SendResponse sr = enclave.send(sc);

    ReceiveRequest rc = new ReceiveRequest(sr.getKey(), publicKeys.get(0));
    ReceiveResponse rr = enclave.receive(rc);

    assertEquals(PAYLOAD, new String(rr.getPayload(), UTF_8));
    assertNotNull(rr.getPrivacyGroupId());
  }

  @Test
  public void testCreateAndDeletePrivacyGroup() throws Exception {
    List<String> publicKeys = testHarness.getPublicKeys();
    String name = "testName";
    String description = "testDesc";
    CreatePrivacyGroupRequest privacyGroupRequest =
        new CreatePrivacyGroupRequest(
            publicKeys.toArray(new String[0]), publicKeys.get(0), name, description);

    PrivacyGroup privacyGroupResponse = enclave.createPrivacyGroup(privacyGroupRequest);

    assertNotNull(privacyGroupResponse.getPrivacyGroupId());
    assertEquals(name, privacyGroupResponse.getName());
    assertEquals(description, privacyGroupResponse.getDescription());
    assertEquals(PrivacyGroup.Type.PANTHEON, privacyGroupResponse.getType());

    DeletePrivacyGroupRequest deletePrivacyGroupRequest =
        new DeletePrivacyGroupRequest(privacyGroupResponse.getPrivacyGroupId(), publicKeys.get(0));

    String response = enclave.deletePrivacyGroup(deletePrivacyGroupRequest);

    assertEquals(response, privacyGroupResponse.getPrivacyGroupId());
  }

  @Test(expected = IOException.class)
  public void whenUpCheckFailsThrows() throws IOException {
    Enclave broken = new Enclave(URI.create("http://null"));

    broken.upCheck();
  }
}
