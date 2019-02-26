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

import java.util.Optional;

import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.websocket.WebSocketService;

public class JsonRequestFactories {

  private final JsonRpc2_0Web3j netEth;
  private final CliqueJsonRpcRequestFactory clique;
  private final IbftJsonRpcRequestFactory ibft;
  private final PermissioningJsonRpcRequestFactory perm;
  private final AdminJsonRpcRequestFactory admin;
  private final EeaJsonRpcRequestFactory eea;
  private final Optional<WebSocketService> websocketService;

  public JsonRequestFactories(
      final JsonRpc2_0Web3j netEth,
      final CliqueJsonRpcRequestFactory clique,
      final IbftJsonRpcRequestFactory ibft,
      final PermissioningJsonRpcRequestFactory perm,
      final AdminJsonRpcRequestFactory admin,
      final EeaJsonRpcRequestFactory eea,
      final Optional<WebSocketService> websocketService) {
    this.netEth = netEth;
    this.clique = clique;
    this.ibft = ibft;
    this.perm = perm;
    this.admin = admin;
    this.eea = eea;
    this.websocketService = websocketService;
  }

  public JsonRpc2_0Web3j eth() {
    return netEth;
  }

  public JsonRpc2_0Web3j net() {
    return netEth;
  }

  public CliqueJsonRpcRequestFactory clique() {
    return clique;
  }

  public IbftJsonRpcRequestFactory ibft() {
    return ibft;
  }

  public PermissioningJsonRpcRequestFactory perm() {
    return perm;
  }

  public AdminJsonRpcRequestFactory admin() {
    return admin;
  }

  public EeaJsonRpcRequestFactory eea() {
    return eea;
  }

  public void shutdown() {
    netEth.shutdown();
    websocketService.ifPresent(WebSocketService::close);
  }
}
