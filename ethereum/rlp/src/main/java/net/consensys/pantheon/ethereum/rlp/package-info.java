/**
 * Recursive Length Prefix (RLP) encoding and decoding.
 *
 * <p>This package provides encoding and decoding of data with the RLP encoding scheme. Encoding is
 * done through writing data to a {@link net.consensys.pantheon.ethereum.rlp.RLPOutput} (for
 * instance {@link net.consensys.pantheon.ethereum.rlp.BytesValueRLPOutput}, which then exposes the
 * encoded output as a {@link net.consensys.pantheon.util.bytes.BytesValue} through {@link
 * net.consensys.pantheon.ethereum.rlp.BytesValueRLPOutput#encoded()}). Decoding is done by wrapping
 * encoded data in a {@link net.consensys.pantheon.ethereum.rlp.RLPInput} (using, for instance,
 * {@link net.consensys.pantheon.ethereum.rlp.RLP#input}) and reading from it.
 */
package net.consensys.pantheon.ethereum.rlp;
