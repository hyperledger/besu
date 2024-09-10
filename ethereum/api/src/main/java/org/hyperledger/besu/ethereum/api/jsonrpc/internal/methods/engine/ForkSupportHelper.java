/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Optional;

public class ForkSupportHelper {
  public static ValidationResult<RpcErrorType> validateForkSupported(
      final HardforkId hardforkId,
      final Optional<Long> maybeForkMilestone,
      final long blockTimestamp) {
    if (maybeForkMilestone.isEmpty()) {
      return ValidationResult.invalid(
          RpcErrorType.UNSUPPORTED_FORK,
          "Configuration error, no schedule for " + hardforkId.name() + " fork set");
    }

    if (blockTimestamp < maybeForkMilestone.get()) {
      return ValidationResult.invalid(
          RpcErrorType.UNSUPPORTED_FORK,
          hardforkId.name() + " configured to start at timestamp: " + maybeForkMilestone.get());
    }

    return ValidationResult.valid();
  }
}
