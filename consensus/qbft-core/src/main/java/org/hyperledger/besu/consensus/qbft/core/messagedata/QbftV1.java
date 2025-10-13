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
package org.hyperledger.besu.consensus.qbft.core.messagedata;

/** Message codes for QBFT v1 messages */
public interface QbftV1 {
  /** The constant PROPOSAL. */
  int PROPOSAL = 0x12;

  /** The constant PREPARE. */
  int PREPARE = 0x13;

  /** The constant COMMIT. */
  int COMMIT = 0x14;

  /** The constant ROUND_CHANGE. */
  int ROUND_CHANGE = 0x15;

  /** The constant MESSAGE_SPACE. */
  int MESSAGE_SPACE = 0x16;
}
