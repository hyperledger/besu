/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Objects;
import java.util.Optional;

public class PivotBlockProposal {

  public static final PivotBlockProposal EMPTY_SYNC_STATE = new PivotBlockProposal();
  final ProposalOption option;

  final Optional<Hash> maybeHash;
  final Optional<Long> maybeNumber;

  final Optional<BlockHeader> maybeHeader;

  public PivotBlockProposal(final Hash hash) {
    this.option = ProposalOption.Hash;
    this.maybeHash = Optional.of(hash);
    this.maybeNumber = Optional.empty();
    this.maybeHeader = Optional.empty();
  }

  public PivotBlockProposal(final long number) {
    this.option = ProposalOption.Number;
    this.maybeHash = Optional.empty();
    this.maybeNumber = Optional.of(number);
    this.maybeHeader = Optional.empty();
  }

  public PivotBlockProposal(final BlockHeader header) {
    this.option = ProposalOption.Header;
    this.maybeHash = Optional.of(header.getBlockHash());
    this.maybeNumber = Optional.of(header.getNumber());
    this.maybeHeader = Optional.of(header);
  }

  public PivotBlockProposal() {
    this.option = ProposalOption.None;
    this.maybeNumber = Optional.empty();
    this.maybeHeader = Optional.empty();
    this.maybeHash = Optional.empty();
  }

  public ProposalOption getOption() {
    return option;
  }

  public BlockHeader getHeader() {
    return maybeHeader.orElseThrow();
  }

  public Hash getHash() {
    return maybeHash.orElseThrow();
  }

  public long getNumber() {
    return maybeNumber.orElseThrow();
  }

  public boolean hasHeader() {
    return ProposalOption.Header.equals(option);
  }

  public enum ProposalOption {
    Hash,
    Number,
    Header,
    None
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PivotBlockProposal proposal = (PivotBlockProposal) o;
    return option == proposal.option
        && Objects.equals(maybeHash, proposal.maybeHash)
        && Objects.equals(maybeNumber, proposal.maybeNumber)
        && Objects.equals(maybeHeader, proposal.maybeHeader);
  }

  @Override
  public int hashCode() {
    return Objects.hash(option, maybeHash, maybeNumber, maybeHeader);
  }
}
