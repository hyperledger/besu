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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.ethereum.vm.FixedStack;

/**
 * This class describes the behaviour of the Return stack introduce on the
 * https://eips.ethereum.org/EIPS/eip-2315
 */
public class ReturnStack extends FixedStack<Integer> {

  // as defined on https://eips.ethereum.org/EIPS/eip-2315
  private static final int MAX_RETURN_STACK_SIZE = 1023;

  public ReturnStack() {
    super(MAX_RETURN_STACK_SIZE, Integer.class);
  }
}
