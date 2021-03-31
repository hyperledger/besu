package org.hyperledger.besu.ethereum.api.util;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.*;
import org.hyperledger.besu.ethereum.core.encoding.TransactionEncoder;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class DomainObjectDecodeUtilsTest {

  static final BlockDataGenerator gen = new BlockDataGenerator();
  private static final SECPSignature signature =
      SignatureAlgorithmFactory.getInstance()
          .createSignature(BigInteger.ONE, BigInteger.TEN, (byte) 1);
  private static final Address sender =
      Address.fromHexString("0x0000000000000000000000000000000000000003");

  private static final Transaction accessListTxn =
      Transaction.builder()
          .accessList(List.of(new AccessListEntry(gen.address(), List.of(gen.bytes32()))))
          .nonce(1)
          .gasPrice(Wei.of(12))
          .gasLimit(43)
          .payload(Bytes.EMPTY)
          .value(Wei.ZERO)
          .signature(signature)
          .sender(sender)
          .guessType()
          .build();

  @Test
  public void testAccessListRLPSerDes() {
    final BytesValueRLPOutput encoded = new BytesValueRLPOutput();
    TransactionEncoder.encodeForWire(accessListTxn, encoded);
    Transaction decoded = DomainObjectDecodeUtils.decodeRawTransaction(encoded.toString());
    Assertions.assertThat(decoded.getAccessList().isPresent());
    Assertions.assertThat(decoded.getAccessList().map(l -> l.size() == 1));
  }

  @Test
  public void testAccessList2718OpaqueSerDes() {
    final Bytes encoded = TransactionEncoder.encodeOpaqueBytes(accessListTxn);
    Transaction decoded = DomainObjectDecodeUtils.decodeRawTransaction(encoded.toString());
    Assertions.assertThat(decoded.getAccessList().isPresent());
    Assertions.assertThat(decoded.getAccessList().map(l -> l.size() == 1));
  }
}
