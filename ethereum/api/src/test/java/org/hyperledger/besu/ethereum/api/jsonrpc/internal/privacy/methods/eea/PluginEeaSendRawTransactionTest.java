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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PluginEeaSendRawTransactionTest extends BaseEeaSendRawTransaction {

  PluginEeaSendRawTransaction method;

  @Before
  public void before() {
    method = new PluginEeaSendRawTransaction(transactionPool, privacyController, (user) -> "");
  }

  @Test
  public void validUnrestrictedTransactionIsSentToTransactionPool() {
    when(privacyController.createPrivateMarkerTransactionPayload(any(), any(), any()))
        .thenReturn("");
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.createPrivateMarkerTransaction(any(), any(), any()))
        .thenReturn(PUBLIC_TRANSACTION);

    when(transactionPool.addLocalTransaction(any())).thenReturn(ValidationResult.valid());

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            "0x221e930a2c18d91fca4d509eaa3512f3e01fef266f660e32473de67474b36c15");

    final JsonRpcResponse actualResponse =
        method.response(validUnrestrictedPrivacyGroupTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addLocalTransaction(PUBLIC_TRANSACTION);
    verify(privacyController)
        .createPrivateMarkerTransaction(any(), any(), eq(Address.PLUGIN_PRIVACY));
  }
}
