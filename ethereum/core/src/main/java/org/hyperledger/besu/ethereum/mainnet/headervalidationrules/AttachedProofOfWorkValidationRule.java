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
