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

public class EIP1559Config {
  public static final long BASEFEE_MAX_CHANGE_DENOMINATOR = 8L;
  public static final long TARGET_GAS_USED = 10000000L;
  public static final long MAX_GAS_EIP1559 = 16000000L;
  public static final long EIP1559_DECAY_RANGE = MAX_GAS_EIP1559 / 20L;
  public static final long EIP1559_GAS_INCREMENT_AMOUNT =
      (MAX_GAS_EIP1559 / 2) / EIP1559_DECAY_RANGE;
  public static final long INITIAL_BASEFEE = 1000000000L;
  public static final long PER_TX_GASLIMIT = 8000000L;
}
