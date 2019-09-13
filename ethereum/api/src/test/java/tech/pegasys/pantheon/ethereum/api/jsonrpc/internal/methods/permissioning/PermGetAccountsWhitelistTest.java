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
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.permissioning.AccountLocalConfigPermissioningController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PermGetAccountsWhitelistTest {

  private static final JsonRpcRequest request =
      new JsonRpcRequest("2.0", "perm_getAccountsWhitelist", null);

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
    when(accountWhitelist.getAccountWhitelist()).thenReturn(accountsList);
    JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, accountsList);

    JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyListOfAccountsWhenWhitelistHasBeenSetAndIsEmpty() {
    List<String> emptyAccountsList = new ArrayList<>();
    when(accountWhitelist.getAccountWhitelist()).thenReturn(emptyAccountsList);
    JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, emptyAccountsList);

    JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }
}
