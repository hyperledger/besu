package org.hyperledger.besu.crypto;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressService;
import com.bloxbean.cardano.client.cip.cip8.COSEKey;
import com.bloxbean.cardano.client.cip.cip8.COSESign1;
import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

public class Ed25519 {
  public static final String ADDRESS_LABEL = "address";
  public static final int PUBLIC_KEY_ID = -2;

  public static boolean verify(final byte[] rawCOSESign1, final byte[] rawCOSEKey)
      throws CborRuntimeException, CryptoException, AddressRuntimeException {
    COSESign1 sign1 = COSESign1.deserialize(rawCOSESign1);
    COSEKey key = COSEKey.deserialize(rawCOSEKey);

    byte[] rawAddress =
        sign1.headers()._protected().getAsHeaderMap().otherHeaderAsBytes(ADDRESS_LABEL);
    Address address = new Address(rawAddress);

    byte[] publicKey = key.otherHeaderAsBytes(PUBLIC_KEY_ID);

    EdDSASigningProvider signer = new EdDSASigningProvider();

    boolean signatureVerification =
        signer.verify(sign1.signature(), sign1.signedData().serializeAsBytes(), publicKey);

    boolean addressVerification = AddressService.getInstance().verifyAddress(address, publicKey);

    return signatureVerification && addressVerification;
  }
}
