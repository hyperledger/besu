package org.hyperledger.besu.plugin.services.rlp;

import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.BesuService;

import org.apache.tuweni.bytes.Bytes;

public interface RlpConverterService extends BesuService {

  BlockHeader buildHeaderFromRlp(final Bytes rlp);

  BlockBody buildBodyFromRlp(final Bytes rlp);

  TransactionReceipt buildReceiptFromRlp(final Bytes rlp);

  Bytes buildRlpFromHeader(final BlockHeader blockHeader);

  Bytes buildRlpFromBody(final BlockBody blockBody);

  Bytes buildRlpFromReceipt(final TransactionReceipt receipt);
}
