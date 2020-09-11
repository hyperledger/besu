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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.cache.TransactionLogBloomCacher;
import org.hyperledger.besu.ethereum.api.query.cache.TransactionLogBloomCacher.CachingStatus;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class AdminGenerateLogBloomCacheTest {

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionLogBloomCacher transactionLogBloomCacher;
  @Captor private ArgumentCaptor<Long> fromBlock;
  @Captor private ArgumentCaptor<Long> toBlock;

  private AdminGenerateLogBloomCache method;

  @Before
  public void setup() {
    method = new AdminGenerateLogBloomCache(blockchainQueries);
  }

  @Test
  public void requestWithZeroParameters_NoCacher_returnsNull() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_generateLogBloomCache", new String[] {}));

    when(blockchainQueries.getTransactionLogBloomCacher()).thenReturn(Optional.empty());

    final JsonRpcResponse actualResponse = method.response(request);

    verifyNoMoreInteractions(blockchainQueries);

    assertThat(actualResponse).isInstanceOf(JsonRpcSuccessResponse.class);
    assertThat(((JsonRpcSuccessResponse) actualResponse).getResult()).isNull();
  }

  @Test
  public void testParameterized() {
    final Object[][] testVector = {
      {new String[] {}, 0L, -1L},
      {new String[] {"earliest"}, 0L, Long.MAX_VALUE},
      {new String[] {"latest"}, Long.MAX_VALUE, Long.MAX_VALUE},
      {new String[] {"pending"}, Long.MAX_VALUE, Long.MAX_VALUE},
      {new String[] {"0x50"}, 0x50L, Long.MAX_VALUE},
      {new String[] {"earliest", "earliest"}, 0L, 0L},
      {new String[] {"latest", "earliest"}, Long.MAX_VALUE, 0L},
      {new String[] {"pending", "earliest"}, Long.MAX_VALUE, 0L},
      {new String[] {"0x50", "earliest"}, 0x50L, 0L},
      {new String[] {"earliest", "latest"}, 0L, Long.MAX_VALUE},
      {new String[] {"latest", "latest"}, Long.MAX_VALUE, Long.MAX_VALUE},
      {new String[] {"pending", "latest"}, Long.MAX_VALUE, Long.MAX_VALUE},
      {new String[] {"0x50", "latest"}, 0x50L, Long.MAX_VALUE},
      {new String[] {"earliest", "pending"}, 0L, Long.MAX_VALUE},
      {new String[] {"latest", "pending"}, Long.MAX_VALUE, Long.MAX_VALUE},
      {new String[] {"pending", "pending"}, Long.MAX_VALUE, Long.MAX_VALUE},
      {new String[] {"0x50", "pending"}, 0x50L, Long.MAX_VALUE},
      {new String[] {"earliest", "0x100"}, 0L, 0x100L},
      {new String[] {"latest", "0x100"}, Long.MAX_VALUE, 0x100L},
      {new String[] {"pending", "0x100"}, Long.MAX_VALUE, 0x100L},
      {new String[] {"0x50", "0x100"}, 0x50L, 0x100L},
      {new String[] {"earliest", "0x10"}, 0L, 0x10L},
      {new String[] {"latest", "0x10"}, Long.MAX_VALUE, 0x10L},
      {new String[] {"pending", "0x10"}, Long.MAX_VALUE, 0x10L},
      {new String[] {"0x50", "0x10"}, 0x50L, 0x10L}
    };

    for (final Object[] test : testVector) {
      System.out.println("Vector - " + List.of((String[]) test[0]));
      testMockedResult((String[]) test[0], (Long) test[1], (Long) test[2]);
    }
  }

  public void testMockedResult(
      final String[] args, final long expectedFromBlock, final long expectedToBlock) {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "admin_generateLogBloomCache", args));

    final CachingStatus expectedStatus = new CachingStatus();

    when(blockchainQueries.getTransactionLogBloomCacher())
        .thenReturn(Optional.of(transactionLogBloomCacher));
    when(transactionLogBloomCacher.requestCaching(fromBlock.capture(), toBlock.capture()))
        .thenReturn(expectedStatus);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isInstanceOf(JsonRpcSuccessResponse.class);
    assertThat(((JsonRpcSuccessResponse) actualResponse).getResult()).isSameAs(expectedStatus);
    assertThat(fromBlock.getValue()).isEqualTo(expectedFromBlock);
    assertThat(toBlock.getValue()).isEqualTo(expectedToBlock);
    verifyNoMoreInteractions(blockchainQueries);
  }
}
