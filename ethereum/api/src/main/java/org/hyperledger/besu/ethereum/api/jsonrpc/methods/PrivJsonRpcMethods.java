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

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivCreatePrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivDeletePrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivDistributeRawTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivFindPrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetPrivacyPrecompileAddress;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetPrivateTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetTransactionCount;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.PrivGetTransactionReceipt;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionHandler;

import java.util.Map;

public class PrivJsonRpcMethods extends PrivacyApiGroupJsonRpcMethods {

  public PrivJsonRpcMethods(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule<?> protocolSchedule,
      final TransactionPool transactionPool,
      final PrivacyParameters privacyParameters) {
    super(blockchainQueries, protocolSchedule, transactionPool, privacyParameters);
  }

  @Override
  protected RpcApi getApiGroup() {
    return RpcApis.PRIV;
  }

  @Override
  protected Map<String, JsonRpcMethod> create(
      final PrivateTransactionHandler privateTransactionHandler, final Enclave enclave) {
    return mapOf(
        new PrivGetTransactionReceipt(getBlockchainQueries(), enclave, getPrivacyParameters()),
        new PrivCreatePrivacyGroup(
            new Enclave(getPrivacyParameters().getEnclaveUri()), getPrivacyParameters()),
        new PrivDeletePrivacyGroup(
            new Enclave(getPrivacyParameters().getEnclaveUri()), getPrivacyParameters()),
        new PrivFindPrivacyGroup(new Enclave(getPrivacyParameters().getEnclaveUri())),
        new PrivGetPrivacyPrecompileAddress(getPrivacyParameters()),
        new PrivGetTransactionCount(getPrivateNonceProvider()),
        new PrivGetPrivateTransaction(getBlockchainQueries(), enclave, getPrivacyParameters()),
        new PrivDistributeRawTransaction(privateTransactionHandler, getTransactionPool()));
  }
}
