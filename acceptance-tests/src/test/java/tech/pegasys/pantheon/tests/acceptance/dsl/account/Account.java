/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
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
