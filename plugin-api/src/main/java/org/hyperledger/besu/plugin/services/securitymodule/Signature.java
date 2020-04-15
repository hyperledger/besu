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
package org.hyperledger.besu.plugin.services.securitymodule;

import java.math.BigInteger;

public final class Signature {

  /**
   * The recovery id to reconstruct the public key used to create the signature.
   *
   * <p>The recId is an index from 0 to 3 which indicates which of the 4 possible keys is the
   * correct one. Because the key recovery operation yields multiple potential keys, the correct key
   * must either be stored alongside the signature, or you must be willing to try each recId in turn
   * until you find one that outputs the key you are expecting.
   */
  private final byte recoveryId;

  private final BigInteger r;
  private final BigInteger s;

  public Signature(final BigInteger r, final BigInteger s, final byte recoveryId) {
    this.r = r;
    this.s = s;
    this.recoveryId = recoveryId;
  }

  public byte getRecoveryId() {
    return recoveryId;
  }

  public BigInteger getR() {
    return r;
  }

  public BigInteger getS() {
    return s;
  }
}
