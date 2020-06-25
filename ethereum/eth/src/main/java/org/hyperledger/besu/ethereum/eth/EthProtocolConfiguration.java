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
  @Builder.Default Integer maxGetBlockHeaders = 192;
  @Builder.Default Integer maxGetBlockBodies = 128;
  @Builder.Default Integer maxGetReceipts = 256;
  @Builder.Default Integer maxGetNodeData = 384;
  @Builder.Default Integer maxGetPooledTransactions = 256;
  @Builder.Default boolean eth65Enabled = false;
}
