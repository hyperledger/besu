package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;

class PayloadWithExtra {
  /** The Payload identifier. */
  final PayloadIdentifier payloadIdentifier;
  /** The Block with receipts. */
  final BlockWithReceipts blockWithReceipts;

  /**
   * Instantiates a new Payload.
   *
   * @param payloadIdentifier the payload identifier
   * @param blockWithReceipts the block with receipts
   */
  PayloadWithExtra(
      final PayloadIdentifier payloadIdentifier, final BlockWithReceipts blockWithReceipts) {
    this.payloadIdentifier = payloadIdentifier;
    this.blockWithReceipts = blockWithReceipts;
  }
}
