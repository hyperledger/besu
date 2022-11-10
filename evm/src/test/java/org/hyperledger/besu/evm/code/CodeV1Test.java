package org.hyperledger.besu.evm.code;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class CodeV1Test {

  @Test
  void calculatesJumpDestMap() {
    String codeHex = "0xEF000101000F006001600055600D5660026000555B00";
    final EOFLayout layout = EOFLayout.parseEOF(Bytes.fromHexString(codeHex));

    long[] jumpDest = OpcodesV1.validateAndCalculateJumpDests(layout.getSections()[1]);

    assertThat(jumpDest).containsExactly(0x2000);
  }
}
