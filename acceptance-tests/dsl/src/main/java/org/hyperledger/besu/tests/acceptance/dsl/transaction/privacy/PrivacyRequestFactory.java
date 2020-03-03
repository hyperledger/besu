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

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivateTransactionGroupResponse;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;

public class PrivacyRequestFactory {

  public static class GetPrivacyPrecompileAddressResponse extends Response<Address> {}

  public static class GetPrivateTransactionResponse
      extends Response<PrivateTransactionGroupResponse> {}

  public static class CreatePrivacyGroupResponse extends Response<String> {}

  public static class DeletePrivacyGroupResponse extends Response<String> {}

  public static class FindPrivacyGroupResponse extends Response<PrivacyGroup[]> {}

  public static class SendRawTransactionResponse extends Response<Hash> {}

  public static class GetTransactionReceiptResponse extends Response<PrivateTransactionReceipt> {}

  public static class GetTransactionCountResponse extends Response<Integer> {

    final Integer count;

    @JsonCreator
    public GetTransactionCountResponse(@JsonProperty("result") final String result) {
      this.count = Integer.decode(result);
    }

    public Integer getCount() {
      return count;
    }
  }

  public static class GetCodeResponse extends Response<String> {}

  public Request<?, PrivDistributeTransactionResponse> privDistributeTransaction(
      final String signedPrivateTransaction) {
    return new Request<>(
        "priv_distributeRawTransaction",
        singletonList(signedPrivateTransaction),
        web3jService,
        PrivDistributeTransactionResponse.class);
  }

  private final Besu besuClient;
  private final Web3jService web3jService;

  public PrivacyRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
    this.besuClient = Besu.build(web3jService);
  }

  public Besu getBesuClient() {
    return besuClient;
  }

  public static class PrivDistributeTransactionResponse extends Response<String> {

    public PrivDistributeTransactionResponse() {}

    public String getTransactionKey() {
      return getResult();
    }
  }

  public Request<?, GetPrivacyPrecompileAddressResponse> privGetPrivacyPrecompileAddress() {
    return new Request<>(
        "priv_getPrivacyPrecompileAddress",
        Collections.emptyList(),
        web3jService,
        GetPrivacyPrecompileAddressResponse.class);
  }

  public Request<?, GetPrivateTransactionResponse> privGetPrivateTransaction(
      final Hash transactionHash) {
    return new Request<>(
        "priv_getPrivateTransaction",
        singletonList(transactionHash.toHexString()),
        web3jService,
        GetPrivateTransactionResponse.class);
  }

  public Request<?, CreatePrivacyGroupResponse> privCreatePrivacyGroup(
      final CreatePrivacyGroupParameter params) {
    return new Request<>(
        "priv_createPrivacyGroup",
        singletonList(params),
        web3jService,
        CreatePrivacyGroupResponse.class);
  }

  public Request<?, DeletePrivacyGroupResponse> privDeletePrivacyGroup(final String groupId) {
    return new Request<>(
        "priv_deletePrivacyGroup",
        singletonList(groupId),
        web3jService,
        DeletePrivacyGroupResponse.class);
  }

  public Request<?, FindPrivacyGroupResponse> privFindPrivacyGroup(final String[] groupMembers) {
    return new Request<>(
        "priv_findPrivacyGroup",
        singletonList(groupMembers),
        web3jService,
        FindPrivacyGroupResponse.class);
  }

  public Request<?, SendRawTransactionResponse> eeaSendRawTransaction(final String transaction) {
    return new Request<>(
        "eea_sendRawTransaction",
        singletonList(transaction),
        web3jService,
        SendRawTransactionResponse.class);
  }

  public Request<?, GetTransactionReceiptResponse> privGetTransactionReceipt(
      final Hash transactionHash) {
    return new Request<>(
        "priv_getTransactionReceipt",
        singletonList(transactionHash.toHexString()),
        web3jService,
        GetTransactionReceiptResponse.class);
  }

  public Request<?, GetTransactionCountResponse> privGetTransactionCount(final Object[] params) {
    return new Request<>(
        "priv_getTransactionCount",
        List.of(params),
        web3jService,
        GetTransactionCountResponse.class);
  }

  public Request<?, GetTransactionCountResponse> privGetEeaTransactionCount(final Object[] params) {
    return new Request<>(
        "priv_getEeaTransactionCount",
        List.of(params),
        web3jService,
        GetTransactionCountResponse.class);
  }

  public Request<?, GetCodeResponse> privGetCode(
      final String privacyGroupId, final String contractAddress, final String blockParameter) {
    return new Request<>(
        "priv_getCode",
        List.of(privacyGroupId, contractAddress, blockParameter),
        web3jService,
        GetCodeResponse.class);
  }
}
