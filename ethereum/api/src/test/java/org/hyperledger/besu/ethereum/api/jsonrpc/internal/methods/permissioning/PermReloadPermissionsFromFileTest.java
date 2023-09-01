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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PermReloadPermissionsFromFileTest {

  @Mock private AccountLocalConfigPermissioningController accountLocalConfigPermissioningController;
  @Mock private NodeLocalConfigPermissioningController nodeLocalConfigPermissioningController;
  private PermReloadPermissionsFromFile method;

  @BeforeEach
  public void before() {
    method =
        new PermReloadPermissionsFromFile(
            Optional.of(accountLocalConfigPermissioningController),
            Optional.of(nodeLocalConfigPermissioningController));
  }

  @Test
  public void getNameShouldReturnExpectedName() {
    assertThat(method.getName()).isEqualTo("perm_reloadPermissionsFromFile");
  }

  @Test
  public void whenBothControllersAreNotPresentMethodShouldReturnPermissioningDisabled() {
    JsonRpcResponse expectedErrorResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.PERMISSIONING_NOT_ENABLED);

    method = new PermReloadPermissionsFromFile(Optional.empty(), Optional.empty());

    JsonRpcResponse response = method.response(reloadRequest());

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedErrorResponse);
  }

  @Test
  public void whenControllersReloadSucceedsMethodShouldReturnSuccess() {
    JsonRpcResponse response = method.response(reloadRequest());

    verify(accountLocalConfigPermissioningController).reload();
    verify(nodeLocalConfigPermissioningController).reload();

    assertThat(response).usingRecursiveComparison().isEqualTo(successResponse());
  }

  @Test
  public void whenControllerReloadFailsMethodShouldReturnError() {
    doThrow(new RuntimeException()).when(accountLocalConfigPermissioningController).reload();
    JsonRpcResponse expectedErrorResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.ALLOWLIST_RELOAD_ERROR);

    JsonRpcResponse response = method.response(reloadRequest());

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedErrorResponse);
  }

  private JsonRpcSuccessResponse successResponse() {
    return new JsonRpcSuccessResponse(null);
  }

  private JsonRpcRequestContext reloadRequest() {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "perm_reloadPermissionsFromFile", null));
  }
}
