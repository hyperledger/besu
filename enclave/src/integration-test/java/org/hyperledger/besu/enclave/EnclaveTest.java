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
package org.hyperledger.besu.enclave;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.enclave.testutil.EnclaveKeyConfiguration;
import org.hyperledger.enclave.testutil.TesseraTestHarness;
import org.hyperledger.enclave.testutil.TesseraTestHarnessFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import io.vertx.core.Vertx;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EnclaveTest {

  @TempDir private static Path folder;

  private static final String PAYLOAD = "a wonderful transaction";
  private static final String MOCK_KEY = "iOCzoGo5kwtZU0J41Z9xnGXHN6ZNukIa9MspvHtu3Jk=";
  private static Enclave enclave;
  private Vertx vertx;
  private EnclaveFactory factory;

  private static TesseraTestHarness testHarness;

  @BeforeEach
  public void setUp() throws Exception {
    vertx = Vertx.vertx();
    factory = new EnclaveFactory(vertx);

    testHarness =
        TesseraTestHarnessFactory.create(
            "enclave",
            Files.createTempDirectory(folder, "enclave"),
            new EnclaveKeyConfiguration("enclave_key_0.pub", "enclave_key_0.key"),
            Optional.empty());

    testHarness.start();

    enclave = factory.createVertxEnclave(testHarness.clientUrl());
  }

  @AfterEach
  public void tearDown() {
    testHarness.close();
    vertx.close();
  }

  @Test
  public void testUpCheck() {
    assertThat(enclave.upCheck()).isTrue();
  }

  @Test
  public void testReceiveThrowsWhenPayloadDoesNotExist() {
    final String publicKey = testHarness.getDefaultPublicKey();

    final Throwable t = catchThrowable(() -> enclave.receive(MOCK_KEY, publicKey));

    assertThat(t.getMessage()).isEqualTo("Message with hash was not found");
  }

  @Test
  public void testSendAndReceive() {
    final List<String> publicKeys = testHarness.getPublicKeys();

    final SendResponse sr =
        enclave.send(PAYLOAD, publicKeys.get(0), Lists.newArrayList(publicKeys.get(0)));

    final ReceiveResponse rr = enclave.receive(sr.getKey(), publicKeys.get(0));
    assertThat(rr).isNotNull();
    assertThat(new String(rr.getPayload(), UTF_8)).isEqualTo(PAYLOAD);
    assertThat(rr.getPrivacyGroupId()).isNotNull();
  }

  @Test
  public void testSendWithPrivacyGroupAndReceive() {
    final List<String> publicKeys = testHarness.getPublicKeys();

    final PrivacyGroup privacyGroupResponse =
        enclave.createPrivacyGroup(publicKeys, publicKeys.get(0), "", "");

    final SendResponse sr =
        enclave.send(PAYLOAD, publicKeys.get(0), privacyGroupResponse.getPrivacyGroupId());

    final ReceiveResponse rr = enclave.receive(sr.getKey(), publicKeys.get(0));
    assertThat(rr).isNotNull();
    assertThat(new String(rr.getPayload(), UTF_8)).isEqualTo(PAYLOAD);
    assertThat(rr.getPrivacyGroupId()).isNotNull();
  }

  @Test
  public void testCreateAndDeletePrivacyGroup() {
    final List<String> publicKeys = testHarness.getPublicKeys();
    final String name = "testName";
    final String description = "testDesc";

    final PrivacyGroup privacyGroupResponse =
        enclave.createPrivacyGroup(publicKeys, publicKeys.get(0), name, description);

    assertThat(privacyGroupResponse.getPrivacyGroupId()).isNotNull();
    assertThat(privacyGroupResponse.getName()).isEqualTo(name);
    assertThat(privacyGroupResponse.getDescription()).isEqualTo(description);
    assertThat(privacyGroupResponse.getType()).isEqualByComparingTo(PrivacyGroup.Type.PANTHEON);

    final String response =
        enclave.deletePrivacyGroup(privacyGroupResponse.getPrivacyGroupId(), publicKeys.get(0));

    assertThat(privacyGroupResponse.getPrivacyGroupId()).isEqualTo(response);
  }

  @Test
  public void testCreateFindDeleteFindPrivacyGroup() {
    final List<String> publicKeys = testHarness.getPublicKeys();
    final String name = "name";
    final String description = "desc";

    final PrivacyGroup privacyGroupResponse =
        enclave.createPrivacyGroup(publicKeys, publicKeys.get(0), name, description);

    assertThat(privacyGroupResponse.getPrivacyGroupId()).isNotNull();
    assertThat(privacyGroupResponse.getName()).isEqualTo(name);
    assertThat(privacyGroupResponse.getDescription()).isEqualTo(description);
    assertThat(privacyGroupResponse.getType()).isEqualTo(PrivacyGroup.Type.PANTHEON);

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              final PrivacyGroup[] findPrivacyGroupResponse = enclave.findPrivacyGroup(publicKeys);

              assertThat(findPrivacyGroupResponse.length).isEqualTo(1);
              assertThat(findPrivacyGroupResponse[0].getPrivacyGroupId())
                  .isEqualTo(privacyGroupResponse.getPrivacyGroupId());
            });

    final String response =
        enclave.deletePrivacyGroup(privacyGroupResponse.getPrivacyGroupId(), publicKeys.get(0));

    assertThat(privacyGroupResponse.getPrivacyGroupId()).isEqualTo(response);

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              final PrivacyGroup[] findPrivacyGroupResponse = enclave.findPrivacyGroup(publicKeys);

              assertThat(findPrivacyGroupResponse.length).isEqualTo(0);
            });
  }

  @Test
  public void testCreateDeleteRetrievePrivacyGroup() {
    final List<String> publicKeys = testHarness.getPublicKeys();
    final String name = "name";
    final String description = "desc";

    final PrivacyGroup privacyGroupResponse =
        enclave.createPrivacyGroup(publicKeys, publicKeys.get(0), name, description);

    assertThat(privacyGroupResponse.getPrivacyGroupId()).isNotNull();
    assertThat(privacyGroupResponse.getName()).isEqualTo(name);
    assertThat(privacyGroupResponse.getDescription()).isEqualTo(description);
    assertThat(privacyGroupResponse.getType()).isEqualTo(PrivacyGroup.Type.PANTHEON);

    final PrivacyGroup retrievePrivacyGroup =
        enclave.retrievePrivacyGroup(privacyGroupResponse.getPrivacyGroupId());

    assertThat(retrievePrivacyGroup).usingRecursiveComparison().isEqualTo(privacyGroupResponse);

    final String response =
        enclave.deletePrivacyGroup(privacyGroupResponse.getPrivacyGroupId(), publicKeys.get(0));

    assertThat(privacyGroupResponse.getPrivacyGroupId()).isEqualTo(response);
  }

  @Test
  public void upcheckReturnsFalseIfNoResponseReceived() throws URISyntaxException {
    assertThat(factory.createVertxEnclave(new URI("http://8.8.8.8:65535")).upCheck()).isFalse();
  }
}
