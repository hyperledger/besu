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

import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.GoQuorumEthGetQuorumPayload;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.GoQuorumSendRawPrivateTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv.GoQuorumStoreRawPrivateTransaction;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class GoQuorumJsonRpcPrivacyMethods extends PrivacyApiGroupJsonRpcMethods {

  private final Optional<GoQuorumPrivacyParameters> goQuorumParameters;

  public GoQuorumJsonRpcPrivacyMethods(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final TransactionPool transactionPool,
      final PrivacyParameters privacyParameters) {
    super(blockchainQueries, protocolSchedule, transactionPool, privacyParameters);
    this.goQuorumParameters = privacyParameters.getGoQuorumPrivacyParameters();
  }

  @Override
  protected Map<String, JsonRpcMethod> create(
      final PrivacyController privacyController,
      final PrivacyIdProvider enclavePublicKeyProvider,
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory) {

    if (goQuorumParameters.isPresent()) {
      final GoQuorumEnclave enclave = goQuorumParameters.get().enclave();
      return mapOf(
          new GoQuorumSendRawPrivateTransaction(
              enclave, getTransactionPool(), enclavePublicKeyProvider),
          new GoQuorumStoreRawPrivateTransaction(enclave),
          new GoQuorumEthGetQuorumPayload(enclave));
    } else {
      return Collections.emptyMap();
    }
  }

  @Override
  protected String getApiGroup() {
    return RpcApis.GOQUORUM.name();
  }
}
