package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.ethereum.core.ValidatorExit;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ValidatorExitDecoderTest {

  @Test
  public void shouldDecodeValidatorExit() {
    final ValidatorExit expectedValidatorExit =
        new ValidatorExit(
            Address.fromHexString("0x814FaE9f487206471B6B0D713cD51a2D35980000"),
            BLSPublicKey.fromHexString(
                "0xb10a4a15bf67b328c9b101d09e5c6ee6672978fdad9ef0d9e2ceffaee99223555d8601f0cb3bcc4ce1af9864779a416e"));

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    expectedValidatorExit.writeTo(out);

    final ValidatorExit decodedValidatorExit =
        ValidatorExitDecoder.decode(RLP.input(out.encoded()));

    Assertions.assertThat(decodedValidatorExit).isEqualTo(expectedValidatorExit);
  }
}
