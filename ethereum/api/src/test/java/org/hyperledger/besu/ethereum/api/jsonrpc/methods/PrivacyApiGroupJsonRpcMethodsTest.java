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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.PRIVACY_NOT_ENABLED;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.MultiTenancyRpcMethodDecorator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivacyApiGroupJsonRpcMethodsTest {
  @Mock private JsonRpcMethod rpcMethod;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private ProtocolSchedule<?> protocolSchedule;
  @Mock private TransactionPool transactionPool;
  @Mock private PrivacyParameters privacyParameters;

  private PrivacyApiGroupJsonRpcMethods privacyApiGroupJsonRpcMethods;

  @Before
  public void setup() {
    when(rpcMethod.getName()).thenReturn("priv_method");
    privacyApiGroupJsonRpcMethods = createPrivacyApiGroupJsonRpcMethods();
  }

  @Test
  public void rpcMethodsCreatedWhenMultiTenancyIsEnabledHaveMultiTenancyValidator() {
    final Map<String, JsonRpcMethod> rpcMethods = privacyApiGroupJsonRpcMethods.create();
    final JsonRpcMethod privMethod = rpcMethods.get("priv_method");

    assertThat(privMethod).isNotSameAs(rpcMethod);
    assertThat(privMethod.getClass()).hasSameClassAs(MultiTenancyRpcMethodDecorator.class);
  }

  @Test
  public void rpcsCreatedWithoutMultiTenancyUseOriginalRpcMethod() {
    when(privacyParameters.isEnabled()).thenReturn(true);
    final Map<String, JsonRpcMethod> rpcMethods = privacyApiGroupJsonRpcMethods.create();
    final JsonRpcMethod privMethod = rpcMethods.get("priv_method");

    assertThat(privMethod).isSameAs(rpcMethod);
  }

  @Test
  public void rpcMethodsCreatedWhenPrivacyIsNotEnabledAreDisabled() {
    final Map<String, JsonRpcMethod> rpcMethods = privacyApiGroupJsonRpcMethods.create();
    assertThat(rpcMethods).hasSize(1);

    final JsonRpcMethod privMethod = rpcMethods.get("priv_method");
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "priv_method", null));
    final JsonRpcResponse response = privMethod.response(request);
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.ERROR);

    JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(PRIVACY_NOT_ENABLED);
  }

  private PrivacyApiGroupJsonRpcMethods createPrivacyApiGroupJsonRpcMethods() {
    return new PrivacyApiGroupJsonRpcMethods(
        blockchainQueries, protocolSchedule, transactionPool, privacyParameters) {

      @Override
      protected RpcApi getApiGroup() {
        return RpcApis.PRIV;
      }

      @Override
      protected Map<String, JsonRpcMethod> create(final PrivacyController privacyController) {
        return mapOf(rpcMethod);
      }
    };
  }
}
