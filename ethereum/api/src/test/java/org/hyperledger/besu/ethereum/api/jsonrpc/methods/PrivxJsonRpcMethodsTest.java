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
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PRIVX_FIND_PRIVACY_GROUP;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod.PRIVX_FIND_PRIVACY_GROUP_OLD;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.privx.PrivxFindFlexiblePrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.privx.PrivxFindOnchainPrivacyGroup;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivxJsonRpcMethodsTest {

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private TransactionPool transactionPool;
  @Mock private PrivacyParameters privacyParameters;

  private PrivxJsonRpcMethods privxJsonRpcMethods;

  @Before
  public void setup() {
    privxJsonRpcMethods =
        new PrivxJsonRpcMethods(
            blockchainQueries, protocolSchedule, transactionPool, privacyParameters);

    lenient().when(privacyParameters.isEnabled()).thenReturn(true);
  }

  @Test
  public void privxFindPrivacyGroupMethodIsDisabledWhenFlexiblePrivacyGroupIsDisabled() {
    when(privacyParameters.isFlexiblePrivacyGroupsEnabled()).thenReturn(false);
    final Map<String, JsonRpcMethod> rpcMethods = privxJsonRpcMethods.create();
    final JsonRpcMethod method = rpcMethods.get(PRIVX_FIND_PRIVACY_GROUP.getMethodName());

    assertThat(method).isNull();
  }

  @Test
  public void privxFindPrivacyGroupMethodIsEnabledWhenFlexiblePrivacyGroupIsEnabled() {
    when(privacyParameters.isFlexiblePrivacyGroupsEnabled()).thenReturn(true);
    final Map<String, JsonRpcMethod> rpcMethods = privxJsonRpcMethods.create();
    final JsonRpcMethod method = rpcMethods.get(PRIVX_FIND_PRIVACY_GROUP.getMethodName());

    assertThat(method).isNotNull();
    assertThat(method).isInstanceOf(PrivxFindFlexiblePrivacyGroup.class);
  }

  @Deprecated
  @Test
  public void privxFindOnchainPrivacyGroupMethodIsStillEnabled() {
    when(privacyParameters.isFlexiblePrivacyGroupsEnabled()).thenReturn(true);
    final Map<String, JsonRpcMethod> rpcMethods = privxJsonRpcMethods.create();
    final JsonRpcMethod method = rpcMethods.get(PRIVX_FIND_PRIVACY_GROUP_OLD.getMethodName());

    assertThat(method).isNotNull();
    assertThat(method).isInstanceOf(PrivxFindOnchainPrivacyGroup.class);
  }
}
