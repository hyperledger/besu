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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.SendRequest;
import org.hyperledger.besu.enclave.types.SendRequestLegacy;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.api.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetPrivateTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.orion.testutil.OrionKeyConfiguration;
import org.hyperledger.orion.testutil.OrionTestHarness;
import org.hyperledger.orion.testutil.OrionTestHarnessFactory;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivGetPrivateTransactionIntegrationTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private static Enclave enclave;

  private static OrionTestHarness testHarness;

  private final TransactionWithMetadata returnedTransaction = mock(TransactionWithMetadata.class);

  private final Transaction justTransaction = mock(Transaction.class);

  @BeforeClass
  public static void setUpOnce() throws Exception {
    folder.create();

    testHarness =
        OrionTestHarnessFactory.create(
            folder.newFolder().toPath(),
            new OrionKeyConfiguration("orion_key_0.pub", "orion_key_0.key"));

    testHarness.start();

    enclave = new Enclave(testHarness.clientUrl());
  }

  @AfterClass
  public static void tearDownOnce() {
    testHarness.close();
  }

  private final Address sender =
      Address.fromHexString("0x0000000000000000000000000000000000000003");
  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
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
              BytesValue.fromHexString(
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
          .privateFrom(
              BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8)))
          .privateFor(
              Lists.newArrayList(
                  BytesValue.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=".getBytes(UTF_8))))
          .restriction(Restriction.RESTRICTED)
          .signAndBuild(KEY_PAIR);

  private final JsonRpcParameter parameters = new JsonRpcParameter();

  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);

  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);

  @Test
  public void returnsStoredPrivateTransaction() {

    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(blockchain, enclave, parameters, privacyParameters);

    when(blockchain.transactionByHash(any(Hash.class)))
        .thenReturn(Optional.of(returnedTransaction));
    when(returnedTransaction.getTransaction()).thenReturn(justTransaction);

    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);

    final SendRequest sendRequest =
        new SendRequestLegacy(
            Base64.getEncoder().encodeToString(bvrlp.encoded().extractArray()),
            "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=",
            Lists.newArrayList("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="));
    final SendResponse sendResponse = enclave.send(sendRequest);

    final BytesValue hexKey = BytesValues.fromBase64(sendResponse.getKey());
    when(justTransaction.getPayload()).thenReturn(hexKey);

    final Object[] params = new Object[] {Hash.ZERO};

    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_getPrivateTransaction", params);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);
    final PrivateTransactionLegacyResult result =
        (PrivateTransactionLegacyResult) response.getResult();

    assertThat(new PrivateTransactionLegacyResult(privateTransaction))
        .isEqualToComparingFieldByField(result);
  }
}
