package tech.pegasys.pantheon.consensus.clique;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;

public class TestHelpers {

  public static BlockHeader createCliqueSignedBlockHeader(
      final BlockHeaderTestFixture blockHeaderBuilder,
      final KeyPair signer,
      final List<Address> validators) {

    final CliqueExtraData unsignedExtraData =
        new CliqueExtraData(BytesValue.wrap(new byte[32]), null, validators);
    blockHeaderBuilder.extraData(unsignedExtraData.encode());

    final Hash signingHash =
        CliqueBlockHashing.calculateDataHashForProposerSeal(
            blockHeaderBuilder.buildHeader(), unsignedExtraData);

    final Signature proposerSignature = SECP256K1.sign(signingHash, signer);

    final CliqueExtraData signedExtraData =
        new CliqueExtraData(
            unsignedExtraData.getVanityData(),
            proposerSignature,
            unsignedExtraData.getValidators());

    blockHeaderBuilder.extraData(signedExtraData.encode());

    return blockHeaderBuilder.buildHeader();
  }
}
