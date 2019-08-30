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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.priv;

import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;

import java.util.Collections;
import java.util.List;

import org.assertj.core.util.Lists;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

public class PrivRequestFactory {

  public static class PrivCreatePrivacyGroupResponse extends Response<String> {}

  public static class PrivFindPrivacyGroupResponse extends Response<List<PrivacyGroup>> {}

  private final Web3jService web3jService;

  public PrivRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
  }

  public Request<?, EthGetTransactionCount> privGetTransactionCount(
      final String accountAddress, final String privacyGroupId) {
    return new Request<>(
        "priv_getTransactionCount",
        Lists.newArrayList(accountAddress, privacyGroupId),
        web3jService,
        EthGetTransactionCount.class);
  }

  public Request<?, PrivCreatePrivacyGroupResponse> privCreatePrivacyGroup(
      final List<String> addresses, final String name, final String description) {
    return new Request<>(
        "priv_createPrivacyGroup",
        Lists.newArrayList(
            new CreatePrivacyGroupParameter(addresses.toArray(new String[] {}), name, description)),
        web3jService,
        PrivCreatePrivacyGroupResponse.class);
  }

  public Request<?, PrivCreatePrivacyGroupResponse> privCreatePrivacyGroupWithoutName(
      final List<String> addresses, final String description) {
    return new Request<>(
        "priv_createPrivacyGroup",
        Collections.singletonList(
            new CreatePrivacyGroupParameter(addresses.toArray(new String[] {}), null, description)),
        web3jService,
        PrivCreatePrivacyGroupResponse.class);
  }

  public Request<?, PrivCreatePrivacyGroupResponse> privCreatePrivacyGroupWithoutDescription(
      final List<String> addresses, final String name) {
    return new Request<>(
        "priv_createPrivacyGroup",
        Collections.singletonList(
            new CreatePrivacyGroupParameter(addresses.toArray(new String[] {}), name, null)),
        web3jService,
        PrivCreatePrivacyGroupResponse.class);
  }

  public Request<?, PrivCreatePrivacyGroupResponse> privCreatePrivacyGroupWithoutOptionalParams(
      final List<String> addresses) {
    return new Request<>(
        "priv_createPrivacyGroup",
        Collections.singletonList(
            new CreatePrivacyGroupParameter(addresses.toArray(new String[] {}), null, null)),
        web3jService,
        PrivCreatePrivacyGroupResponse.class);
  }

  public Request<?, PrivFindPrivacyGroupResponse> privFindPrivacyGroup(
      final List<String> addresses) {
    return new Request<>(
        "priv_findPrivacyGroup",
        Collections.singletonList(addresses),
        web3jService,
        PrivFindPrivacyGroupResponse.class);
  }
}
