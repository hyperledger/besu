/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.crosschain.crypto.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crosschain.core.keys.BlsThresholdCryptoSystem;
import org.hyperledger.besu.crosschain.core.keys.CrosschainKeyManager;
import org.hyperledger.besu.crosschain.core.keys.generation.SimulatedThresholdKeyGenContractWrapper;
import org.hyperledger.besu.crosschain.core.keys.generation.ThresholdKeyGenContractInterface;
import org.hyperledger.besu.crosschain.core.keys.generation.ThresholdKeyGeneration;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crosschain.crypto.threshold.scheme.IntegerSecretShare;
import org.hyperledger.besu.crosschain.crypto.threshold.scheme.ThresholdScheme;
import org.hyperledger.besu.crosschain.p2p.SimulatedCrosschainDevP2P;
import org.hyperledger.besu.crosschain.p2p.SimulatedOtherNode;
import org.hyperledger.besu.crypto.SECP256K1;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Iterator;

import org.junit.Test;

// This is the main class for running through a simple scenario.
public abstract class AbstractThresholdKeyGenerationTest {
  ThresholdKeyGeneration keyGeneration;
  Collection<SimulatedOtherNode> otherNodes;
  int threshold;

  public void generateKeys(final int numberOfNodes, final int threshold) {
    this.threshold = threshold;

    ThresholdKeyGenContractInterface keyGen = new SimulatedThresholdKeyGenContractWrapper();
    SimulatedCrosschainDevP2P p2pI = new SimulatedCrosschainDevP2P(keyGen, numberOfNodes - 1);
    CrosschainKeyManager keyManager = new CrosschainKeyManager(keyGen, p2pI);
    BigInteger blockchainId = BigInteger.TEN;
    keyManager.init(blockchainId, SECP256K1.KeyPair.generate());
    long keyVersionNumber =
        keyManager.generateNewKeys(threshold, BlsThresholdCryptoSystem.ALT_BN_128_WITH_KECCAK256);

    this.keyGeneration = keyManager.activeKeyGenerations.get(keyVersionNumber);

    this.otherNodes = p2pI.otherNodes.values();
  }

  @Test
  public void allPubKeysMatch() {
    BlsPoint pubKey = keyGeneration.getPublicKey();
    for (SimulatedOtherNode other : otherNodes) {
      assertThat(pubKey).isEqualTo(other.getPublicKey());
    }
  }

  @Test
  public void checkPublicKeyMatchesGroupPrivateKeyTest() throws Exception {
    // Calculate the group private key.
    // In a real situation, this private key is never combined.
    // TODO: This just checks one combination of shares. If one combination works, they probably all
    // work. It would, however, be good to check.
    IntegerSecretShare[] shares = new IntegerSecretShare[this.threshold];
    shares[0] =
        new IntegerSecretShare(
            this.keyGeneration.getMyNodeAddress(), this.keyGeneration.getPrivateKeyShare());

    Iterator<SimulatedOtherNode> iter = this.otherNodes.iterator();
    for (int i = 0; i < this.threshold - 1; i++) {
      SimulatedOtherNode otherNode = iter.next();
      shares[i + 1] =
          new IntegerSecretShare(otherNode.getMyNodeAddress(), otherNode.getPrivateKeyShare());
    }

    BlsCryptoProvider cryptoProvider =
        BlsCryptoProvider.getInstance(
            BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128,
            BlsCryptoProvider.DigestAlgorithm.KECCAK256);
    ThresholdScheme thresholdScheme = new ThresholdScheme(cryptoProvider, this.threshold);

    // Do Lagrange interpolation to determine the group private key (the point for x=0).
    BigInteger privateKey = thresholdScheme.calculateSecret(shares);
    //    System.out.println("Private Key: " + privateKey);

    BlsPoint shouldBePublicKey = cryptoProvider.createPointE2(privateKey);
    //    System.out.println("Public Key derived from private key: " + shouldBePublicKey);

    assertThat(this.keyGeneration.getPublicKey()).isEqualTo(shouldBePublicKey);
  }
}
