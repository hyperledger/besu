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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.priv;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionHandler;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

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

    assertEquals(String.format("0x%X", NONCE), response.getResult());
  }
}
