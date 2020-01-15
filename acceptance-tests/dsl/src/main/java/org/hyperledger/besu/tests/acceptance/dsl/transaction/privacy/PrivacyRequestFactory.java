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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy;

import static java.util.Collections.singletonList;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;

public class PrivacyRequestFactory {
  private final Besu besuClient;
  private final Web3jService web3jService;

  public PrivacyRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
    this.besuClient = Besu.build(web3jService);
  }

  public Besu getBesuClient() {
    return besuClient;
  }

  public Request<?, PrivxCreatePrivacyGroupResponse> privxCreatePrivacyGroup(
      final List<String> addresses, final String name, final String description) {
    return new Request<>(
        "privx_createPrivacyGroup",
        singletonList(new PrivxCreatePrivacyGroupRequest(addresses, name, description)),
        web3jService,
        PrivxCreatePrivacyGroupResponse.class);
  }

  public Request<?, PrivDistributeTransactionResponse> privDistributeTransaction(
      final String signedPrivateTransaction) {
    return new Request<>(
        "priv_distributeRawTransaction",
        singletonList(signedPrivateTransaction),
        web3jService,
        PrivDistributeTransactionResponse.class);
  }

  public static class PrivDistributeTransactionResponse extends Response<String> {

    public PrivDistributeTransactionResponse() {}

    public String getTransactionKey() {
      return getResult();
    }
  }

  private static class PrivxCreatePrivacyGroupRequest {
    private final List<String> addresses;
    private final String name;
    private final String description;

    public PrivxCreatePrivacyGroupRequest(
        @JsonProperty("addresses") final List<String> addresses,
        @JsonProperty("name") final String name,
        @JsonProperty("description") final String description) {
      this.addresses = addresses;
      this.name = name;
      this.description = description;
    }

    public List<String> getAddresses() {
      return addresses;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }
  }

  public static class PrivxCreatePrivacyGroupResponse extends Response<PrivxCreatePrivacyGroup> {
    public PrivxCreatePrivacyGroupResponse() {}

    public PrivxCreatePrivacyGroup getPrivxCreatePrivacyGroup() {
      return getResult();
    }
  }

  public static class PrivxCreatePrivacyGroup {
    final String privacyGroupId;
    final String transactionHash;

    @JsonCreator
    public PrivxCreatePrivacyGroup(
        @JsonProperty("privacyGroupId") final String privacyGroupId,
        @JsonProperty("transactionHash") final String transactionHash) {
      this.privacyGroupId = privacyGroupId;
      this.transactionHash = transactionHash;
    }

    public String getPrivacyGroupId() {
      return privacyGroupId;
    }

    public String getTransactionHash() {
      return transactionHash;
    }
  }
}
