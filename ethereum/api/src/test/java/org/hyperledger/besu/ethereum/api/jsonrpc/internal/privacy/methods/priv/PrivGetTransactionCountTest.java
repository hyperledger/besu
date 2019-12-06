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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import org.junit.Before;
import org.junit.Test;

public class PrivGetTransactionCountTest {

  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final PrivateTransactionHandler privateTransactionHandler =
      mock(PrivateTransactionHandler.class);

  private final String privacyGroupId =
      BytesValues.asBase64String(BytesValue.wrap("0x123".getBytes(UTF_8)));

  private final Address senderAddress =
      Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57");
  private final long NONCE = 5;

  @Before
  public void before() {
    when(privacyParameters.isEnabled()).thenReturn(true);
    when(privateTransactionHandler.determineNonce(senderAddress, privacyGroupId)).thenReturn(NONCE);
  }

  @Test
  public void verifyTransactionCount() {
    final PrivGetTransactionCount privGetTransactionCount =
        new PrivGetTransactionCount(privacyParameters, privateTransactionHandler);

    final Object[] params = new Object[] {senderAddress, privacyGroupId};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_getTransactionCount", params));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionCount.response(request);

    assertThat(response.getResult()).isEqualTo(String.format("0x%X", NONCE));
  }

  @Test
  public void returnPrivacyDisabledErrorWhenPrivacyIsDisabled() {
    when(privacyParameters.isEnabled()).thenReturn(false);
    final PrivGetTransactionCount privGetTransactionCount =
        new PrivGetTransactionCount(privacyParameters, privateTransactionHandler);

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("1", "priv_getTransactionCount", new Object[] {}));
    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) privGetTransactionCount.response(request);

    assertThat(response.getError()).isEqualTo(JsonRpcError.PRIVACY_NOT_ENABLED);
  }
}
