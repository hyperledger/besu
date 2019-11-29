package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivDeletePrivacyGroupTest {

  @Mock private Enclave enclave;
  @Mock private PrivacyParameters privacyParameters;
  private PrivDeletePrivacyGroup privDeletePrivacyGroup;

  @Before
  public void before() {
    when(privacyParameters.getEnclave()).thenReturn(enclave);
    privDeletePrivacyGroup = new PrivDeletePrivacyGroup(privacyParameters);
  }

  @Test
  public void returnPrivacyDisabledErrorWhenPrivacyIsDisabled() {
    when(privacyParameters.isEnabled()).thenReturn(false);

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("1", "priv_deletePrivacyGroup", new Object[] {}));
    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) privDeletePrivacyGroup.response(request);

    assertThat(response.getError()).isEqualTo(JsonRpcError.PRIVACY_NOT_ENABLED);
  }
}
