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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.EnclaveException;
import tech.pegasys.pantheon.enclave.types.CreatePrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;

public class PrivCreatePrivacyGroupTest {

  private static final String FROM = "first participant";
  private static final String NAME = "testName";
  private static final String DESCRIPTION = "testDesc";
  private static final String[] ADDRESSES = new String[] {FROM, "second participant"};

  private final Enclave enclave = mock(Enclave.class);
  private final Enclave failingEnclave = mock(Enclave.class);
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final JsonRpcParameter parameters = new JsonRpcParameter();

  @Before
  public void setUp() {
    when(failingEnclave.createPrivacyGroup(any(CreatePrivacyGroupRequest.class)))
        .thenThrow(new EnclaveException(""));
  }

  @Test
  public void verifyCreatePrivacyGroup() {
    final String expected = "a wonderful group";
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(expected, PrivacyGroup.Type.PANTHEON, NAME, DESCRIPTION, ADDRESSES);
    when(enclave.createPrivacyGroup(any(CreatePrivacyGroupRequest.class))).thenReturn(privacyGroup);
    when(privacyParameters.getEnclavePublicKey()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(enclave, privacyParameters, parameters);

    final CreatePrivacyGroupParameter param =
        new CreatePrivacyGroupParameter(ADDRESSES, NAME, DESCRIPTION);

    final Object[] params = new Object[] {param};

    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_createPrivacyGroup", params);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();

    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void verifyCreatePrivacyGroupWithoutDescription() {
    final String expected = "a wonderful group";
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(expected, PrivacyGroup.Type.PANTHEON, NAME, DESCRIPTION, ADDRESSES);
    when(enclave.createPrivacyGroup(any(CreatePrivacyGroupRequest.class))).thenReturn(privacyGroup);
    when(privacyParameters.getEnclavePublicKey()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(enclave, privacyParameters, parameters);

    final Object[] params =
        new Object[] {
          new Object() {
            public String[] getAddresses() {
              return ADDRESSES;
            }

            public String getName() {
              return NAME;
            }
          }
        };

    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_createPrivacyGroup", params);

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
    when(enclave.createPrivacyGroup(any(CreatePrivacyGroupRequest.class))).thenReturn(privacyGroup);
    when(privacyParameters.getEnclavePublicKey()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(enclave, privacyParameters, parameters);

    final Object[] params =
        new Object[] {
          new Object() {
            public String[] getAddresses() {
              return ADDRESSES;
            }

            public String getDescription() {
              return DESCRIPTION;
            }
          }
        };

    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_createPrivacyGroup", params);

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
    when(enclave.createPrivacyGroup(any(CreatePrivacyGroupRequest.class))).thenReturn(privacyGroup);
    when(privacyParameters.getEnclavePublicKey()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(enclave, privacyParameters, parameters);

    final Object[] params =
        new Object[] {
          new Object() {
            public String[] getAddresses() {
              return ADDRESSES;
            }
          }
        };

    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_createPrivacyGroup", params);

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
    when(enclave.createPrivacyGroup(any(CreatePrivacyGroupRequest.class))).thenReturn(privacyGroup);
    when(privacyParameters.getEnclavePublicKey()).thenReturn(FROM);

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(enclave, privacyParameters, parameters);

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

    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_createPrivacyGroup", params);

    final Throwable response =
        catchThrowableOfType(
            () -> privCreatePrivacyGroup.response(request), InvalidJsonRpcParameters.class);

    assertThat(response.getMessage()).isEqualTo("Invalid json rpc parameter at index 0");
  }

  @Test
  public void returnsCorrectExceptionMissingParam() {

    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(enclave, privacyParameters, parameters);

    final Object[] params = new Object[] {};

    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_createPrivacyGroup", params);

    final Throwable response =
        catchThrowableOfType(
            () -> privCreatePrivacyGroup.response(request), InvalidJsonRpcParameters.class);

    assertThat(response.getMessage()).isEqualTo("Missing required json rpc parameter at index 0");
  }

  @Test
  public void returnsCorrectErrorEnclaveError() {
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(failingEnclave, privacyParameters, parameters);

    final CreatePrivacyGroupParameter param =
        new CreatePrivacyGroupParameter(ADDRESSES, NAME, DESCRIPTION);

    final Object[] params = new Object[] {param};

    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_createPrivacyGroup", params);

    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) privCreatePrivacyGroup.response(request);

    final JsonRpcError result = response.getError();

    assertThat(result).isEqualTo(JsonRpcError.ENCLAVE_ERROR);
  }
}
