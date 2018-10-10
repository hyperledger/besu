package net.consensys.pantheon.ethereum.p2p.rlpx.handshake.ecies;

import net.consensys.pantheon.crypto.SECP256K1;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;

public interface InitiatorHandshakeMessage {

  BytesValue encode();

  Bytes32 getNonce();

  SECP256K1.PublicKey getPubKey();

  SECP256K1.PublicKey getEphPubKey();

  Bytes32 getEphPubKeyHash();
}
