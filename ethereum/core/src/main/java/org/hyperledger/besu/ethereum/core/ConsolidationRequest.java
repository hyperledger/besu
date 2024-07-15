/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.PublicKey;
import org.hyperledger.besu.datatypes.RequestType;

import java.util.Objects;

public class ConsolidationRequest extends Request
    implements org.hyperledger.besu.plugin.data.ConsolidationRequest {

  private final Address sourceAddress;
  private final BLSPublicKey sourcePubkey;
  private final BLSPublicKey targetPubkey;

  public ConsolidationRequest(
      final Address sourceAddress,
      final BLSPublicKey sourcePubkey,
      final BLSPublicKey targetPubkey) {
    this.sourceAddress = sourceAddress;
    this.sourcePubkey = sourcePubkey;
    this.targetPubkey = targetPubkey;
  }

  @Override
  public RequestType getType() {
    return RequestType.CONSOLIDATION;
  }

  @Override
  public Address getSourceAddress() {
    return sourceAddress;
  }

  @Override
  public PublicKey getSourcePubkey() {
    return sourcePubkey;
  }

  @Override
  public PublicKey getTargetPubkey() {
    return targetPubkey;
  }

  @Override
  public String toString() {
    return "ConsolidationRequest{"
        + "sourceAddress="
        + sourceAddress
        + " sourcePubkey="
        + sourcePubkey
        + " targetPubkey="
        + targetPubkey
        + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ConsolidationRequest that = (ConsolidationRequest) o;
    return Objects.equals(sourceAddress, that.sourceAddress)
        && Objects.equals(sourcePubkey, that.sourcePubkey)
        && Objects.equals(targetPubkey, that.targetPubkey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceAddress, sourcePubkey, targetPubkey);
  }
}
