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
package org.hyperledger.besu.tests.acceptance.dsl.transaction;

import org.hyperledger.besu.tests.acceptance.dsl.transaction.admin.AdminRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.bft.BftRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.clique.CliqueRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.login.LoginRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.miner.MinerRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.CustomRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.PermissioningJsonRpcRequestFactory;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.txpool.TxPoolRequestFactory;

import java.util.Optional;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.websocket.WebSocketService;

public class NodeRequests {
  private final Web3jService web3jService;
  private final Web3j netEth;
  private final CliqueRequestFactory clique;
  private final BftRequestFactory bft;
  private final PermissioningJsonRpcRequestFactory perm;
  private final AdminRequestFactory admin;
  private final CustomRequestFactory custom;
  private final Optional<WebSocketService> websocketService;
  private final LoginRequestFactory login;
  private final MinerRequestFactory miner;
  private final TxPoolRequestFactory txPool;

  public NodeRequests(
      final Web3jService web3jService,
      final Web3j netEth,
      final CliqueRequestFactory clique,
      final BftRequestFactory bft,
      final PermissioningJsonRpcRequestFactory perm,
      final AdminRequestFactory admin,
      final CustomRequestFactory custom,
      final MinerRequestFactory miner,
      final TxPoolRequestFactory txPool,
      final Optional<WebSocketService> websocketService,
      final LoginRequestFactory login) {
    this.web3jService = web3jService;
    this.netEth = netEth;
    this.clique = clique;
    this.bft = bft;
    this.perm = perm;
    this.admin = admin;
    this.custom = custom;
    this.miner = miner;
    this.txPool = txPool;
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

  public BftRequestFactory bft() {
    return bft;
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

  public LoginRequestFactory login() {
    return login;
  }

  public MinerRequestFactory miner() {
    return miner;
  }

  public TxPoolRequestFactory txPool() {
    return txPool;
  }

  public void shutdown() {
    netEth.shutdown();
    websocketService.ifPresent(WebSocketService::close);
  }

  public Web3jService getWeb3jService() {
    return web3jService;
  }
}
