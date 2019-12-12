package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.PRIVACY_NOT_ENABLED;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivacyApiGroupJsonRpcMethodsTest {
  @Mock private JsonRpcMethod rpcMethod;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private TransactionPool transactionPool;
  @Mock private PrivacyParameters privacyParameters;

  private PrivacyApiGroupJsonRpcMethods privacyApiGroupJsonRpcMethods;

  @Before
  public void setup() {
    when(rpcMethod.getName()).thenReturn("priv_method");
    privacyApiGroupJsonRpcMethods = createPrivacyApiGroupJsonRpcMethods();
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

  @NotNull
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
