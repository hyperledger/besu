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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@Deprecated
@RunWith(MockitoJUnitRunner.class)
public class PermGetAccountsWhitelistTest {

  private static final JsonRpcRequestContext request =
      new JsonRpcRequestContext(new JsonRpcRequest("2.0", "perm_getAccountsWhitelist", null));

  @Mock private AccountLocalConfigPermissioningController accountWhitelist;
  private PermGetAccountsWhitelist method;

  @Before
  public void before() {
    method = new PermGetAccountsWhitelist(java.util.Optional.of(accountWhitelist));
  }

  @Test
  public void getNameShouldReturnExpectedName() {
    assertThat(method.getName()).isEqualTo("perm_getAccountsWhitelist");
  }

  @Test
  public void shouldReturnExpectedListOfAccountsWhenWhitelistHasBeenSet() {
    List<String> accountsList = Arrays.asList("0x0", "0x1");
    when(accountWhitelist.getAccountAllowlist()).thenReturn(accountsList);
    JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, accountsList);

    JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyListOfAccountsWhenWhitelistHasBeenSetAndIsEmpty() {
    List<String> emptyAccountsList = new ArrayList<>();
    when(accountWhitelist.getAccountAllowlist()).thenReturn(emptyAccountsList);
    JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, emptyAccountsList);

    JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
}
