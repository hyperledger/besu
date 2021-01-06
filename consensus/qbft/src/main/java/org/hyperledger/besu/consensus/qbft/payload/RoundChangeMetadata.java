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
package org.hyperledger.besu.consensus.qbft.payload;

import org.hyperledger.besu.consensus.common.bft.payload.SignedData;

import java.util.Collection;
import java.util.StringJoiner;

public class RoundChangeMetadata {

  private final Collection<SignedData<RoundChangePayload>> roundChangePayloads;
  private final Collection<SignedData<PreparePayload>> prepares;

  public RoundChangeMetadata(
      final Collection<SignedData<RoundChangePayload>> roundChangePayloads,
      final Collection<SignedData<PreparePayload>> prepares) {
    this.roundChangePayloads = roundChangePayloads;
    this.prepares = prepares;
  }

  public Collection<SignedData<PreparePayload>> getPrepares() {
    return prepares;
  }

  public Collection<SignedData<RoundChangePayload>> getRoundChangePayloads() {
    return roundChangePayloads;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", RoundChangeMetadata.class.getSimpleName() + "[", "]")
        .add("roundChangePayloads=" + roundChangePayloads)
        .toString();
  }
}
