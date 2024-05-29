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
package org.hyperledger.besu.consensus.ibft.messagedata;

/** Message codes for iBFT v2 messages */
public interface IbftV2 {
  /** The constant PROPOSAL. */
  int PROPOSAL = 0;

  /** The constant PREPARE. */
  int PREPARE = 1;

  /** The constant COMMIT. */
  int COMMIT = 2;

  /** The constant ROUND_CHANGE. */
  int ROUND_CHANGE = 3;

  /** The constant MESSAGE_SPACE. */
  int MESSAGE_SPACE = 4;
}
