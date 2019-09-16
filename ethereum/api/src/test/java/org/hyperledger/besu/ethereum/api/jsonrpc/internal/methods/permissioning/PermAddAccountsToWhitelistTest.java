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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.WhitelistOperationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PermAddAccountsToWhitelistTest {

  @Mock private AccountLocalConfigPermissioningController accountWhitelist;
  private PermAddAccountsToWhitelist method;

  @Before
  public void before() {
    method =
        new PermAddAccountsToWhitelist(
            java.util.Optional.of(accountWhitelist), new JsonRpcParameter());
  }

  @Test
  public void getNameShouldReturnExpectedName() {
    assertThat(method.getName()).isEqualTo("perm_addAccountsToWhitelist");
  }

  @Test
  public void whenAccountsAreAddedToWhitelistShouldReturnSuccess() {
    List<String> accounts = Arrays.asList("0x0", "0x1");
    JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null);
    when(accountWhitelist.addAccounts(eq(accounts))).thenReturn(WhitelistOperationResult.SUCCESS);

    JsonRpcResponse actualResponse = method.response(request(accounts));

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void whenAccountIsInvalidShouldReturnInvalidAccountErrorResponse() {
    JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.ACCOUNT_WHITELIST_INVALID_ENTRY);
    when(accountWhitelist.addAccounts(any()))
        .thenReturn(WhitelistOperationResult.ERROR_INVALID_ENTRY);

    JsonRpcResponse actualResponse = method.response(request(new ArrayList<>()));

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void whenAccountExistsShouldReturnExistingEntryErrorResponse() {
    JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.ACCOUNT_WHITELIST_EXISTING_ENTRY);
    when(accountWhitelist.addAccounts(any()))
        .thenReturn(WhitelistOperationResult.ERROR_EXISTING_ENTRY);

    JsonRpcResponse actualResponse = method.response(request(new ArrayList<>()));

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void whenInputHasDuplicatedAccountsShouldReturnDuplicatedEntryErrorResponse() {
    JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.ACCOUNT_WHITELIST_DUPLICATED_ENTRY);
    when(accountWhitelist.addAccounts(any()))
        .thenReturn(WhitelistOperationResult.ERROR_DUPLICATED_ENTRY);

    JsonRpcResponse actualResponse = method.response(request(new ArrayList<>()));

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void whenEmptyListOnRequestShouldReturnEmptyEntryErrorResponse() {
    JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.ACCOUNT_WHITELIST_EMPTY_ENTRY);

    when(accountWhitelist.addAccounts(eq(new ArrayList<>())))
        .thenReturn(WhitelistOperationResult.ERROR_EMPTY_ENTRY);

    JsonRpcResponse actualResponse = method.response(request(new ArrayList<>()));

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void whenEmptyParamOnRequestShouldThrowInvalidJsonRpcException() {
    JsonRpcRequest request =
        new JsonRpcRequest("2.0", "perm_addAccountsToWhitelist", new Object[] {});

    final Throwable thrown = catchThrowable(() -> method.response(request));
    assertThat(thrown)
        .hasNoCause()
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  private JsonRpcRequest request(final List<String> accounts) {
    return new JsonRpcRequest("2.0", "perm_addAccountsToWhitelist", new Object[] {accounts});
  }
}
