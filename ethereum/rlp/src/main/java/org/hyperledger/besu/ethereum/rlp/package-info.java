/* SPDX-License-Identifier: Apache-2.0 */
/**
 * Recursive Length Prefix (RLP) encoding and decoding.
 *
 * <p>This package provides encoding and decoding of data with the RLP encoding scheme. Encoding is
 * done through writing data to a {@link org.hyperledger.besu.ethereum.rlp.RLPOutput} (for instance
 * {@link org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput}, which then exposes the encoded
 * output as a {@link org.apache.tuweni.bytes.Bytes} through {@link
 * org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput#encoded()}). Decoding is done by wrapping
 * encoded data in a {@link org.hyperledger.besu.ethereum.rlp.RLPInput} (using, for instance, {@link
 * org.hyperledger.besu.ethereum.rlp.RLP#input}) and reading from it.
 */
package org.hyperledger.besu.ethereum.rlp;
