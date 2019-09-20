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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.BytesValue;

/**
 * A pre-compiled contract.
 *
 * <p>It corresponds to one of the function defined in Appendix E of the Yellow Paper (rev.
 * a91c29c).
 */
public interface PrecompiledContract {

  /**
   * Returns the pre-compiled contract name.
   *
   * @return the pre-compiled contract name
   */
  String getName();

  /**
   * Gas requirement for the contract.
   *
   * @param input the input for the pre-compiled contract (on which the gas requirement may or may
   *     not depend).
   * @return the gas requirement (cost) for the pre-compiled contract.
   */
  Gas gasRequirement(BytesValue input);

  /**
   * Executes the pre-compiled contract.
   *
   * @param input the input for the pre-compiled contract.
   * @param messageFrame context for this message
   * @return the output of the pre-compiled contract.
   */
  BytesValue compute(BytesValue input, MessageFrame messageFrame);
}
