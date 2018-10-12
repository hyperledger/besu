/**
 * Recursive Length Prefix (RLP) encoding and decoding.
 *
 * <p>This package provides encoding and decoding of data with the RLP encoding scheme. Encoding is
 * done through writing data to a {@link tech.pegasys.pantheon.ethereum.rlp.RLPOutput} (for instance
 * {@link tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput}, which then exposes the encoded
 * output as a {@link tech.pegasys.pantheon.util.bytes.BytesValue} through {@link
 * tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput#encoded()}). Decoding is done by wrapping
 * encoded data in a {@link tech.pegasys.pantheon.ethereum.rlp.RLPInput} (using, for instance,
 * {@link tech.pegasys.pantheon.ethereum.rlp.RLP#input}) and reading from it.
 */
package tech.pegasys.pantheon.ethereum.rlp;
