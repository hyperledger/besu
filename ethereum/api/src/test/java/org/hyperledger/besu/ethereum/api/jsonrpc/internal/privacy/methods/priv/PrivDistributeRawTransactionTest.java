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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.SendTransactionResponse;
import org.hyperledger.besu.util.bytes.BytesValues;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivDistributeRawTransactionTest {

  private static final String VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP =
      "0xf8ac800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "80801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff"
          + "759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56"
          + "c0b28ad43601b4ab949f53faa07bd2c804a0035695b4cc4b0941"
          + "e60551d7a19cf30603db5bfc23e5ac43a56f57f25f75486aa00f"
          + "200e885ff29e973e2576b6600181d1b0a2b5294e30d9be4a1981"
          + "ffb33a0b8c8a72657374726963746564";

  @Mock private PrivDistributeRawTransaction method;
  @Mock private PrivacyController privacyController;

  @Before
  public void before() {
    method = new PrivDistributeRawTransaction(privacyController);
  }

  @Test
  public void validTransactionHashReturnedAfterDistribute() {
    final String enclavePublicKey = "93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=";
    when(privacyController.sendTransaction(any(PrivateTransaction.class), any()))
        .thenReturn(new SendTransactionResponse(enclavePublicKey, ""));
    when(privacyController.validatePrivateTransaction(
            any(PrivateTransaction.class), any(String.class), any()))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "priv_distributeRawTransaction",
                new String[] {VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP}));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(), BytesValues.fromBase64(enclavePublicKey).toString());

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(privacyController).sendTransaction(any(PrivateTransaction.class), any());
    verify(privacyController)
        .validatePrivateTransaction(any(PrivateTransaction.class), any(String.class), any());
  }
}
