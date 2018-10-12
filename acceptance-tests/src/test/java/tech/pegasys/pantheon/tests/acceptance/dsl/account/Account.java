package tech.pegasys.pantheon.tests.acceptance.dsl.account;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.PrivateKey;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.util.bytes.Bytes32;

import java.math.BigInteger;

import org.web3j.crypto.Credentials;

public class Account {

  private final String name;
  private final KeyPair keyPair;
  private long nonce = 0;

  private Account(final String name, final KeyPair keyPair) {
    this.name = name;
    this.keyPair = keyPair;
  }

  public static Account create(final String name) {
    return new Account(name, KeyPair.generate());
  }

  public static Account fromPrivateKey(final String name, final String privateKey) {
    return new Account(name, KeyPair.create(PrivateKey.create(Bytes32.fromHexString(privateKey))));
  }

  public Credentials web3jCredentials() {
    return Credentials.create(
        keyPair.getPrivateKey().toString(), keyPair.getPublicKey().toString());
  }

  public String getAddress() {
    return Address.extract(Hash.hash(keyPair.getPublicKey().getEncodedBytes())).toString();
  }

  public BigInteger getNextNonce() {
    return BigInteger.valueOf(nonce++);
  }

  public void setNextNonce(final long nonce) {
    this.nonce = nonce;
  }

  public String getName() {
    return name;
  }
}
