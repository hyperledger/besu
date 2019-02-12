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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.permissioning.NodeWhitelistController;
import tech.pegasys.pantheon.ethereum.permissioning.AccountWhitelistController;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PermReloadPermissionsFromFileTest {

  @Mock private AccountWhitelistController accountWhitelistController;
  @Mock private NodeWhitelistController nodeWhitelistController;
  private PermReloadPermissionsFromFile method;

  @Before
  public void before() {
    method =
        new PermReloadPermissionsFromFile(
            Optional.of(accountWhitelistController), Optional.of(nodeWhitelistController));
  }

  @Test
  public void getNameShouldReturnExpectedName() {
    assertThat(method.getName()).isEqualTo("perm_reloadPermissionsFromFile");
  }

  @Test
  public void whenBothControllersAreNotPresentMethodShouldReturnPermissioningDisabled() {
    JsonRpcResponse expectedErrorResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.PERMISSIONING_NOT_ENABLED);

    method = new PermReloadPermissionsFromFile(Optional.empty(), Optional.empty());

    JsonRpcResponse response = method.response(reloadRequest());

    assertThat(response).isEqualToComparingFieldByField(expectedErrorResponse);
  }

  @Test
  public void whenControllersReloadSucceedsMethodShouldReturnSuccess() {
    JsonRpcResponse response = method.response(reloadRequest());

    verify(accountWhitelistController).reload();
    verify(nodeWhitelistController).reload();

    assertThat(response).isEqualToComparingFieldByField(successResponse());
  }

  @Test
  public void whenControllerReloadFailsMethodShouldReturnError() {
    doThrow(new RuntimeException()).when(accountWhitelistController).reload();
    JsonRpcResponse expectedErrorResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.WHITELIST_RELOAD_ERROR);

    JsonRpcResponse response = method.response(reloadRequest());

    assertThat(response).isEqualToComparingFieldByField(expectedErrorResponse);
  }

  private JsonRpcSuccessResponse successResponse() {
    return new JsonRpcSuccessResponse(null);
  }

  private JsonRpcRequest reloadRequest() {
    return new JsonRpcRequest("2.0", "perm_reloadPermissionsFromFile", null);
  }
}
