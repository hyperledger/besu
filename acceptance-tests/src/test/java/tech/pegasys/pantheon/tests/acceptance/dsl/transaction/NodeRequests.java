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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.admin.AdminRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.clique.CliqueRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.EeaRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ibft2.Ibft2RequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.login.LoginRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.net.CustomNetJsonRpcRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.PermissioningJsonRpcRequestFactory;

import java.util.Optional;

import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.websocket.WebSocketService;

public class NodeRequests {

  private final JsonRpc2_0Web3j netEth;
  private final CliqueRequestFactory clique;
  private final Ibft2RequestFactory ibft;
  private final PermissioningJsonRpcRequestFactory perm;
  private final AdminRequestFactory admin;
  private final EeaRequestFactory eea;
  private final CustomNetJsonRpcRequestFactory customNet;
  private final Optional<WebSocketService> websocketService;
  private final LoginRequestFactory login;

  public NodeRequests(
      final JsonRpc2_0Web3j netEth,
      final CliqueRequestFactory clique,
      final Ibft2RequestFactory ibft,
      final PermissioningJsonRpcRequestFactory perm,
      final AdminRequestFactory admin,
      final EeaRequestFactory eea,
      final CustomNetJsonRpcRequestFactory customNet,
      final Optional<WebSocketService> websocketService,
      final LoginRequestFactory login) {
    this.netEth = netEth;
    this.clique = clique;
    this.ibft = ibft;
    this.perm = perm;
    this.admin = admin;
    this.eea = eea;
    this.customNet = customNet;
    this.websocketService = websocketService;
    this.login = login;
  }

  public JsonRpc2_0Web3j eth() {
    return netEth;
  }

  public JsonRpc2_0Web3j net() {
    return netEth;
  }

  public CliqueRequestFactory clique() {
    return clique;
  }

  public Ibft2RequestFactory ibft() {
    return ibft;
  }

  public PermissioningJsonRpcRequestFactory perm() {
    return perm;
  }

  public AdminRequestFactory admin() {
    return admin;
  }

  public CustomNetJsonRpcRequestFactory customNet() {
    return customNet;
  }

  public EeaRequestFactory eea() {
    return eea;
  }

  public LoginRequestFactory login() {
    return login;
  }

  public void shutdown() {
    netEth.shutdown();
    websocketService.ifPresent(WebSocketService::close);
  }
}
