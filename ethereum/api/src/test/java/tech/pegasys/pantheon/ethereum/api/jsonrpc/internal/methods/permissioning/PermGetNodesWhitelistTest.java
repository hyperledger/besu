/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.permissioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.permissioning.NodeLocalConfigPermissioningController;

import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PermGetNodesWhitelistTest {

  private PermGetNodesWhitelist method;
  private static final String METHOD_NAME = "perm_getNodesWhitelist";

  private final String enode1 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode2 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.11:4567";
  private final String enode3 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.12:4567";

  @Mock private NodeLocalConfigPermissioningController nodeLocalConfigPermissioningController;

  @Before
  public void setUp() {
    method = new PermGetNodesWhitelist(Optional.of(nodeLocalConfigPermissioningController));
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(METHOD_NAME);
  }

  @Test
  public void shouldReturnSuccessResponseWhenListPopulated() {
    final JsonRpcRequest request = buildRequest();
    final JsonRpcResponse expected =
        new JsonRpcSuccessResponse(request.getId(), Lists.newArrayList(enode1, enode2, enode3));

    when(nodeLocalConfigPermissioningController.getNodesWhitelist())
        .thenReturn(buildNodesList(enode1, enode2, enode3));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);

    verify(nodeLocalConfigPermissioningController, times(1)).getNodesWhitelist();
    verifyNoMoreInteractions(nodeLocalConfigPermissioningController);
  }

  @Test
  public void shouldReturnSuccessResponseWhenListSetAndEmpty() {
    final JsonRpcRequest request = buildRequest();
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getId(), Lists.emptyList());

    when(nodeLocalConfigPermissioningController.getNodesWhitelist()).thenReturn(buildNodesList());

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);

    verify(nodeLocalConfigPermissioningController, times(1)).getNodesWhitelist();
    verifyNoMoreInteractions(nodeLocalConfigPermissioningController);
  }

  @Test
  public void shouldFailWhenP2pDisabled() {
    method = new PermGetNodesWhitelist(Optional.empty());

    final JsonRpcRequest request = buildRequest();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_NOT_ENABLED);

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest buildRequest() {
    return new JsonRpcRequest("2.0", METHOD_NAME, new Object[] {});
  }

  private List<String> buildNodesList(final String... enodes) {
    return Lists.newArrayList(enodes);
  }
}
