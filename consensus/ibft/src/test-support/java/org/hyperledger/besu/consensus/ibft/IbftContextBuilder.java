/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.consensus.ibft;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.ethereum.core.Address;

import java.util.Collection;

public class IbftContextBuilder {

  public static IbftContext setupContextWithValidators(final Collection<Address> validators) {
    final IbftContext ibftContext = mock(IbftContext.class, withSettings().lenient());
    final VoteTallyCache mockCache = mock(VoteTallyCache.class, withSettings().lenient());
    final VoteTally mockVoteTally = mock(VoteTally.class, withSettings().lenient());
    when(ibftContext.getVoteTallyCache()).thenReturn(mockCache);
    when(mockCache.getVoteTallyAfterBlock(any())).thenReturn(mockVoteTally);
    when(mockVoteTally.getValidators()).thenReturn(validators);
    when(ibftContext.getVoteProposer()).thenReturn(new VoteProposer());

    return ibftContext;
  }
}
