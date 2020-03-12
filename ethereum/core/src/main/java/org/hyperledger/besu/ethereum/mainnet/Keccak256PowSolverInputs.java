/*
 * Copyright 2020 Whiteblock Inc.
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

import java.util.Arrays;

import org.apache.tuweni.units.bigints.UInt256;

public class Keccak256PowSolverInputs extends SolverInputs {

  public Keccak256PowSolverInputs(
      final UInt256 target, final byte[] prePowHash, final long blockNumber) {
    super(target, prePowHash, blockNumber);
  }

  @Override
  public String toString() {
    return "Keccak256PowSolverInputs{"
        + "target="
        + target
        + ", prePowHash="
        + Arrays.toString(prePowHash)
        + ", blockNumber="
        + blockNumber
        + '}';
  }
}
