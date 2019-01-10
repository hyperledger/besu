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
package tech.pegasys.pantheon.consensus.ibft.support;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RoundSpecificNodeRoles {

  private final ValidatorPeer proposer;
  private final Collection<ValidatorPeer> peers;
  private final List<ValidatorPeer> nonProposingPeers;

  public RoundSpecificNodeRoles(
      final ValidatorPeer proposer,
      final Collection<ValidatorPeer> peers,
      final List<ValidatorPeer> nonProposingPeers) {
    this.proposer = proposer;
    this.peers = peers;
    this.nonProposingPeers = nonProposingPeers;
  }

  public ValidatorPeer getProposer() {
    return proposer;
  }

  public Collection<ValidatorPeer> getAllPeers() {
    return Collections.unmodifiableCollection(peers);
  }

  public List<ValidatorPeer> getNonProposingPeers() {
    return Collections.unmodifiableList(nonProposingPeers);
  }

  public ValidatorPeer getNonProposingPeer(final int index) {
    return nonProposingPeers.get(index);
  }
}
