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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PRIV_CREATE_PRIVACY_GROUP;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PRIV_DELETE_PRIVACY_GROUP;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PRIV_FIND_PRIVACY_GROUP;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PrivJsonRpcMethodsTest {

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private TransactionPool transactionPool;
  @Mock private PrivacyParameters privacyParameters;
  @Mock private FilterManager filterManager;
  private PrivJsonRpcMethods privJsonRpcMethods;

  @BeforeEach
  public void setup() {
    privJsonRpcMethods =
        new PrivJsonRpcMethods(
            blockchainQueries, protocolSchedule, transactionPool, privacyParameters, filterManager);

    lenient().when(privacyParameters.isEnabled()).thenReturn(true);
  }

  @Test
  public void offchainPrivacyGroupMethodsAreDisabledWhenFlexiblePrivacyGroupIsEnabled() {
    when(privacyParameters.isFlexiblePrivacyGroupsEnabled()).thenReturn(true);
    final Map<String, JsonRpcMethod> rpcMethods = privJsonRpcMethods.create();

    assertThat(rpcMethods.get(PRIV_CREATE_PRIVACY_GROUP.getMethodName())).isNull();
    assertThat(rpcMethods.get(PRIV_DELETE_PRIVACY_GROUP.getMethodName())).isNull();
    assertThat(rpcMethods.get(PRIV_FIND_PRIVACY_GROUP.getMethodName())).isNull();
  }

  @Test
  public void offchainPrivacyGroupMethodsAreEnabledWhenFlexiblePrivacyGroupIsDisabled() {
    when(privacyParameters.isFlexiblePrivacyGroupsEnabled()).thenReturn(false);
    final Map<String, JsonRpcMethod> rpcMethods = privJsonRpcMethods.create();

    assertThat(rpcMethods.get(PRIV_CREATE_PRIVACY_GROUP.getMethodName())).isNotNull();
    assertThat(rpcMethods.get(PRIV_DELETE_PRIVACY_GROUP.getMethodName())).isNotNull();
    assertThat(rpcMethods.get(PRIV_FIND_PRIVACY_GROUP.getMethodName())).isNotNull();
  }
}
