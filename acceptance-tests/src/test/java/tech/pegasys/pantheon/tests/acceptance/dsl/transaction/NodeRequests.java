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
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ibft2.Ibft2RequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.login.LoginRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.miner.MinerRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.net.CustomRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.PermissioningJsonRpcRequestFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory;

import java.util.Optional;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

public class NodeRequests {

  private final Web3j netEth;
  private final CliqueRequestFactory clique;
  private final Ibft2RequestFactory ibft;
  private final PermissioningJsonRpcRequestFactory perm;
  private final AdminRequestFactory admin;
  private final PrivacyRequestFactory privacy;
  private final CustomRequestFactory custom;
  private final Optional<WebSocketService> websocketService;
  private final LoginRequestFactory login;
  private final MinerRequestFactory miner;

  public NodeRequests(
      final Web3j netEth,
      final CliqueRequestFactory clique,
      final Ibft2RequestFactory ibft,
      final PermissioningJsonRpcRequestFactory perm,
      final AdminRequestFactory admin,
      final PrivacyRequestFactory privacy,
      final CustomRequestFactory custom,
      final MinerRequestFactory miner,
      final Optional<WebSocketService> websocketService,
      final LoginRequestFactory login) {
    this.netEth = netEth;
    this.clique = clique;
    this.ibft = ibft;
    this.perm = perm;
    this.admin = admin;
    this.privacy = privacy;
    this.custom = custom;
    this.miner = miner;
    this.websocketService = websocketService;
    this.login = login;
  }

  public Web3j eth() {
    return netEth;
  }

  public Web3j net() {
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

  public CustomRequestFactory custom() {
    return custom;
  }

  public PrivacyRequestFactory privacy() {
    return privacy;
  }

  public LoginRequestFactory login() {
    return login;
  }

  public MinerRequestFactory miner() {
    return miner;
  }

  public void shutdown() {
    netEth.shutdown();
    websocketService.ifPresent(WebSocketService::close);
  }
}
