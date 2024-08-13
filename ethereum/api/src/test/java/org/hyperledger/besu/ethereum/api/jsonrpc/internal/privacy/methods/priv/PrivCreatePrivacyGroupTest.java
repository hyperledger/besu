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
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.List;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.impl.UserImpl;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("MockNotUsedInProduction")
public class PrivCreatePrivacyGroupTest {

  private static final String FROM = "first participant";
  private static final String NAME = "testName";
  private static final String DESCRIPTION = "testDesc";
  private static final List<String> ADDRESSES = Lists.newArrayList(FROM, "second participant");
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  private final Enclave enclave = mock(Enclave.class);
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final PrivacyController privacyController = mock(PrivacyController.class);
  private final User user =
      new UserImpl(new JsonObject().put("privacyPublicKey", ENCLAVE_PUBLIC_KEY), new JsonObject());
  private final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  @BeforeEach
  public void setUp() {
    when(privacyParameters.getEnclave()).thenReturn(enclave);
    when(privacyParameters.isEnabled()).thenReturn(true);
  }

  @Test
  public void verifyCreatePrivacyGroup() {
    final String expected = "a wonderful group";
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(expected, PrivacyGroup.Type.PANTHEON, NAME, DESCRIPTION, ADDRESSES);
    when(privacyController.createPrivacyGroup(ADDRESSES, NAME, DESCRIPTION, ENCLAVE_PUBLIC_KEY))
        .thenReturn(privacyGroup);
    when(privacyParameters.getPrivacyUserId()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(privacyController, privacyIdProvider);

    final CreatePrivacyGroupParameter param =
        new CreatePrivacyGroupParameter(ADDRESSES, NAME, DESCRIPTION);

    final Object[] params = new Object[] {param};

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params), user);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();

    assertThat(result).isEqualTo(expected);
    verify(privacyController).createPrivacyGroup(ADDRESSES, NAME, DESCRIPTION, ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void verifyCreatePrivacyGroupWithoutDescription() {
    final String expected = "a wonderful group";
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(expected, PrivacyGroup.Type.PANTHEON, NAME, DESCRIPTION, ADDRESSES);
    when(privacyController.createPrivacyGroup(ADDRESSES, NAME, null, ENCLAVE_PUBLIC_KEY))
        .thenReturn(privacyGroup);
    when(privacyParameters.getPrivacyUserId()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(privacyController, privacyIdProvider);

    final Object[] params =
        new Object[] {
          new Object() {
            public List<String> getAddresses() {
              return ADDRESSES;
            }

            public String getName() {
              return NAME;
            }
          }
        };

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void verifyCreatePrivacyGroupWithoutName() {
    final String expected = "a wonderful group";
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(expected, PrivacyGroup.Type.PANTHEON, NAME, DESCRIPTION, ADDRESSES);
    when(privacyController.createPrivacyGroup(ADDRESSES, null, DESCRIPTION, ENCLAVE_PUBLIC_KEY))
        .thenReturn(privacyGroup);
    when(privacyParameters.getPrivacyUserId()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(privacyController, privacyIdProvider);

    final Object[] params =
        new Object[] {
          new Object() {
            public List<String> getAddresses() {
              return ADDRESSES;
            }

            public String getDescription() {
              return DESCRIPTION;
            }
          }
        };

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void verifyCreatePrivacyGroupWithoutOptionalParams() {
    final String expected = "a wonderful group";
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(expected, PrivacyGroup.Type.PANTHEON, NAME, DESCRIPTION, ADDRESSES);
    when(privacyController.createPrivacyGroup(ADDRESSES, null, null, ENCLAVE_PUBLIC_KEY))
        .thenReturn(privacyGroup);
    when(privacyParameters.getPrivacyUserId()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(privacyController, privacyIdProvider);

    final Object[] params =
        new Object[] {
          new Object() {
            public List<String> getAddresses() {
              return ADDRESSES;
            }
          }
        };

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void returnsCorrectExceptionInvalidParam() {

    final String expected = "a wonderful group";
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(expected, PrivacyGroup.Type.PANTHEON, NAME, DESCRIPTION, ADDRESSES);
    when(enclave.createPrivacyGroup(any(), any(), any(), any())).thenReturn(privacyGroup);
    when(privacyParameters.getPrivacyUserId()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(privacyController, privacyIdProvider);

    final Object[] params =
        new Object[] {
          new Object() {
            public String getName() {
              return NAME;
            }

            public String getDescription() {
              return DESCRIPTION;
            }
          }
        };

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final Throwable response =
        catchThrowableOfType(
            () -> privCreatePrivacyGroup.response(request), InvalidJsonRpcParameters.class);

    assertThat(response.getMessage()).contains("Invalid create privacy group parameter (index 0)");
  }

  @Test
  public void returnsCorrectExceptionMissingParam() {

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(privacyController, privacyIdProvider);

    final Object[] params = new Object[] {};

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final Throwable response =
        catchThrowableOfType(
            () -> privCreatePrivacyGroup.response(request), InvalidJsonRpcParameters.class);

    assertThat(response.getMessage()).isEqualTo("Invalid create privacy group parameter (index 0)");
  }

  @Test
  public void returnsCorrectErrorEnclaveError() {
    when(privacyController.createPrivacyGroup(ADDRESSES, NAME, DESCRIPTION, ENCLAVE_PUBLIC_KEY))
        .thenThrow(new EnclaveClientException(400, "Enclave error"));
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(privacyController, privacyIdProvider);

    final CreatePrivacyGroupParameter param =
        new CreatePrivacyGroupParameter(ADDRESSES, NAME, DESCRIPTION);

    final Object[] params = new Object[] {param};

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("1", "priv_createPrivacyGroup", params));

    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) privCreatePrivacyGroup.response(request);

    final RpcErrorType result = response.getErrorType();

    assertThat(result).isEqualTo(RpcErrorType.ENCLAVE_ERROR);
  }
}
