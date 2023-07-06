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
package org.hyperledger.besu.plugin.services.privacy;

import java.util.Optional;

/**
 * When in a multi-tenant environment you need to decided if an authz user can access a privacyGroup
 */
@FunctionalInterface
public interface PrivacyGroupAuthProvider {
  /**
   * Should this privacyUserId be able to access this privacyGroupId at this blockNumber
   *
   * @param privacyGroupId the privacyGroupId
   * @param privacyUserId the authz privacyUserId when in a multi-tenant environment
   * @param blockNumber the block height it's happening at
   * @return if the can access that privacyUserId
   */
  boolean canAccess(String privacyGroupId, String privacyUserId, Optional<Long> blockNumber);
}
