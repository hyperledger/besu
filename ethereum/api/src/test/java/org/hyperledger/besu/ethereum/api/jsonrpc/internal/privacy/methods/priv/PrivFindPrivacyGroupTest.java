package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;

import org.junit.Before;
import org.junit.Test;

public class PrivFindPrivacyGroupTest {

  private Enclave enclave;
  private PrivacyParameters privacyParameters;
  private PrivFindPrivacyGroup privFindPrivacyGroup;

  @Before
  public void before() {
    enclave = mock(Enclave.class);
    privacyParameters = mock(PrivacyParameters.class);
    when(privacyParameters.getEnclave()).thenReturn(enclave);
    privFindPrivacyGroup = new PrivFindPrivacyGroup(privacyParameters);
  }

  @Test
  public void returnPrivacyDisabledErrorWhenPrivacyIsDisabled() {
    when(privacyParameters.isEnabled()).thenReturn(false);

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "findPrivacyGroup", new Object[] {}));
    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) privFindPrivacyGroup.response(request);

    assertThat(response.getError()).isEqualTo(JsonRpcError.PRIVACY_NOT_ENABLED);
  }
}
