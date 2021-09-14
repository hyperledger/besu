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

package org.hyperledger.besu.ethereum.core.contract;

import org.hyperledger.besu.ethereum.core.Hash;

import org.apache.tuweni.bytes.Bytes;

/** key for use by the CodeCache */
public class CodeHash {

  private final Hash codeHash;
  private final Bytes contract;

  public CodeHash(final Bytes contract) {
    this.contract = contract;
    this.codeHash = Hash.hash(contract);
  }

  public Bytes getContract() {
    return contract;
  }

  @Override
  public int hashCode() {
    return codeHash.toBigInteger().hashCode();
  }

  @Override
  public boolean equals(final Object o) {

    if (o == null || getClass() != o.getClass()) return false;
    CodeHash that = (CodeHash) o;
    if (this.contract == that.contract) return true;
    return this.contract.compareTo(that.contract) == 0;
  }
}
