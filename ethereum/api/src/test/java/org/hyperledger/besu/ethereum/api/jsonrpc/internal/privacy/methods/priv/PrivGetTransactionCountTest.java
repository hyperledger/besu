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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import org.junit.Test;

public class PrivGetTransactionCountTest {

  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private final String privacyGroupId =
      BytesValues.asBase64String(BytesValue.wrap("0x123".getBytes(UTF_8)));

  private final Address senderAddress =
      Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57");
  private final long NONCE = 5;

  @Test
  public void verifyTransactionCount() {
    final PrivateTransactionHandler privateTransactionHandler =
        mock(PrivateTransactionHandler.class);
    when(privateTransactionHandler.getSenderNonce(senderAddress, privacyGroupId)).thenReturn(NONCE);

    final PrivGetTransactionCount privGetTransactionCount =
        new PrivGetTransactionCount(parameters, privateTransactionHandler);

    final Object[] params = new Object[] {senderAddress, privacyGroupId};
    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_getTransactionCount", params);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionCount.response(request);

    assertThat(response.getResult()).isEqualTo(String.format("0x%X", NONCE));
  }
}
