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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Optional;

@Deprecated(since = "24.12.0")
public class PrivUtil {

  public static void checkMembershipForAuthenticatedUser(
      final PrivacyController privacyController,
      final PrivacyIdProvider privacyIdProvider,
      final JsonRpcRequestContext request,
      final String privacyGroupId,
      final long blockNumber) {
    final String privacyUserId = privacyIdProvider.getPrivacyUserId(request.getUser());
    // check group membership at previous block (they could have been removed as of blockNumber but
    // membership will be correct as at previous block)
    final long blockNumberToCheck = blockNumber == 0 ? 0 : blockNumber - 1;
    privacyController.verifyPrivacyGroupContainsPrivacyUserId(
        privacyGroupId, privacyUserId, Optional.of(blockNumberToCheck));
  }
}
