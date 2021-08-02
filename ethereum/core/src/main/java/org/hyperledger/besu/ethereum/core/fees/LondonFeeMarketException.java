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
package org.hyperledger.besu.ethereum.core.fees;

public class LondonFeeMarketException extends Exception {

  public static LondonFeeMarketException MissingBaseFeeFromBlockHeader() {
    return new LondonFeeMarketException("Invalid block header: basefee should be specified");
  }

  public static LondonFeeMarketException BaseFeePresentBeforeForkBlock() {
    return new LondonFeeMarketException("Invalid block header: basefee should not be present before London fork block");
  }

  private LondonFeeMarketException(final String reason) {
    super(reason);
  }
}
