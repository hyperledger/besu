/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.crypto;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.SECP256K1_EC_UNCOMPRESSED;

import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recoverable_signature;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_signature;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_pubkey;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;

/*
 * Adapted from the BitcoinJ ECKey (Apache 2 License) implementation:
 * https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/core/ECKey.java
 *
 *
 * Adapted from the web3j (Apache 2 License) implementations:
 * https://github.com/web3j/web3j/crypto/src/main/java/org/web3j/crypto/*.java
 */
public class SECP256K1 implements SignatureAlgorithm {

  private static final Logger LOG = LogManager.getLogger();

  private boolean useNative = true;

  public static final String CURVE_NAME = "secp256k1";
  public static final String PROVIDER = "BC";

  private final ECDomainParameters curve;
  private final BigInteger halfCurveOrder;

  private final KeyPairGenerator keyPairGenerator;
  private final BigInteger curveOrder;

  public SECP256K1() {
    Security.addProvider(new BouncyCastleProvider());

    final X9ECParameters params = SECNamedCurves.getByName(CURVE_NAME);
    curve = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
    curveOrder = curve.getN();
    halfCurveOrder = curveOrder.shiftRight(1);
    try {
      keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    final ECGenParameterSpec ecGenParameterSpec = new ECGenParameterSpec(CURVE_NAME);
    try {
      keyPairGenerator.initialize(ecGenParameterSpec, SecureRandomProvider.createSecureRandom());
    } catch (final InvalidAlgorithmParameterException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void enableNative() {
    useNative = LibSecp256k1.CONTEXT != null;
    LOG.info(useNative ? "Using native secp256k1" : "Native secp256k1 requested but not available");
  }

  @Override
  public SECPSignature sign(final Bytes32 dataHash, final KeyPair keyPair) {
    if (useNative) {
      return signNative(dataHash, keyPair);
    } else {
      return signDefault(dataHash, keyPair);
    }
  }

  /**
   * Verifies the given ECDSA signature against the message bytes using the public key bytes.
   *
   * <p>When using native ECDSA verification, data must be 32 bytes, and no element may be larger
   * than 520 bytes.
   *
   * @param data Hash of the data to verify.
   * @param signature ASN.1 encoded signature.
   * @param pub The public key bytes to use.
   * @return True if the verification is successful.
   */
  @Override
  public boolean verify(final Bytes data, final SECPSignature signature, final SECPPublicKey pub) {
    if (useNative) {
      return verifyNative(data, signature, pub);
    } else {
      return verifyDefault(data, signature, pub);
    }
  }

  /** Decompress a compressed public key (x co-ord and low-bit of y-coord). */
  private ECPoint decompressKey(final BigInteger xBN, final boolean yBit) {
    final X9IntegerConverter x9 = new X9IntegerConverter();
    final byte[] compEnc = x9.integerToBytes(xBN, 1 + x9.getByteLength(curve.getCurve()));
    compEnc[0] = (byte) (yBit ? 0x03 : 0x02);
    // TODO: Find a better way to handle an invalid point compression here.
    // Currently ECCurve#decodePoint throws an IllegalArgumentException.
    return curve.getCurve().decodePoint(compEnc);
  }

  /**
   * Given the components of a signature and a selector value, recover and return the public key
   * that generated the signature according to the algorithm in SEC1v2 section 4.1.6.
   *
   * <p>If this method returns null it means recovery was not possible and recId should be iterated.
   *
   * <p>Given the above two points, a correct usage of this method is inside a for loop from 0 to 3,
   * and if the output is null OR a key that is not the one you expect, you try again with the next
   * recId.
   *
   * @param recId Which possible key to recover.
   * @param r The R component of the signature.
   * @param s The S component of the signature.
   * @param dataHash Hash of the data that was signed.
   * @return An ECKey containing only the public part, or null if recovery wasn't possible.
   */
  private BigInteger recoverFromSignature(
      final int recId, final BigInteger r, final BigInteger s, final Bytes32 dataHash) {
    assert (recId >= 0);
    assert (r.signum() >= 0);
    assert (s.signum() >= 0);
    assert (dataHash != null);

    // 1.0 For j from 0 to h (h == recId here and the loop is outside this function)
    // 1.1 Let x = r + jn
    final BigInteger n = curve.getN(); // Curve order.
    final BigInteger i = BigInteger.valueOf((long) recId / 2);
    final BigInteger x = r.add(i.multiply(n));
    // 1.2. Convert the integer x to an octet string X of length mlen using the conversion
    // routine specified in Section 2.3.7, where mlen = ⌈(log2 p)/8⌉ or mlen = ⌈m/8⌉.
    // 1.3. Convert the octet string (16 set binary digits)||X to an elliptic curve point R
    // using the conversion routine specified in Section 2.3.4. If this conversion
    // routine outputs "invalid", then do another iteration of Step 1.
    //
    // More concisely, what these points mean is to use X as a compressed public key.
    final BigInteger prime = SecP256K1Curve.q;
    if (x.compareTo(prime) >= 0) {
      // Cannot have point co-ordinates larger than this as everything takes place modulo Q.
      return null;
    }
    // Compressed keys require you to know an extra bit of data about the y-coord as there are
    // two possibilities. So it's encoded in the recId.
    final ECPoint R = decompressKey(x, (recId & 1) == 1);
    // 1.4. If nR != point at infinity, then do another iteration of Step 1 (callers
    // responsibility).
    if (!R.multiply(n).isInfinity()) {
      return null;
    }
    // 1.5. Compute e from M using Steps 2 and 3 of ECDSA signature verification.
    final BigInteger e = dataHash.toUnsignedBigInteger();
    // 1.6. For k from 1 to 2 do the following. (loop is outside this function via
    // iterating recId)
    // 1.6.1. Compute a candidate public key as:
    // Q = mi(r) * (sR - eG)
    //
    // Where mi(x) is the modular multiplicative inverse. We transform this into the following:
    // Q = (mi(r) * s ** R) + (mi(r) * -e ** G)
    // Where -e is the modular additive inverse of e, that is z such that z + e = 0 (mod n).
    // In the above equation ** is point multiplication and + is point addition (the EC group
    // operator).
    //
    // We can find the additive inverse by subtracting e from zero then taking the mod. For
    // example the additive inverse of 3 modulo 11 is 8 because 3 + 8 mod 11 = 0, and
    // -3 mod 11 = 8.
    final BigInteger eInv = BigInteger.ZERO.subtract(e).mod(n);
    final BigInteger rInv = r.modInverse(n);
    final BigInteger srInv = rInv.multiply(s).mod(n);
    final BigInteger eInvrInv = rInv.multiply(eInv).mod(n);
    final ECPoint q = ECAlgorithms.sumOfTwoMultiplies(curve.getG(), eInvrInv, R, srInv);

    if (q.isInfinity()) {
      return null;
    }

    final byte[] qBytes = q.getEncoded(false);
    // We remove the prefix
    return new BigInteger(1, Arrays.copyOfRange(qBytes, 1, qBytes.length));
  }

  private SECPSignature signDefault(final Bytes32 dataHash, final KeyPair keyPair) {
    final ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));

    final ECPrivateKeyParameters privKey =
        new ECPrivateKeyParameters(
            keyPair.getPrivateKey().getEncodedBytes().toUnsignedBigInteger(), curve);
    signer.init(true, privKey);

    final BigInteger[] components = signer.generateSignature(dataHash.toArrayUnsafe());

    return normaliseSignature(components[0], components[1], keyPair.getPublicKey(), dataHash);
  }

  @Override
  public SECPSignature normaliseSignature(
      final BigInteger nativeR,
      final BigInteger nativeS,
      final SECPPublicKey publicKey,
      final Bytes32 dataHash) {

    BigInteger s = nativeS;
    // Automatically adjust the S component to be less than or equal to half the curve
    // order, if necessary. This is required because for every signature (r,s) the signature
    // (r, -s (mod N)) is a valid signature of the same message. However, we dislike the
    // ability to modify the bits of a Bitcoin transaction after it's been signed, as that
    // violates various assumed invariants. Thus in future only one of those forms will be
    // considered legal and the other will be banned.
    if (s.compareTo(halfCurveOrder) > 0) {
      // The order of the curve is the number of valid points that exist on that curve.
      // If S is in the upper half of the number of valid points, then bring it back to
      // the lower half. Otherwise, imagine that
      // N = 10
      // s = 8, so (-8 % 10 == 2) thus both (r, 8) and (r, 2) are valid solutions.
      // 10 - 8 == 2, giving us always the latter solution, which is canonical.
      s = curve.getN().subtract(s);
    }

    // Now we have to work backwards to figure out the recId needed to recover the signature.
    int recId = -1;
    final BigInteger publicKeyBI = publicKey.getEncodedBytes().toUnsignedBigInteger();
    for (int i = 0; i < 4; i++) {
      final BigInteger k = recoverFromSignature(i, nativeR, s, dataHash);
      if (k != null && k.equals(publicKeyBI)) {
        recId = i;
        break;
      }
    }
    if (recId == -1) {
      throw new RuntimeException(
          "Could not construct a recoverable key. This should never happen.");
    }

    return new SECPSignature(nativeR, s, (byte) recId);
  }

  private boolean verifyDefault(
      final Bytes data, final SECPSignature signature, final SECPPublicKey pub) {
    final ECDSASigner signer = new ECDSASigner();
    final Bytes toDecode = Bytes.wrap(Bytes.of((byte) 4), pub.getEncodedBytes());
    final ECPublicKeyParameters params =
        new ECPublicKeyParameters(curve.getCurve().decodePoint(toDecode.toArrayUnsafe()), curve);
    signer.init(false, params);
    try {
      return signer.verifySignature(data.toArrayUnsafe(), signature.getR(), signature.getS());
    } catch (final NullPointerException e) {
      // Bouncy Castle contains a bug that can cause NPEs given specially crafted signatures. Those
      // signatures
      // are inherently invalid/attack sigs so we just fail them here rather than crash the thread.
      return false;
    }
  }

  /**
   * Verifies the given ECDSA signature using the public key bytes against the message bytes,
   * previously passed through a preprocessor function, which is normally a hashing function.
   *
   * @param data The data to verify.
   * @param signature ASN.1 encoded signature.
   * @param pub The public key bytes to use.
   * @param preprocessor The function to apply to the data before verifying the signature, normally
   *     a hashing function.
   * @return True if the verification is successful.
   */
  @Override
  public boolean verify(
      final Bytes data,
      final SECPSignature signature,
      final SECPPublicKey pub,
      final UnaryOperator<Bytes> preprocessor) {
    checkArgument(preprocessor != null, "preprocessor must not be null");
    return verify(preprocessor.apply(data), signature, pub);
  }

  /**
   * Calculates an ECDH key agreement between the private and the public key.
   *
   * @param privKey The private key.
   * @param theirPubKey The public key.
   * @return The agreed secret.
   */
  @Override
  public Bytes32 calculateECDHKeyAgreement(
      final SECPPrivateKey privKey, final SECPPublicKey theirPubKey) {
    checkArgument(privKey != null, "missing private key");
    checkArgument(theirPubKey != null, "missing remote public key");

    final ECPrivateKeyParameters privKeyP = new ECPrivateKeyParameters(privKey.getD(), curve);
    final ECPublicKeyParameters pubKeyP =
        new ECPublicKeyParameters(theirPubKey.asEcPoint(curve), curve);

    final ECDHBasicAgreement agreement = new ECDHBasicAgreement();
    agreement.init(privKeyP);
    final BigInteger agreed = agreement.calculateAgreement(pubKeyP);

    return UInt256.valueOf(agreed).toBytes();
  }

  @Override
  public SECPPrivateKey createPrivateKey(final BigInteger key) {
    return SECPPrivateKey.create(key, ALGORITHM);
  }

  @Override
  public SECPPrivateKey createPrivateKey(final Bytes32 key) {
    return SECPPrivateKey.create(key, ALGORITHM);
  }

  @Override
  public SECPPublicKey createPublicKey(final SECPPrivateKey privateKey) {
    return SECPPublicKey.create(privateKey, curve, ALGORITHM);
  }

  @Override
  public SECPPublicKey createPublicKey(final BigInteger key) {
    return SECPPublicKey.create(key, ALGORITHM);
  }

  @Override
  public SECPPublicKey createPublicKey(final Bytes encoded) {
    return SECPPublicKey.create(encoded, ALGORITHM);
  }

  @Override
  public Optional<SECPPublicKey> recoverPublicKeyFromSignature(
      final Bytes32 dataHash, final SECPSignature signature) {
    if (useNative) {
      return recoverFromSignatureNative(dataHash, signature);
    } else {
      final BigInteger publicKeyBI =
          recoverFromSignature(signature.getRecId(), signature.getR(), signature.getS(), dataHash);
      return Optional.of(SECPPublicKey.create(publicKeyBI, ALGORITHM));
    }
  }

  @Override
  public ECPoint publicKeyAsEcPoint(final SECPPublicKey publicKey) {
    return publicKey.asEcPoint(curve);
  }

  @Override
  public KeyPair createKeyPair(final SECPPrivateKey privateKey) {
    return KeyPair.create(privateKey, curve, ALGORITHM);
  }

  @Override
  public KeyPair generateKeyPair() {
    return KeyPair.generate(keyPairGenerator, ALGORITHM);
  }

  @Override
  public SECPSignature createSignature(final BigInteger r, final BigInteger s, final byte recId) {
    return SECPSignature.create(r, s, recId, curveOrder);
  }

  @Override
  public SECPSignature decodeSignature(final Bytes bytes) {
    return SECPSignature.decode(bytes, curveOrder);
  }

  @Override
  public BigInteger getHalfCurveOrder() {
    return halfCurveOrder;
  }

  @Override
  public String getProvider() {
    return PROVIDER;
  }

  @Override
  public String getCurveName() {
    return CURVE_NAME;
  }

  private SECPSignature signNative(final Bytes32 dataHash, final KeyPair keyPair) {
    final LibSecp256k1.secp256k1_ecdsa_recoverable_signature signature =
        new secp256k1_ecdsa_recoverable_signature();
    // sign in internal form
    if (LibSecp256k1.secp256k1_ecdsa_sign_recoverable(
            LibSecp256k1.CONTEXT,
            signature,
            dataHash.toArrayUnsafe(),
            keyPair.getPrivateKey().getEncoded(),
            null,
            null)
        == 0) {
      throw new RuntimeException(
          "Could not natively sign. Private Key is invalid or default nonce generation failed.");
    }

    // encode to compact form
    final ByteBuffer compactSig = ByteBuffer.allocate(64);
    final IntByReference recId = new IntByReference(0);
    LibSecp256k1.secp256k1_ecdsa_recoverable_signature_serialize_compact(
        LibSecp256k1.CONTEXT, compactSig, recId, signature);
    compactSig.flip();
    final byte[] sig = compactSig.array();

    // wrap in signature object
    final Bytes32 r = Bytes32.wrap(sig, 0);
    final Bytes32 s = Bytes32.wrap(sig, 32);
    return SECPSignature.create(
        r.toUnsignedBigInteger(), s.toUnsignedBigInteger(), (byte) recId.getValue(), curveOrder);
  }

  private boolean verifyNative(
      final Bytes data, final SECPSignature signature, final SECPPublicKey pub) {

    // translate signature
    final LibSecp256k1.secp256k1_ecdsa_signature _signature = new secp256k1_ecdsa_signature();
    if (LibSecp256k1.secp256k1_ecdsa_signature_parse_compact(
            LibSecp256k1.CONTEXT, _signature, signature.encodedBytes().toArrayUnsafe())
        == 0) {
      throw new IllegalArgumentException("Could not parse signature");
    }

    // translate key
    final LibSecp256k1.secp256k1_pubkey _pub = new secp256k1_pubkey();
    final Bytes encodedPubKey = Bytes.concatenate(Bytes.of(0x04), pub.getEncodedBytes());
    if (LibSecp256k1.secp256k1_ec_pubkey_parse(
            LibSecp256k1.CONTEXT, _pub, encodedPubKey.toArrayUnsafe(), encodedPubKey.size())
        == 0) {
      throw new IllegalArgumentException("Could not parse public key");
    }

    return LibSecp256k1.secp256k1_ecdsa_verify(
            LibSecp256k1.CONTEXT, _signature, data.toArrayUnsafe(), _pub)
        != 0;
  }

  private Optional<SECPPublicKey> recoverFromSignatureNative(
      final Bytes32 dataHash, final SECPSignature signature) {

    // parse the sig
    final LibSecp256k1.secp256k1_ecdsa_recoverable_signature parsedSignature =
        new LibSecp256k1.secp256k1_ecdsa_recoverable_signature();
    final Bytes encodedSig = signature.encodedBytes();
    if (LibSecp256k1.secp256k1_ecdsa_recoverable_signature_parse_compact(
            LibSecp256k1.CONTEXT,
            parsedSignature,
            encodedSig.slice(0, 64).toArrayUnsafe(),
            encodedSig.get(64))
        == 0) {
      throw new IllegalArgumentException("Could not parse signature");
    }

    // recover the key
    final LibSecp256k1.secp256k1_pubkey newPubKey = new LibSecp256k1.secp256k1_pubkey();
    if (LibSecp256k1.secp256k1_ecdsa_recover(
            LibSecp256k1.CONTEXT, newPubKey, parsedSignature, dataHash.toArrayUnsafe())
        == 0) {
      throw new IllegalArgumentException("Could not recover public key");
    }

    // parse the key
    final ByteBuffer recoveredKey = ByteBuffer.allocate(65);
    final LongByReference keySize = new LongByReference(recoveredKey.limit());
    LibSecp256k1.secp256k1_ec_pubkey_serialize(
        LibSecp256k1.CONTEXT, recoveredKey, keySize, newPubKey, SECP256K1_EC_UNCOMPRESSED);

    return Optional.of(
        SECPPublicKey.create(Bytes.wrapByteBuffer(recoveredKey).slice(1), ALGORITHM));
  }
}
