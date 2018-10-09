package net.consensys.pantheon.consensus.clique;

import net.consensys.pantheon.crypto.SECP256K1;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.crypto.SECP256K1.Signature;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.util.bytes.BytesValue;

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
