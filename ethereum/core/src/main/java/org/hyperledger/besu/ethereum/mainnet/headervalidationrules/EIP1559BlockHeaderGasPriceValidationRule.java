package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.fees.EIP1559Manager;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EIP1559BlockHeaderGasPriceValidationRule<C> implements AttachedBlockHeaderValidationRule<C> {
  private final Logger LOG = LogManager.getLogger(CalculatedDifficultyValidationRule.class);
  private final EIP1559Manager eip1559 = new EIP1559Manager();

  @Override
  public boolean validate(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext<C> protocolContext) {
    final long baseFee = eip1559.computeBaseFee(parent.getBaseFee(), header.getGasUsed());
    if (baseFee != header.getBaseFee()) {
      LOG.trace(
          "Invalid block header: basefee {} does not equal expected basefee {}",
          header.getBaseFee(),
          baseFee);
    }
    return baseFee == header.getBaseFee()
        && eip1559.isValidBaseFee(parent.getBaseFee(), header.getBaseFee());
  }
}
