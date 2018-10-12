package tech.pegasys.pantheon.ethereum.p2p.rlpx.handshake.ecies;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public interface InitiatorHandshakeMessage {

  BytesValue encode();

  Bytes32 getNonce();

  SECP256K1.PublicKey getPubKey();

  SECP256K1.PublicKey getEphPubKey();

  Bytes32 getEphPubKeyHash();
}
