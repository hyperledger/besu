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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.perm;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;

public class PermissioningJsonRpcRequestFactory {

  public static class AddAccountsToWhitelistResponse extends Response<String> {}

  public static class RemoveAccountsFromWhitelistResponse extends Response<String> {}

  public static class GetAccountsWhitelistResponse extends Response<List<String>> {}

  public static class AddNodeResponse extends Response<String> {}

  public static class RemoveNodeResponse extends Response<String> {}

  public static class GetNodesWhitelistResponse extends Response<List<String>> {}

  private final Web3jService web3jService;

  public PermissioningJsonRpcRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
  }

  Request<?, AddNodeResponse> addNodesToWhitelist(final List<URI> enodeList) {
    return new Request<>(
        "perm_addNodesToAllowlist",
        Collections.singletonList(enodeList),
        web3jService,
        AddNodeResponse.class);
  }

  Request<?, RemoveNodeResponse> removeNodesFromWhitelist(final List<URI> enodeList) {
    return new Request<>(
        "perm_removeNodesFromAllowlist",
        Collections.singletonList(enodeList),
        web3jService,
        RemoveNodeResponse.class);
  }

  Request<?, GetNodesWhitelistResponse> getNodesWhitelist() {
    return new Request<>(
        "perm_getNodesAllowlist",
        Collections.emptyList(),
        web3jService,
        GetNodesWhitelistResponse.class);
  }

  Request<?, GetAccountsWhitelistResponse> getAccountsWhitelist() {
    return new Request<>(
        "perm_getAccountsAllowlist", null, web3jService, GetAccountsWhitelistResponse.class);
  }

  Request<?, AddAccountsToWhitelistResponse> addAccountsToWhitelist(final List<String> accounts) {
    return new Request<>(
        "perm_addAccountsToAllowlist",
        Collections.singletonList(accounts),
        web3jService,
        AddAccountsToWhitelistResponse.class);
  }

  Request<?, RemoveAccountsFromWhitelistResponse> removeAccountsFromWhitelist(
      final List<String> accounts) {
    return new Request<>(
        "perm_removeAccountsFromAllowlist",
        Collections.singletonList(accounts),
        web3jService,
        RemoveAccountsFromWhitelistResponse.class);
  }
}
