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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.AllowlistOperationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PermAddAccountsToAllowlistTest {

  @Mock private AccountLocalConfigPermissioningController accountAllowlist;
  private PermAddAccountsToAllowlist method;

  @BeforeEach
  public void before() {
    method = new PermAddAccountsToAllowlist(java.util.Optional.of(accountAllowlist));
  }

  @Test
  public void getNameShouldReturnExpectedName() {
    assertThat(method.getName()).isEqualTo("perm_addAccountsToAllowlist");
  }

  @Test
  public void whenAccountsAreAddedToAllowlistShouldReturnSuccess() {
    List<String> accounts = Arrays.asList("0x0", "0x1");
    JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null);
    when(accountAllowlist.addAccounts(eq(accounts))).thenReturn(AllowlistOperationResult.SUCCESS);

    JsonRpcResponse actualResponse = method.response(request(accounts));

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void whenAccountIsInvalidShouldReturnInvalidAccountErrorResponse() {
    JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.ACCOUNT_ALLOWLIST_INVALID_ENTRY);
    when(accountAllowlist.addAccounts(any()))
        .thenReturn(AllowlistOperationResult.ERROR_INVALID_ENTRY);

    JsonRpcResponse actualResponse = method.response(request(new ArrayList<>()));

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void whenAccountExistsShouldReturnExistingEntryErrorResponse() {
    JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.ACCOUNT_ALLOWLIST_EXISTING_ENTRY);
    when(accountAllowlist.addAccounts(any()))
        .thenReturn(AllowlistOperationResult.ERROR_EXISTING_ENTRY);

    JsonRpcResponse actualResponse = method.response(request(new ArrayList<>()));

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void whenInputHasDuplicatedAccountsShouldReturnDuplicatedEntryErrorResponse() {
    JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.ACCOUNT_ALLOWLIST_DUPLICATED_ENTRY);
    when(accountAllowlist.addAccounts(any()))
        .thenReturn(AllowlistOperationResult.ERROR_DUPLICATED_ENTRY);

    JsonRpcResponse actualResponse = method.response(request(new ArrayList<>()));

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void whenEmptyListOnRequestShouldReturnEmptyEntryErrorResponse() {
    JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.ACCOUNT_ALLOWLIST_EMPTY_ENTRY);

    when(accountAllowlist.addAccounts(eq(new ArrayList<>())))
        .thenReturn(AllowlistOperationResult.ERROR_EMPTY_ENTRY);

    JsonRpcResponse actualResponse = method.response(request(new ArrayList<>()));

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void whenEmptyParamOnRequestShouldThrowInvalidJsonRpcException() {
    JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "perm_addAccountsToAllowlist", new Object[] {}));

    final Throwable thrown = catchThrowable(() -> method.response(request));
    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid accounts list parameter (index 0)");
  }

  private JsonRpcRequestContext request(final List<String> accounts) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "perm_addAccountsToAllowlist", new Object[] {accounts}));
  }
}
