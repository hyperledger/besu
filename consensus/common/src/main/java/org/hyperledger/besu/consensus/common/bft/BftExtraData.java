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
package org.hyperledger.besu.consensus.common.bft;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;

import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class BftExtraData implements ParsedExtraData {
  private final Bytes vanityData;
  private final Collection<SECPSignature> seals;
  private final Collection<Address> validators;
  private final Optional<Vote> vote;
  private final int round;

  public BftExtraData(
      final Bytes vanityData,
      final Collection<SECPSignature> seals,
      final Optional<Vote> vote,
      final int round,
      final Collection<Address> validators) {
    checkNotNull(vanityData);
    checkNotNull(seals);
    checkNotNull(validators);
    this.vanityData = vanityData;
    this.seals = seals;
    this.validators = validators;
    this.vote = vote;
    this.round = round;
  }

  public Bytes getVanityData() {
    return vanityData;
  }

  public Collection<SECPSignature> getSeals() {
    return seals;
  }

  public Collection<Address> getValidators() {
    return validators;
  }

  public Optional<Vote> getVote() {
    return vote;
  }

  public int getRound() {
    return round;
  }
}
