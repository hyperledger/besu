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
package org.hyperledger.besu.plugin.data;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.PublicKey;
import org.hyperledger.besu.plugin.Unstable;

/** A consolidation request is an operation sent to the Beacon Node for processing. */
@Unstable
public interface ConsolidationRequest {

  /**
   * Withdrawal credential (0x01) associated with the validator
   *
   * @return withdrawal credential address
   */
  Address getSourceAddress();

  /**
   * Public key of the address that sends the consolidation
   *
   * @return public key of sender
   */
  PublicKey getSourcePubkey();

  /**
   * Public key of the address to receives the consolidation
   *
   * @return public key of target
   */
  PublicKey getTargetPubkey();
}
