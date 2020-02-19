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

import java.util.Arrays;

import org.apache.tuweni.units.bigints.UInt256;

public class EthHashSolverInputs {
  private final UInt256 target;
  private final byte[] prePowHash;
  private final long blockNumber;

  public EthHashSolverInputs(
      final UInt256 target, final byte[] prePowHash, final long blockNumber) {
    this.target = target;
    this.prePowHash = prePowHash;
    this.blockNumber = blockNumber;
  }

  public UInt256 getTarget() {
    return target;
  }

  public byte[] getPrePowHash() {
    return prePowHash;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  @Override
  public String toString() {
    return "EthHashSolverInputs{"
        + "target="
        + target
        + ", prePowHash="
        + Arrays.toString(prePowHash)
        + ", blockNumber="
        + blockNumber
        + '}';
  }
}
