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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.privacyMarkerTransaction;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetPrivateTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.DefaultPrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.enclave.testutil.EnclaveKeyConfiguration;
import org.hyperledger.enclave.testutil.OrionTestHarness;
import org.hyperledger.enclave.testutil.OrionTestHarnessFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivGetPrivateTransactionIntegrationTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  private final EnclavePublicKeyProvider enclavePublicKeyProvider = (user) -> ENCLAVE_PUBLIC_KEY;
  private final PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);
  private final Blockchain blockchain = mock(Blockchain.class);

  private final Address sender =
      Address.fromHexString("0x0000000000000000000000000000000000000003");

  private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();

  private final KeyPair KEY_PAIR =
      signatureAlgorithm.createKeyPair(
          signatureAlgorithm.createPrivateKey(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private final PrivateTransaction privateTransaction =
      PrivateTransaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(null)
          .value(Wei.ZERO)
          .payload(
              Bytes.fromHexString(
                  "0x608060405234801561001057600080fd5b5060d08061001f60003960"
                      + "00f3fe60806040526004361060485763ffffffff7c01000000"
                      + "00000000000000000000000000000000000000000000000000"
                      + "60003504166360fe47b18114604d5780636d4ce63c14607557"
                      + "5b600080fd5b348015605857600080fd5b5060736004803603"
                      + "6020811015606d57600080fd5b50356099565b005b34801560"
                      + "8057600080fd5b506087609e565b6040805191825251908190"
                      + "0360200190f35b600055565b6000549056fea165627a7a7230"
                      + "5820cb1d0935d14b589300b12fcd0ab849a7e9019c81da24d6"
                      + "daa4f6b2f003d1b0180029"))
          .sender(sender)
          .chainId(BigInteger.valueOf(2018))
          .privateFrom(Bytes.wrap(ENCLAVE_PUBLIC_KEY.getBytes(UTF_8)))
          .privateFor(
              Lists.newArrayList(
                  Bytes.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8))))
          .restriction(Restriction.RESTRICTED)
          .signAndBuild(KEY_PAIR);

  private Vertx vertx = Vertx.vertx();
  private OrionTestHarness testHarness;
  private Enclave enclave;
  private PrivacyController privacyController;

  @Before
  public void setUp() throws Exception {
    folder.create();

    vertx = Vertx.vertx();

    testHarness =
        OrionTestHarnessFactory.create(
            "enclave",
            folder.newFolder().toPath(),
            new EnclaveKeyConfiguration("enclave_key_0.pub", "enclave_key_0.key"));

    testHarness.start();

    final EnclaveFactory factory = new EnclaveFactory(vertx);
    enclave = factory.createVertxEnclave(testHarness.clientUrl());

    privacyController =
        new DefaultPrivacyController(
            blockchain, privateStateStorage, enclave, null, null, null, null, null, null);
  }

  @After
  public void tearDown() {
    testHarness.close();
    vertx.close();
  }

  @Test
  public void returnsStoredPrivateTransaction() {
    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(privacyController, enclavePublicKeyProvider);

    final Hash blockHash = Hash.ZERO;
    final Transaction pmt = spy(privacyMarkerTransaction());
    when(blockchain.getTransactionByHash(eq(pmt.getHash()))).thenReturn(Optional.of(pmt));
    when(blockchain.getTransactionLocation(eq(pmt.getHash())))
        .thenReturn(Optional.of(new TransactionLocation(blockHash, 0)));

    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getHash()).thenReturn(blockHash);
    when(blockchain.getBlockHeader(eq(blockHash))).thenReturn(Optional.of(blockHeader));

    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);

    final String payload = Base64.getEncoder().encodeToString(bvrlp.encoded().toArrayUnsafe());
    final ArrayList<String> to = Lists.newArrayList("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");
    final SendResponse sendResponse = enclave.send(payload, ENCLAVE_PUBLIC_KEY, to);

    final Bytes hexKey = Bytes.fromBase64String(sendResponse.getKey());
    when(pmt.getPayload()).thenReturn(hexKey);

    final Object[] params = new Object[] {pmt.getHash()};

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_getPrivateTransaction", params));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);
    final PrivateTransactionLegacyResult result =
        (PrivateTransactionLegacyResult) response.getResult();

    assertThat(new PrivateTransactionLegacyResult(this.privateTransaction))
        .isEqualToComparingFieldByField(result);
  }
}
