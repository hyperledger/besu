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
package org.hyperledger.besu.ethereum.eth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EthProtocolConfiguration {

  public static final int DEFAULT_MAX_GET_BLOCK_HEADERS = 192;
  public static final int DEFAULT_MAX_GET_BLOCK_BODIES = 128;
  public static final int DEFAULT_MAX_GET_RECEIPTS = 256;
  public static final int DEFAULT_MAX_GET_NODE_DATA = 384;
  public static final int DEFAULT_MAX_GET_POOLED_TRANSACTIONS = 256;
  public static final boolean DEFAULT_ETH_65_ENABLED = false;

  @Builder.Default int maxGetBlockHeaders = DEFAULT_MAX_GET_BLOCK_HEADERS;
  @Builder.Default int maxGetBlockBodies = DEFAULT_MAX_GET_BLOCK_BODIES;
  @Builder.Default int maxGetReceipts = DEFAULT_MAX_GET_RECEIPTS;
  @Builder.Default int maxGetNodeData = DEFAULT_MAX_GET_NODE_DATA;
  @Builder.Default int maxGetPooledTransactions = DEFAULT_MAX_GET_POOLED_TRANSACTIONS;
  @Builder.Default boolean eth65Enabled = DEFAULT_ETH_65_ENABLED;

  public static class EthProtocolConfigurationBuilder {
    public EthProtocolConfigurationBuilder allLimits(final int n) {
      this.maxGetBlockHeaders(n)
          .maxGetBlockBodies(n)
          .maxGetReceipts(n)
          .maxGetNodeData(n)
          .maxGetPooledTransactions(n);
      return this;
    }
  }
}
