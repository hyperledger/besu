package net.consensys.pantheon.consensus.clique;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.consensys.pantheon.crypto.SECP256K1.Signature;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.AddressHelpers;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public class CliqueExtraDataTest {

  @Test
  public void encodeAndDecodingDoNotAlterData() {
    final Signature proposerSeal = Signature.create(BigInteger.ONE, BigInteger.ONE, (byte) 0);
    final List<Address> validators =
        Arrays.asList(
            AddressHelpers.ofValue(1), AddressHelpers.ofValue(2), AddressHelpers.ofValue(3));
    final BytesValue vanityData = BytesValue.fromHexString("11223344", 32);

    final CliqueExtraData extraData = new CliqueExtraData(vanityData, proposerSeal, validators);

    final BytesValue serialisedData = extraData.encode();

    final CliqueExtraData decodedExtraData = CliqueExtraData.decode(serialisedData);

    assertThat(decodedExtraData.getValidators()).isEqualTo(validators);
    assertThat(decodedExtraData.getProposerSeal().get()).isEqualTo(proposerSeal);
    assertThat(decodedExtraData.getVanityData()).isEqualTo(vanityData);
  }

  @Test
  public void parseRinkebyGenesisBlockExtraData() {
    // Rinkeby gensis block extra data text found @ rinkeby.io
    final byte[] genesisBlockExtraData =
        Hex.decode(
            "52657370656374206d7920617574686f7269746168207e452e436172746d616e42eb768f2244c8811c63729a21a3569731535f067ffc57839b00206d1ad20c69a1981b489f772031b279182d99e65703f0076e4812653aab85fca0f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

    final BytesValue bufferToInject = BytesValue.wrap(genesisBlockExtraData);

    final CliqueExtraData extraData = CliqueExtraData.decode(bufferToInject);
    assertThat(extraData.getProposerSeal()).isEmpty();
    assertThat(extraData.getValidators().size()).isEqualTo(3);
  }

  @Test
  public void insufficientDataResultsInAnIllegalArgumentException() {
    final BytesValue illegalData =
        BytesValue.wrap(
            new byte[Signature.BYTES_REQUIRED + CliqueExtraData.EXTRA_VANITY_LENGTH - 1]);

    assertThatThrownBy(() -> CliqueExtraData.decode(illegalData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Invalid BytesValue supplied - too short to produce a valid Clique Extra Data object.");
  }

  @Test
  public void sufficientlyLargeButIllegallySizedInputThrowsException() {
    final BytesValue illegalData =
        BytesValue.wrap(
            new byte
                [Signature.BYTES_REQUIRED
                    + CliqueExtraData.EXTRA_VANITY_LENGTH
                    + Address.SIZE
                    - 1]);

    assertThatThrownBy(() -> CliqueExtraData.decode(illegalData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("BytesValue is of invalid size - i.e. contains unused bytes.");
  }
}
