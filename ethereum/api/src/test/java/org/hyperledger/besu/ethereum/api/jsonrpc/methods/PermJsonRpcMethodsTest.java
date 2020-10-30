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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_ADD_ACCOUNTS_TO_ALLOWLIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_ADD_ACCOUNTS_TO_WHITELIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_ADD_NODES_TO_ALLOWLIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_ADD_NODES_TO_WHITELIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_GET_ACCOUNTS_ALLOWLIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_GET_ACCOUNTS_WHITELIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_GET_NODES_ALLOWLIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_GET_NODES_WHITELIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_REMOVE_ACCOUNTS_FROM_ALLOWLIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_REMOVE_ACCOUNTS_FROM_WHITELIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_REMOVE_NODES_FROM_ALLOWLIST;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PERM_REMOVE_NODES_FROM_WHITELIST;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermAddAccountsToAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermAddAccountsToWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermAddNodesToAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetAccountsAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetAccountsWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetNodesAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermGetNodesWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveAccountsFromAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveAccountsFromWhitelist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveNodesFromAllowlist;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.permissioning.PermRemoveNodesFromWhitelist;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;

import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PermJsonRpcMethodsTest {

  @Mock private AccountLocalConfigPermissioningController accountLocalConfigPermissioningController;
  @Mock private NodeLocalConfigPermissioningController nodeLocalConfigPermissioningController;

  private PermJsonRpcMethods permJsonRpcMethods;

  @Before
  public void setup() {
    permJsonRpcMethods =
        new PermJsonRpcMethods(
            Optional.of(accountLocalConfigPermissioningController),
            Optional.of(nodeLocalConfigPermissioningController));
  }

  @Test
  public void allowlistMethodsPresent() {
    final Map<String, JsonRpcMethod> rpcMethods = permJsonRpcMethods.create();

    // Account methods x 3
    assertThat(rpcMethods.get(PERM_GET_ACCOUNTS_ALLOWLIST.getMethodName()))
        .isInstanceOf(PermGetAccountsAllowlist.class);
    assertThat(rpcMethods.get(PERM_ADD_ACCOUNTS_TO_ALLOWLIST.getMethodName()))
        .isInstanceOf(PermAddAccountsToAllowlist.class);
    assertThat(rpcMethods.get(PERM_REMOVE_ACCOUNTS_FROM_ALLOWLIST.getMethodName()))
        .isInstanceOf(PermRemoveAccountsFromAllowlist.class);

    // Node methods x 3
    assertThat(rpcMethods.get(PERM_GET_NODES_ALLOWLIST.getMethodName()))
        .isInstanceOf(PermGetNodesAllowlist.class);
    assertThat(rpcMethods.get(PERM_ADD_NODES_TO_ALLOWLIST.getMethodName()))
        .isInstanceOf(PermAddNodesToAllowlist.class);
    assertThat(rpcMethods.get(PERM_REMOVE_NODES_FROM_ALLOWLIST.getMethodName()))
        .isInstanceOf(PermRemoveNodesFromAllowlist.class);
  }

  @Deprecated
  @Test
  public void whitelistMethodsPresent() {
    final Map<String, JsonRpcMethod> rpcMethods = permJsonRpcMethods.create();
    assertThat(rpcMethods.size()).isEqualTo(13);

    // Account methods x 3
    assertThat(rpcMethods.get(PERM_GET_ACCOUNTS_WHITELIST.getMethodName()))
        .isInstanceOf(PermGetAccountsWhitelist.class);
    assertThat(rpcMethods.get(PERM_ADD_ACCOUNTS_TO_WHITELIST.getMethodName()))
        .isInstanceOf(PermAddAccountsToWhitelist.class);
    assertThat(rpcMethods.get(PERM_REMOVE_ACCOUNTS_FROM_WHITELIST.getMethodName()))
        .isInstanceOf(PermRemoveAccountsFromWhitelist.class);

    // Node methods x 3
    assertThat(rpcMethods.get(PERM_GET_NODES_WHITELIST.getMethodName()))
        .isInstanceOf(PermGetNodesWhitelist.class);
    assertThat(rpcMethods.get(PERM_ADD_NODES_TO_WHITELIST.getMethodName()))
        .isInstanceOf(PermAddNodesToAllowlist.class);
    assertThat(rpcMethods.get(PERM_REMOVE_NODES_FROM_WHITELIST.getMethodName()))
        .isInstanceOf(PermRemoveNodesFromWhitelist.class);
  }
}
