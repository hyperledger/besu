package net.consensys.pantheon.ethereum.core;

import net.consensys.pantheon.crypto.SECP256K1.PublicKey;
import net.consensys.pantheon.crypto.SECP256K1.Signature;

public class Util {

  /**
   * Converts a Signature to an Address, underlying math requires the hash of the data used to
   * create the signature.
   *
   * @param seal the signature from which an address is to be extracted
   * @param dataHash the hash of the data which was signed.
   * @return The Address of the Ethereum node which signed the data defined by the supplied dataHash
   */
  public static Address signatureToAddress(final Signature seal, final Hash dataHash) {
    return PublicKey.recoverFromSignature(dataHash, seal)
        .map(Util::publicKeyToAddress)
        .orElse(null);
  }

  public static Address publicKeyToAddress(final PublicKey publicKey) {
    return Address.extract(Hash.hash(publicKey.getEncodedBytes()));
  }
}
