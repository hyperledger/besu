package org.hyperledger.besu.consensus.qbft.validator;
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
import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.consensus.common.validator.VoteProvider;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class NoOpTransactionVoteProvider implements VoteProvider {

  @Override
  public Optional<ValidatorVote> getVoteAfterBlock(
      final BlockHeader header, final Address localAddress) {
    return Optional.empty();
  }

  @Override
  public void authVote(final Address address) {}

  @Override
  public void dropVote(final Address address) {}

  @Override
  public void discardVote(final Address address) {}

  @Override
  public Map<Address, VoteType> getProposals() {
    return Collections.emptyMap();
  }
}
