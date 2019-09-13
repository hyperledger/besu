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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.permissioning.NodeLocalConfigPermissioningController.NodesWhitelistResult;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.permissioning.NodeLocalConfigPermissioningController;
import tech.pegasys.pantheon.ethereum.permissioning.WhitelistOperationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PermRemoveNodesFromWhitelistTest {

  private PermRemoveNodesFromWhitelist method;
  private static final String METHOD_NAME = "perm_removeNodesFromWhitelist";

  private final String enode1 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode2 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode3 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String badEnode = "enod://dog@cat:fish";

  @Mock private NodeLocalConfigPermissioningController nodeLocalConfigPermissioningController;

  private JsonRpcParameter params = new JsonRpcParameter();

  @Before
  public void setUp() {
    method =
        new PermRemoveNodesFromWhitelist(
            Optional.of(nodeLocalConfigPermissioningController), params);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(METHOD_NAME);
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersExceptionWhenBadEnode() {
    final JsonRpcRequest request = buildRequest(Lists.newArrayList(badEnode));
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_INVALID_ENTRY);

    when(nodeLocalConfigPermissioningController.removeNodes(eq(Lists.newArrayList(badEnode))))
        .thenThrow(IllegalArgumentException.class);

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersExceptionWhenEmptyList() {
    final JsonRpcRequest request = buildRequest(Lists.emptyList());
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_EMPTY_ENTRY);

    when(nodeLocalConfigPermissioningController.removeNodes(eq(Lists.emptyList())))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.ERROR_EMPTY_ENTRY));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldRemoveSingleValidNode() {
    final JsonRpcRequest request = buildRequest(Lists.newArrayList(enode1));
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getId());

    when(nodeLocalConfigPermissioningController.removeNodes(any()))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.SUCCESS));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);

    verify(nodeLocalConfigPermissioningController, times(1)).removeNodes(any());
    verifyNoMoreInteractions(nodeLocalConfigPermissioningController);
  }

  @Test
  public void shouldRemoveMultipleValidNodes() {
    final JsonRpcRequest request = buildRequest(Lists.newArrayList(enode1, enode2, enode3));
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getId());

    when(nodeLocalConfigPermissioningController.removeNodes(any()))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.SUCCESS));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);

    verify(nodeLocalConfigPermissioningController, times(1)).removeNodes(any());
    verifyNoMoreInteractions(nodeLocalConfigPermissioningController);
  }

  @Test
  public void shouldFailWhenP2pDisabled() {
    method = new PermRemoveNodesFromWhitelist(Optional.empty(), params);
    final JsonRpcRequest request = buildRequest(Lists.newArrayList(enode1, enode2, enode3));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_NOT_ENABLED);

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void whenRequestContainsDuplicatedNodesShouldReturnDuplicatedEntryError() {
    final JsonRpcRequest request = buildRequest(Lists.newArrayList(enode1, enode1));
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_DUPLICATED_ENTRY);

    when(nodeLocalConfigPermissioningController.removeNodes(any()))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.ERROR_DUPLICATED_ENTRY));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void whenRequestContainsEmptyListOfNodesShouldReturnEmptyEntryError() {
    final JsonRpcRequest request = buildRequest(new ArrayList<>());
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_EMPTY_ENTRY);

    when(nodeLocalConfigPermissioningController.removeNodes(eq(new ArrayList<>())))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.ERROR_EMPTY_ENTRY));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldReturnCantRemoveBootnodeWhenRemovingBootnode() {
    final JsonRpcRequest request = buildRequest(Lists.newArrayList(enode1));
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getId(), JsonRpcError.NODE_WHITELIST_FIXED_NODE_CANNOT_BE_REMOVED);

    when(nodeLocalConfigPermissioningController.removeNodes(any()))
        .thenReturn(
            new NodesWhitelistResult(WhitelistOperationResult.ERROR_FIXED_NODE_CANNOT_BE_REMOVED));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  private JsonRpcRequest buildRequest(final List<String> enodeList) {
    return new JsonRpcRequest("2.0", METHOD_NAME, new Object[] {enodeList});
  }
}
