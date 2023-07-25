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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController.NodesAllowlistResult;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.permissioning.AllowlistOperationResult;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PermRemoveNodesFromAllowlistTest {

  private PermRemoveNodesFromAllowlist method;
  private static final String METHOD_NAME = "perm_removeNodesFromAllowlist";

  private final String enode1 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode2 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode3 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String badEnode = "enod://dog@cat:fish";

  @Mock private NodeLocalConfigPermissioningController nodeLocalConfigPermissioningController;

  @BeforeEach
  public void setUp() {
    method = new PermRemoveNodesFromAllowlist(Optional.of(nodeLocalConfigPermissioningController));
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(METHOD_NAME);
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersExceptionWhenBadEnode() {
    final JsonRpcRequestContext request = buildRequest(Lists.newArrayList(badEnode));
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.NODE_ALLOWLIST_INVALID_ENTRY);

    when(nodeLocalConfigPermissioningController.removeNodes(eq(Lists.newArrayList(badEnode))))
        .thenThrow(IllegalArgumentException.class);

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersExceptionWhenEmptyList() {
    final JsonRpcRequestContext request = buildRequest(Collections.emptyList());
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.NODE_ALLOWLIST_EMPTY_ENTRY);

    when(nodeLocalConfigPermissioningController.removeNodes(Collections.emptyList()))
        .thenReturn(new NodesAllowlistResult(AllowlistOperationResult.ERROR_EMPTY_ENTRY));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void shouldRemoveSingleValidNode() {
    final JsonRpcRequestContext request = buildRequest(Lists.newArrayList(enode1));
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getRequest().getId());

    when(nodeLocalConfigPermissioningController.removeNodes(any()))
        .thenReturn(new NodesAllowlistResult(AllowlistOperationResult.SUCCESS));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);

    verify(nodeLocalConfigPermissioningController, times(1)).removeNodes(any());
    verifyNoMoreInteractions(nodeLocalConfigPermissioningController);
  }

  @Test
  public void shouldRemoveMultipleValidNodes() {
    final JsonRpcRequestContext request = buildRequest(Lists.newArrayList(enode1, enode2, enode3));
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getRequest().getId());

    when(nodeLocalConfigPermissioningController.removeNodes(any()))
        .thenReturn(new NodesAllowlistResult(AllowlistOperationResult.SUCCESS));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);

    verify(nodeLocalConfigPermissioningController, times(1)).removeNodes(any());
    verifyNoMoreInteractions(nodeLocalConfigPermissioningController);
  }

  @Test
  public void shouldFailWhenP2pDisabled() {
    method = new PermRemoveNodesFromAllowlist(Optional.empty());
    final JsonRpcRequestContext request = buildRequest(Lists.newArrayList(enode1, enode2, enode3));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.NODE_ALLOWLIST_NOT_ENABLED);

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  @Test
  public void whenRequestContainsDuplicatedNodesShouldReturnDuplicatedEntryError() {
    final JsonRpcRequestContext request = buildRequest(Lists.newArrayList(enode1, enode1));
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.NODE_ALLOWLIST_DUPLICATED_ENTRY);

    when(nodeLocalConfigPermissioningController.removeNodes(any()))
        .thenReturn(new NodesAllowlistResult(AllowlistOperationResult.ERROR_DUPLICATED_ENTRY));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void whenRequestContainsEmptyListOfNodesShouldReturnEmptyEntryError() {
    final JsonRpcRequestContext request = buildRequest(new ArrayList<>());
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.NODE_ALLOWLIST_EMPTY_ENTRY);

    when(nodeLocalConfigPermissioningController.removeNodes(eq(new ArrayList<>())))
        .thenReturn(new NodesAllowlistResult(AllowlistOperationResult.ERROR_EMPTY_ENTRY));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  public void shouldReturnCantRemoveBootnodeWhenRemovingBootnode() {
    final JsonRpcRequestContext request = buildRequest(Lists.newArrayList(enode1));
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.NODE_ALLOWLIST_FIXED_NODE_CANNOT_BE_REMOVED);

    when(nodeLocalConfigPermissioningController.removeNodes(any()))
        .thenReturn(
            new NodesAllowlistResult(AllowlistOperationResult.ERROR_FIXED_NODE_CANNOT_BE_REMOVED));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  private JsonRpcRequestContext buildRequest(final List<String> enodeList) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", METHOD_NAME, new Object[] {enodeList}));
  }
}
