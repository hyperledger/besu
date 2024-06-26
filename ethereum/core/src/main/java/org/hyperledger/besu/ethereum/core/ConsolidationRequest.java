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
  private final BLSPublicKey sourcePublicKey;
  private final BLSPublicKey targetPublicKey;

  public ConsolidationRequest(
      final Address sourceAddress,
      final BLSPublicKey sourcePublicKey,
      final BLSPublicKey targetPublicKey) {
    this.sourceAddress = sourceAddress;
    this.sourcePublicKey = sourcePublicKey;
    this.targetPublicKey = targetPublicKey;
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
  public PublicKey getSourcePublicKey() {
    return sourcePublicKey;
  }

  @Override
  public PublicKey getTargetPublicKey() {
    return targetPublicKey;
  }

  @Override
  public String toString() {
    return "ConsolidationRequest{"
        + "sourceAddress="
        + sourceAddress
        + " sourcePublicKey="
        + sourcePublicKey
        + " targetPublicKey="
        + targetPublicKey
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
        && Objects.equals(sourcePublicKey, that.sourcePublicKey)
        && Objects.equals(targetPublicKey, that.targetPublicKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceAddress, sourcePublicKey, targetPublicKey);
  }
}
