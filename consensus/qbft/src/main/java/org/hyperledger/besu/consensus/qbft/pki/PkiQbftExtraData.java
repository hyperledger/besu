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

package org.hyperledger.besu.consensus.qbft.pki;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Address;

import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class PkiQbftExtraData extends BftExtraData {

  private final Bytes cms;

  public PkiQbftExtraData(
      final Bytes vanityData,
      final Collection<SECPSignature> seals,
      final Optional<Vote> vote,
      final int round,
      final Collection<Address> validators,
      final Bytes cms) {
    super(vanityData, seals, vote, round, validators);
    this.cms = cms;
  }

  public PkiQbftExtraData(final BftExtraData bftExtraData, final Bytes cms) {
    this(
        bftExtraData.getVanityData(),
        bftExtraData.getSeals(),
        bftExtraData.getVote(),
        bftExtraData.getRound(),
        bftExtraData.getValidators(),
        cms);
  }

  public Bytes getCms() {
    return cms;
  }
}
