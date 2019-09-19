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
package org.hyperledger.besu.crypto.crosschain.threshold;

import org.hyperledger.besu.crypto.crosschain.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crypto.crosschain.threshold.protocol.Node;

import java.io.FileWriter;
import java.math.BigInteger;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;

import com.google.common.base.Charsets;

// This is the main class is used to generate keys for use in the Subordinate View PoC.
public class CrosschainPocGenerateKeys extends CrosschainCheck {

  private void storeStuff(final BlsPoint otherNetworksPublicKey, final int sidechainId)
      throws Exception {
    BigInteger[] xValues = this.th.getAllNodeIds();
    BlsPoint pubKey = nodes[0].getPublicKey();
    byte[] pubKeyBytes = pubKey.store();
    String pubKeyBase64 = Base64.getEncoder().encodeToString(pubKeyBytes);
    byte[] otherPubKeyBytes = otherNetworksPublicKey.store();
    String otherPubKeyBase64 = Base64.getEncoder().encodeToString(otherPubKeyBytes);

    for (Node node : this.nodes) {
      BigInteger privateKeyShare = node.getPrivateKeyShare();
      BigInteger nodeId = node.getNodeId();
      int nodeNumber = node.getNodeNumber();

      // I don't know if we are going to need all of this, but let's put it into the file as a
      // starting point!
      Properties props = new Properties();
      props.setProperty("NodeNumber", Integer.toString(nodeNumber));
      props.setProperty("Threshold", Integer.toString(THRESHOLD));
      props.setProperty("NumNodes", Integer.toString(TOTAL_NUMBER_NODES));

      props.setProperty("NodePrivateKeyShare", privateKeyShare.toString());
      props.setProperty("NodeId", nodeId.toString());
      props.setProperty("SidechainPubKey", pubKeyBase64);
      props.setProperty("OtherSidechainPubKey", otherPubKeyBase64);

      for (int i = 0; i < xValues.length; i++) {
        props.setProperty("Xvalue" + i, xValues[i].toString());
      }

      String name = "sidechain" + sidechainId + "_node" + nodeNumber;
      String filename = name + ".properties";
      System.out.println("Saving file: " + "build/" + filename);
      FileWriter fw = new FileWriter("build/" + filename, Charsets.UTF_8);
      props.store(fw, name);
    }
  }

  public static void main(final String[] args) throws Exception {

    // Make stdout and stderr one stream. Have them both non-buffered.
    // What this means is that if an error or exception stack trace is thrown,
    // it will be shown in the context of the other output.
    System.setOut(System.err);

    System.out.println("Test: Start");
    System.out.println(" Date: " + (new Date().toString()));
    System.out.println();

    if (THRESHOLD > TOTAL_NUMBER_NODES) {
      throw new Exception("Configuration Error: THRESHOLD > TOTAL_NUMBER_NODES !");
    }

    CrosschainPocGenerateKeys network1 = new CrosschainPocGenerateKeys();
    network1.initNodes();
    network1.thresholdKeyGeneration();
    network1.checkPublicKey();
    BlsPoint network1PubKey = network1.nodes[0].getPublicKey();

    CrosschainPocGenerateKeys network2 = new CrosschainPocGenerateKeys();
    network2.initNodes();
    network2.thresholdKeyGeneration();
    network2.checkPublicKey();
    BlsPoint network2PubKey = network2.nodes[0].getPublicKey();

    network1.storeStuff(network2PubKey, 1);
    network2.storeStuff(network1PubKey, 2);

    System.out.println();
    System.out.println(" Date: " + (new Date().toString()));
    System.out.println("Test: End");
  }
}
