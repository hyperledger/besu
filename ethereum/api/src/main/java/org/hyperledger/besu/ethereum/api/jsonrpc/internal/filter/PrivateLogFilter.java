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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;

public class PrivateLogFilter extends LogFilter {

  private final String privacyGroupId;
  private final String privacyUserId;

  PrivateLogFilter(
      final String id,
      final String privacyGroupId,
      final String privacyUserId,
      final BlockParameter fromBlock,
      final BlockParameter toBlock,
      final LogsQuery logsQuery) {
    super(id, fromBlock, toBlock, logsQuery);
    this.privacyGroupId = privacyGroupId;
    this.privacyUserId = privacyUserId;
  }

  public String getPrivacyGroupId() {
    return privacyGroupId;
  }

  public String getPrivacyUserId() {
    return privacyUserId;
  }
}
