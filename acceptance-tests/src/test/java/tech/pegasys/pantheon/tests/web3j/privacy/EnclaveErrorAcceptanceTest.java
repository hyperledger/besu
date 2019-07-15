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
package tech.pegasys.pantheon.tests.web3j.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static tech.pegasys.pantheon.tests.web3j.privacy.PrivacyGroup.generatePrivacyGroup;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNet;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.PrivateTransactionBuilder;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.PrivateTransactionBuilder.TransactionType;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.util.Base64;

import com.google.common.collect.Lists;
import net.consensys.cava.crypto.sodium.Box;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EnclaveErrorAcceptanceTest extends PrivacyAcceptanceTestBase {
  protected static final String CONTRACT_NAME = "Event Emitter";

  private EventEmitterHarness eventEmitterHarness;
  private PrivacyNet privacyNet;
  private BytesValue wrongPublicKey;
  private BytesValue privacyGroup;

  @Before
  public void setUp() throws Exception {
    privacyNet =
        PrivacyNet.builder(privacy, privacyPantheon, cluster, false).addMinerNode("Alice").build();
    privacyNet.startPrivacyNet();
    privacyGroup = generatePrivacyGroup(privacyNet, "Alice");
    eventEmitterHarness =
        new EventEmitterHarness(
            privateTransactionBuilder,
            privacyNet,
            privateTransactions,
            privateTransactionVerifier,
            eea);
    wrongPublicKey =
        BytesValues.fromBase64(
            Base64.getEncoder().encode(Box.KeyPair.random().publicKey().bytesArray()));
  }

  @Test
  @SuppressWarnings("MissingFail")
  public void enclaveNoMatchingPrivateKeyError() {

    final String invalidDeploy =
        PrivateTransactionBuilder.builder()
            .nonce(privacyNet.getNode("Alice").nextNonce(privacyGroup))
            .from(privacyNet.getNode("Alice").getAddress())
            .privateFrom(wrongPublicKey)
            .keyPair(privacyNet.getNode("Alice").keyPair())
            .build(TransactionType.CREATE_CONTRACT);

    final Throwable thrown =
        catchThrowable(
            () ->
                privacyNet
                    .getNode("Alice")
                    .execute(privateTransactions.createPrivateRawTransaction(invalidDeploy)));

    assertThat(thrown)
        .hasMessageContaining(JsonRpcError.ENCLAVE_NO_MATCHING_PRIVATE_KEY.getMessage());
  }

  @Test
  @SuppressWarnings("MissingFail")
  public void enclaveNoPeerUrlError() {

    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice");

    final String invalidStore =
        PrivateTransactionBuilder.builder()
            .nonce(privacyNet.getNode("Alice").nextNonce(privacyGroup))
            .from(privacyNet.getNode("Alice").getAddress())
            .privateFrom(
                BytesValues.fromBase64(privacyNet.getEnclave("Alice").getPublicKeys().get(0)))
            .privateFor(Lists.newArrayList(wrongPublicKey))
            .keyPair(privacyNet.getNode("Alice").keyPair())
            .build(TransactionType.CREATE_CONTRACT);

    final Throwable thrown =
        catchThrowable(
            () ->
                privacyNet
                    .getNode("Alice")
                    .execute(privateTransactions.createPrivateRawTransaction(invalidStore)));

    assertThat(thrown).hasMessageContaining(JsonRpcError.NODE_MISSING_PEER_URL.getMessage());
  }

  @After
  public void tearDown() {
    privacyNet.stopPrivacyNet();
  }
}
