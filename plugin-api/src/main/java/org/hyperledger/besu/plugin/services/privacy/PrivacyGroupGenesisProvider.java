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

import org.hyperledger.besu.plugin.data.PrivacyGenesis;

import org.apache.tuweni.bytes.Bytes;

/** A way to initiate private state with a genesis */
@FunctionalInterface
public interface PrivacyGroupGenesisProvider {
  /**
   * Allows you to specify a custom private genesis to apply when initialising a privacy group
   *
   * @param privacyGroupId the privacyGroupId
   * @param blockNumber the block height
   * @return the privacy genesis to apply
   */
  PrivacyGenesis getPrivacyGenesis(Bytes privacyGroupId, long blockNumber);
}
