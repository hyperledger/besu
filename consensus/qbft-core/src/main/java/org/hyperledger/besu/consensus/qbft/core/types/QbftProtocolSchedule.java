/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.core.types;

/**
 * Provides the ability to select the appropriate QbftProtocolSpec containing the validation and
 * import for the supplied block header.
 */
public interface QbftProtocolSchedule {

  /**
   * Returns the QbftProtocolSpec for the supplied block header.
   *
   * @param header The block header to select the appropriate QbftProtocolSpec for
   * @return The QbftProtocolSpec for the supplied block header
   */
  QbftProtocolSpec getByBlockHeader(QbftBlockHeader header);
}
