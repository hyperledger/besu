/*
 * Copyright Hyperledger Besu Contributors
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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWHasher;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.Optional;

/**
 * An attached proof of work validation rule that wraps the detached version of the same. Suitable
 * for use in block validator stacks supporting the merge.
 */
public class AttachedProofOfWorkValidationRule implements AttachedBlockHeaderValidationRule {

  private final ProofOfWorkValidationRule detachedRule;

  public AttachedProofOfWorkValidationRule(
      final EpochCalculator epochCalculator,
      final PoWHasher hasher,
      final Optional<FeeMarket> feeMarket) {
    this.detachedRule = new ProofOfWorkValidationRule(epochCalculator, hasher, feeMarket);
  }

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext protocolContext) {
    return detachedRule.validate(header, parent);
  }
}
