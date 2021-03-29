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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.ethereum.core.Address;

import java.util.Collection;

public class BftContextBuilder {

  public static BftContext setupContextWithValidators(final Collection<Address> validators) {
    final BftContext bftContext = mock(BftContext.class, withSettings().lenient());
    final VoteTallyCache mockCache = mock(VoteTallyCache.class, withSettings().lenient());
    final VoteTally mockVoteTally = mock(VoteTally.class, withSettings().lenient());
    final BftBlockInterface mockBftBlockInterface =
        mock(BftBlockInterface.class, withSettings().lenient());
    when(bftContext.getVoteTallyCache()).thenReturn(mockCache);
    when(mockCache.getVoteTallyAfterBlock(any())).thenReturn(mockVoteTally);
    when(mockVoteTally.getValidators()).thenReturn(validators);
    when(bftContext.getVoteProposer()).thenReturn(new VoteProposer());
    when(bftContext.getBlockInterface()).thenReturn(mockBftBlockInterface);
    return bftContext;
  }

  public static BftContext setupContextWithBftExtraData(
      final Collection<Address> validators, final BftExtraData bftExtraData) {
    final BftContext bftContext = mock(BftContext.class, withSettings().lenient());
    final VoteTallyCache mockCache = mock(VoteTallyCache.class, withSettings().lenient());
    final VoteTally mockVoteTally = mock(VoteTally.class, withSettings().lenient());
    final BftBlockInterface mockBftBlockInterface =
        mock(BftBlockInterface.class, withSettings().lenient());
    when(bftContext.getVoteTallyCache()).thenReturn(mockCache);
    when(mockCache.getVoteTallyAfterBlock(any())).thenReturn(mockVoteTally);
    when(mockVoteTally.getValidators()).thenReturn(validators);
    when(bftContext.getVoteProposer()).thenReturn(new VoteProposer());
    when(bftContext.getBlockInterface()).thenReturn(mockBftBlockInterface);
    when(mockBftBlockInterface.getExtraData(any())).thenReturn(bftExtraData);
    return bftContext;
  }

  public static BftContext setupContextWithBftExtraDataEncoder(
      final Collection<Address> validators, final BftExtraDataCodec bftExtraDataCodec) {
    final BftContext bftContext = mock(BftContext.class, withSettings().lenient());
    final VoteTallyCache mockCache = mock(VoteTallyCache.class, withSettings().lenient());
    final VoteTally mockVoteTally = mock(VoteTally.class, withSettings().lenient());
    when(bftContext.getVoteTallyCache()).thenReturn(mockCache);
    when(mockCache.getVoteTallyAfterBlock(any())).thenReturn(mockVoteTally);
    when(mockVoteTally.getValidators()).thenReturn(validators);
    when(bftContext.getVoteProposer()).thenReturn(new VoteProposer());
    when(bftContext.getBlockInterface()).thenReturn(new BftBlockInterface(bftExtraDataCodec));

    return bftContext;
  }
}
