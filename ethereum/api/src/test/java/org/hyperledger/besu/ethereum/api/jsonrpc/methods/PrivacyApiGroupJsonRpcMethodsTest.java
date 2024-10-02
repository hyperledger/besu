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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.PRIVACY_NOT_ENABLED;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.MultiTenancyRpcMethodDecorator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyPrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;
import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;

import java.util.Map;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.impl.UserImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PrivacyApiGroupJsonRpcMethodsTest {
  private static final String DEFAULT_ENCLAVE_PUBLIC_KEY =
      "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  @Mock private JsonRpcMethod rpcMethod;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private TransactionPool transactionPool;
  @Mock private PrivacyParameters privacyParameters;

  private TestPrivacyApiGroupJsonRpcMethods privacyApiGroupJsonRpcMethods;

  @BeforeEach
  public void setup() {
    when(rpcMethod.getName()).thenReturn("priv_method");

    privacyApiGroupJsonRpcMethods =
        new TestPrivacyApiGroupJsonRpcMethods(
            blockchainQueries, protocolSchedule, transactionPool, privacyParameters, rpcMethod);
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
  public void rpcsCreatedWithoutMultiTenancyUseFixedEnclavePublicKey() {
    when(privacyParameters.isEnabled()).thenReturn(true);
    when(privacyParameters.getPrivacyUserId()).thenReturn(DEFAULT_ENCLAVE_PUBLIC_KEY);

    final User user = createUser(DEFAULT_ENCLAVE_PUBLIC_KEY);
    privacyApiGroupJsonRpcMethods.create();
    final PrivacyIdProvider privacyIdProvider = privacyApiGroupJsonRpcMethods.privacyIdProvider;

    assertThat(privacyIdProvider.getPrivacyUserId(Optional.of(user)))
        .isEqualTo(DEFAULT_ENCLAVE_PUBLIC_KEY);
    assertThat(privacyIdProvider.getPrivacyUserId(Optional.empty()))
        .isEqualTo(DEFAULT_ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void rpcsCreatedWithMultiTenancyUseEnclavePublicKeyFromRequest() {
    when(privacyParameters.isEnabled()).thenReturn(true);
    when(privacyParameters.isMultiTenancyEnabled()).thenReturn(true);

    final User user1 = createUser("key1");
    final User user2 = createUser("key2");

    privacyApiGroupJsonRpcMethods.create();
    final PrivacyIdProvider privacyIdProvider = privacyApiGroupJsonRpcMethods.privacyIdProvider;

    assertThat(privacyIdProvider.getPrivacyUserId(Optional.of(user1))).isEqualTo("key1");
    assertThat(privacyIdProvider.getPrivacyUserId(Optional.of(user2))).isEqualTo("key2");
  }

  @Test
  public void rpcsCreatedWithMultiTenancyAndWithoutUserFail() {
    when(privacyParameters.isEnabled()).thenReturn(true);
    when(privacyParameters.isMultiTenancyEnabled()).thenReturn(true);

    privacyApiGroupJsonRpcMethods.create();
    final PrivacyIdProvider privacyIdProvider = privacyApiGroupJsonRpcMethods.privacyIdProvider;

    assertThatThrownBy(() -> privacyIdProvider.getPrivacyUserId(Optional.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Request does not contain an authorization token");
  }

  @Test
  public void rpcMethodsCreatedWhenPrivacyIsNotEnabledAreDisabled() {
    final Map<String, JsonRpcMethod> rpcMethods = privacyApiGroupJsonRpcMethods.create();
    assertThat(rpcMethods).hasSize(1);

    final JsonRpcMethod privMethod = rpcMethods.get("priv_method");
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "priv_method", null));
    final JsonRpcResponse response = privMethod.response(request);
    assertThat(response.getType()).isEqualTo(RpcResponseType.ERROR);

    JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getErrorType()).isEqualTo(PRIVACY_NOT_ENABLED);
  }

  @Test
  public void rpcsCreatedWithMultiTenancyUseMultiTenancyController() {
    when(privacyParameters.isEnabled()).thenReturn(true);
    when(privacyParameters.isMultiTenancyEnabled()).thenReturn(true);

    privacyApiGroupJsonRpcMethods.create();
    final PrivacyController privacyController = privacyApiGroupJsonRpcMethods.privacyController;

    assertThat(privacyController).isInstanceOf(MultiTenancyPrivacyController.class);
  }

  private User createUser(final String enclavePublicKey) {
    return new UserImpl(
        new JsonObject().put("privacyPublicKey", enclavePublicKey), new JsonObject()) {};
  }

  private static class TestPrivacyApiGroupJsonRpcMethods extends PrivacyApiGroupJsonRpcMethods {

    private final JsonRpcMethod rpcMethod;
    private PrivacyController privacyController;
    private PrivacyIdProvider privacyIdProvider;

    public TestPrivacyApiGroupJsonRpcMethods(
        final BlockchainQueries blockchainQueries,
        final ProtocolSchedule protocolSchedule,
        final TransactionPool transactionPool,
        final PrivacyParameters privacyParameters,
        final JsonRpcMethod rpcMethod) {
      super(blockchainQueries, protocolSchedule, transactionPool, privacyParameters);
      this.rpcMethod = rpcMethod;
    }

    @Override
    protected Map<String, JsonRpcMethod> create(
        final PrivacyController privacyController,
        final PrivacyIdProvider privacyIdProvider,
        final PrivateMarkerTransactionFactory privateMarkerTransactionFactory) {
      this.privacyController = privacyController;
      this.privacyIdProvider = privacyIdProvider;
      return mapOf(rpcMethod);
    }

    @Override
    protected String getApiGroup() {
      return RpcApis.PRIV.name();
    }
  }
}
