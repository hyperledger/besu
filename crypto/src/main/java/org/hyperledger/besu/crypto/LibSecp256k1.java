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
 *
 */
package org.hyperledger.besu.crypto;

import java.nio.ByteBuffer;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import org.apache.logging.log4j.LogManager;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class LibSecp256k1 implements Library {

  /* Flags to pass to secp256k1_context_create */
  public static final int SECP256K1_CONTEXT_VERIFY = 0x0101;
  public static final int SECP256K1_CONTEXT_SIGN = 0x0201;

  /* Flag to pass to secp256k1_ec_pubkey_serialize. */
  public static final int SECP256K1_EC_UNCOMPRESSED = 0x0002;

  static final PointerByReference CONTEXT = createContext();

  private static PointerByReference createContext() {
    try {
      Native.register(LibSecp256k1.class, "secp256k1");
      final PointerByReference context =
          secp256k1_context_create(SECP256K1_CONTEXT_VERIFY | SECP256K1_CONTEXT_SIGN);
      if (secp256k1_context_randomize(
              context, SecureRandomProvider.createSecureRandom().generateSeed(32))
          == 1) {
        // run a test extract to prove the library is built with the recover option
        try {
          final Bytes privKey =
              UInt256.fromHexString(
                      "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4")
                  .toBytes();
          final secp256k1_pubkey pubKey = new secp256k1_pubkey();
          secp256k1_ec_pubkey_create(context, pubKey, privKey.toArrayUnsafe());
          final ByteBuffer serializedKey = ByteBuffer.allocate(65);
          final LongByReference keySize = new LongByReference(serializedKey.limit());

          secp256k1_ec_pubkey_serialize(
              context, serializedKey, keySize, pubKey, SECP256K1_EC_UNCOMPRESSED);
          serializedKey.flip();
          final secp256k1_ecdsa_recoverable_signature signature =
              new secp256k1_ecdsa_recoverable_signature();
          secp256k1_ecdsa_sign_recoverable(
              context, signature, privKey.toArrayUnsafe(), privKey.toArrayUnsafe(), null, null);
          final secp256k1_pubkey newPubKey = new secp256k1_pubkey();
          secp256k1_ecdsa_recover(context, newPubKey, signature, privKey.toArrayUnsafe());

          // we don't care about the answer, we care that no UnsatisfiedLinkErrors are thrown.
          LogManager.getLogger().info("Using native secp256k1");
          return context;
        } catch (final UnsatisfiedLinkError ule) {
          LogManager.getLogger()
              .warn(
                  "Cannot use native secp256k1 because of a missing method (was it built with --enable-module-recover?)",
                  ule);
          return null;
        }
      } else {
        return null;
      }
    } catch (final Throwable t) {
      return null;
    }
  }

  /**
   * A pointer to a function to deterministically generate a nonce
   *
   * <p>Except for test cases, this function should compute some cryptographic hash of the message,
   * the algorithm, the key and the attempt.
   */
  public interface secp256k1_nonce_function extends Callback {

    /**
     * @param nonce32 (output) Pointer to a 32-byte array to be filled by the function.
     * @param msg32 The 32-byte message hash being verified (will not be NULL).
     * @param key32 Pointer to a 32-byte secret key (will not be NULL)
     * @param algo16 Pointer to a 16-byte array describing the signature * algorithm (will be NULL
     *     for ECDSA for compatibility).
     * @param data Arbitrary data pointer that is passed through.
     * @param attempt How many iterations we have tried to find a nonce. This will almost always be
     *     0, but different attempt values are required to result in a different nonce.
     * @return 1 if a nonce was successfully generated. 0 will cause signing to fail.
     */
    int apply(
        Pointer nonce32, Pointer msg32, Pointer key32, Pointer algo16, Pointer data, int attempt);
  }

  /**
   * Opaque data structure that holds a parsed and valid public key.
   *
   * <p>The exact representation of data inside is implementation defined and not guaranteed to be
   * portable between different platforms or versions. It is however guaranteed to be 64 bytes in
   * size, and can be safely copied/moved. If you need to convert to a format suitable for storage,
   * transmission, or comparison, use secp256k1_ec_pubkey_serialize and secp256k1_ec_pubkey_parse.
   */
  @FieldOrder({"data"})
  public static class secp256k1_pubkey extends Structure {
    public byte[] data = new byte[64];
  }

  /**
   * Opaque data structured that holds a parsed ECDSA signature.
   *
   * <p>The exact representation of data inside is implementation defined and not guaranteed to be
   * portable between different platforms or versions. It is however guaranteed to be 64 bytes in
   * size, and can be safely copied/moved. If you need to convert to a format suitable for storage,
   * transmission, or comparison, use the secp256k1_ecdsa_signature_serialize_* and
   * secp256k1_ecdsa_signature_parse_* functions.
   */
  @FieldOrder({"data"})
  public static class secp256k1_ecdsa_signature extends Structure {
    public byte[] data = new byte[64];
  }

  /**
   * Opaque data structured that holds a parsed ECDSA signature, supporting pubkey recovery.
   *
   * <p>The exact representation of data inside is implementation defined and not guaranteed to be
   * portable between different platforms or versions. It is however guaranteed to be 65 bytes in
   * size, and can be safely copied/moved. If you need to convert to a format suitable for storage
   * or transmission, use the secp256k1_ecdsa_signature_serialize_* and
   * secp256k1_ecdsa_signature_parse_* functions.
   *
   * <p>Furthermore, it is guaranteed that identical signatures (including their recoverability)
   * will have identical representation, so they can be memcmp'ed.
   */
  @FieldOrder({"data"})
  public static class secp256k1_ecdsa_recoverable_signature extends Structure {
    public byte[] data = new byte[65];
  }

  /**
   * Create a secp256k1 context object (in dynamically allocated memory).
   *
   * <p>This function uses malloc to allocate memory. It is guaranteed that malloc is called at most
   * once for every call of this function. If you need to avoid dynamic memory allocation entirely,
   * see the functions in secp256k1_preallocated.h.
   *
   * <p>See also secp256k1_context_randomize.
   *
   * @param flags which parts of the context to initialize.
   * @return a newly created context object.
   */
  public static native PointerByReference secp256k1_context_create(final int flags);

  /**
   * Parse a variable-length public key into the pubkey object.
   *
   * <p>This function supports parsing compressed (33 bytes, header byte 0x02 or 0x03), uncompressed
   * (65 bytes, header byte 0x04), or hybrid (65 bytes, header byte 0x06 or 0x07) format public
   * keys.
   *
   * @return 1 if the public key was fully valid. 0 if the public key could not be parsed or is
   *     invalid.
   * @param ctx a secp256k1 context object.
   * @param pubkey (output) pointer to a pubkey object. If 1 is returned, it is set to a parsed
   *     version of input. If not, its value is undefined.
   * @param input pointer to a serialized public key
   * @param inputlen length of the array pointed to by input
   */
  public static native int secp256k1_ec_pubkey_parse(
      final PointerByReference ctx,
      final secp256k1_pubkey pubkey,
      final byte[] input,
      final long inputlen);

  /**
   * Serialize a pubkey object into a serialized byte sequence.
   *
   * @return 1 always.
   * @param ctx a secp256k1 context object.
   * @param output (output) a pointer to a 65-byte (if compressed==0) or 33-byte (if compressed==1)
   *     byte array to place the serialized key in.
   * @param outputlen (input/output) a pointer to an integer which is initially set to the size of
   *     output, and is overwritten with the written size.
   * @param pubkey a pointer to a secp256k1_pubkey containing an initialized public key.
   * @param flags SECP256K1_EC_COMPRESSED if serialization should be in compressed format, otherwise
   *     SECP256K1_EC_UNCOMPRESSED.
   */
  public static native int secp256k1_ec_pubkey_serialize(
      final PointerByReference ctx,
      final ByteBuffer output,
      final LongByReference outputlen,
      final secp256k1_pubkey pubkey,
      final int flags);

  /**
   * Parse an ECDSA signature in compact (64 bytes) format.
   *
   * <p>The signature must consist of a 32-byte big endian R value, followed by a 32-byte big endian
   * S value. If R or S fall outside of [0..order-1], the encoding is invalid. R and S with value 0
   * are allowed in the encoding.
   *
   * <p>After the call, sig will always be initialized. If parsing failed or R or S are zero, the
   * resulting sig value is guaranteed to fail validation for any message and public key.
   *
   * @return 1 when the signature could be parsed, 0 otherwise.
   * @param ctx a secp256k1 context object.
   * @param sig (output) a pointer to a signature object
   * @param input64 a pointer to the 64-byte array to parse
   */
  public static native int secp256k1_ecdsa_signature_parse_compact(
      final PointerByReference ctx, final secp256k1_ecdsa_signature sig, final byte[] input64);

  /**
   * Verify an ECDSA signature.
   *
   * <p>To avoid accepting malleable signatures, only ECDSA signatures in lower-S form are accepted.
   *
   * <p>If you need to accept ECDSA signatures from sources that do not obey this rule, apply
   * secp256k1_ecdsa_signature_normalize to the signature prior to validation, but be aware that
   * doing so results in malleable signatures.
   *
   * <p>For details, see the comments for that function.
   *
   * @return 1 if it is a correct signature, 0 if it is an incorrect or unparseable signature.
   * @param ctx a secp256k1 context object, initialized for verification.
   * @param sig the signature being verified (cannot be NULL)
   * @param msg32 the 32-byte message hash being verified (cannot be NULL)
   * @param pubkey pointer to an initialized public key to verify with (cannot be NULL)
   */
  public static native int secp256k1_ecdsa_verify(
      final PointerByReference ctx,
      final secp256k1_ecdsa_signature sig,
      final byte[] msg32,
      final secp256k1_pubkey pubkey);

  /**
   * Compute the public key for a secret key.
   *
   * @return 1 if secret was valid, public key stores, 0 if secret was invalid, try again.
   * @param ctx pointer to a context object, initialized for signing (cannot be NULL)
   * @param pubkey (output) pointer to the created public key (cannot be NULL)
   * @param seckey pointer to a 32-byte private key (cannot be NULL)
   */
  public static native int secp256k1_ec_pubkey_create(
      final PointerByReference ctx, final secp256k1_pubkey pubkey, final byte[] seckey);

  /**
   * Updates the context randomization to protect against side-channel leakage. While secp256k1 code
   * is written to be constant-time no matter what secret values are, it's possible that a future
   * compiler may output code which isn't, and also that the CPU may not emit the same radio
   * frequencies or draw the same amount power for all values.
   *
   * <p>This function provides a seed which is combined into the blinding value: that blinding value
   * is added before each multiplication (and removed afterwards) so that it does not affect
   * function results, but shields against attacks which rely on any input-dependent behaviour.
   *
   * <p>This function has currently an effect only on contexts initialized for signing because
   * randomization is currently used only for signing. However, this is not guaranteed and may
   * change in the future. It is safe to call this function on contexts not initialized for signing;
   * then it will have no effect and return 1.
   *
   * <p>You should call this after secp256k1_context_create or secp256k1_context_clone (and
   * secp256k1_context_preallocated_create or secp256k1_context_clone, resp.), and you may call this
   * repeatedly afterwards.
   *
   * @param ctx pointer to a context object (cannot be NULL)
   * @param seed32 pointer to a 32-byte random seed (NULL resets to initial state)
   * @return Returns 1 if randomization successfully updated or nothing to randomize or 0 if an
   *     error occured
   */
  public static native int secp256k1_context_randomize(final PointerByReference ctx, final byte[] seed32);

  /**
   * Parse a compact ECDSA signature (64 bytes + recovery id).
   *
   * @return 1 when the signature could be parsed, 0 otherwise
   * @param ctx a secp256k1 context object
   * @param sig (output) a pointer to a signature object
   * @param input64 a pointer to a 64-byte compact signature
   * @param recid the recovery id (0, 1, 2 or 3)
   */
  public static native int secp256k1_ecdsa_recoverable_signature_parse_compact(
      final PointerByReference ctx,
      final secp256k1_ecdsa_recoverable_signature sig,
      final byte[] input64,
      final int recid);

  /**
   * Serialize an ECDSA signature in compact format (64 bytes + recovery id).
   *
   * @param ctx a secp256k1 context object
   * @param output64 (output) a pointer to a 64-byte array of the compact signature (cannot be NULL)
   * @param recid (output) a pointer to an integer to hold the recovery id (can be NULL).
   * @param sig a pointer to an initialized signature object (cannot be NULL)
   */
  public static native void secp256k1_ecdsa_recoverable_signature_serialize_compact(
      final PointerByReference ctx,
      final ByteBuffer output64,
      final IntByReference recid,
      final secp256k1_ecdsa_recoverable_signature sig);

  /**
   * Create a recoverable ECDSA signature.
   *
   * @return 1 if signature created, 0 if the nonce generation function failed or the private key
   *     was invalid.
   * @param ctx pointer to a context object, initialized for signing (cannot be NULL)
   * @param sig (output) pointer to an array where the signature will be placed (cannot be NULL)
   * @param msg32 the 32-byte message hash being signed (cannot be NULL)
   * @param seckey pointer to a 32-byte secret key (cannot be NULL)
   * @param noncefp pointer to a nonce generation function. If NULL,
   *     secp256k1_nonce_function_default is used
   * @param ndata pointer to arbitrary data used by the nonce generation function (can be NULL)
   */
  public static native int secp256k1_ecdsa_sign_recoverable(
      final PointerByReference ctx,
      final secp256k1_ecdsa_recoverable_signature sig,
      final byte[] msg32,
      final byte[] seckey,
      final secp256k1_nonce_function noncefp,
      final Pointer ndata);

  /**
   * Recover an ECDSA public key from a signature.
   *
   * @return 1 if public key successfully recovered (which guarantees a correct signature), 0
   *     otherwise.
   * @param ctx pointer to a context object, initialized for verification (cannot be NULL)
   * @param pubkey (output) pointer to the recovered public key (cannot be NULL)
   * @param sig pointer to initialized signature that supports pubkey recovery (cannot be NULL)
   * @param msg32 the 32-byte message hash assumed to be signed (cannot be NULL)
   */
  public static native int secp256k1_ecdsa_recover(
      final PointerByReference ctx,
      final secp256k1_pubkey pubkey,
      final secp256k1_ecdsa_recoverable_signature sig,
      final byte[] msg32);
}
