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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.eea;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy.priv.PrivCreatePrivacyGroup;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Test;

public class EeaCreatePrivacyGroupTest {

  private final Enclave enclave = mock(Enclave.class);
  private final JsonRpcParameter parameters = new JsonRpcParameter();

  private final String from = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private final String name = "testName";
  private final String description = "testDesc";
  private final String[] addresses =
      new String[] {
        "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=",
        "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs="
      };
  private final String privacyGroupId =
      "68/Cq0mVjB8FbXDLE1tbDRAvD/srluIok137uFOaClPU/dLFW34ovZebW+PTzy9wUawTXw==";

  @Test
  public void verifyCreatePrivacyGroup() throws Exception {
    PrivacyGroup privacyGroup =
        new PrivacyGroup(privacyGroupId, PrivacyGroup.Type.PANTHEON, name, description, addresses);
    when(enclave.createPrivacyGroup(any())).thenReturn(privacyGroup);

    final PrivCreatePrivacyGroup eeaCreatePrivacyGroup =
        new PrivCreatePrivacyGroup(enclave, parameters);

    Object[] params = new Object[] {from, name, description, addresses};
    final JsonRpcRequest request = new JsonRpcRequest("1", "priv_createPrivacyGroup", params);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) eeaCreatePrivacyGroup.response(request);

    final String result = (String) response.getResult();

    assertEquals(privacyGroupId, result);
  }
}
