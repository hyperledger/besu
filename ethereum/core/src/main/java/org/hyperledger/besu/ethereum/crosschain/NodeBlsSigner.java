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
package org.hyperledger.besu.ethereum.crosschain;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.crypto.PRNGSecureRandom;
import org.hyperledger.besu.crypto.crosschain.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crypto.crosschain.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crypto.crosschain.threshold.scheme.BlsPointSecretShare;
import org.hyperledger.besu.crypto.crosschain.threshold.scheme.ThresholdScheme;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.io.FileInputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/**
 * SIDECHAINS CROSSCHAIN
 *
 * Holds the keys and other values needed to sign and verify information.
 */
public class NodeBlsSigner {
    private static final Logger LOG = LogManager.getLogger();


    private SecureRandom prng = new PRNGSecureRandom();

    private ThresholdScheme thresholdScheme;


    // TODO: import the cryptography from the BLS project.
    BlsPoint sidechainPublicKey;
    BlsPoint otherSidechainPublicKey;
    private BigInteger nodePrivateKeyShare;
    private BigInteger nodeId;
    int threshold;
    int numNodes;

    List<BigInteger> xValues;

    final BlsCryptoProvider cryptoProvider;


    public NodeBlsSigner(final int sidechainId, final int nodeNumber) throws Exception {
        loadConfigFromPropertiesFile(sidechainId, nodeNumber);
        this.cryptoProvider = BlsCryptoProvider.getInstance(BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128, BlsCryptoProvider.DigestAlgorithm.KECCAK256);

        this.thresholdScheme = new ThresholdScheme(this.cryptoProvider, this.threshold, this.prng);
    }





    // TODO the loading and storing of properties should be done in the one file.
    void loadConfigFromPropertiesFile(final int sidechainId, final int nodeNumber) throws Exception {
        String name = "sidechain" + sidechainId + "_node" + nodeNumber;
        String filename = name + ".properties";
        LOG.info("Loading node simulator config: " + "~/crosschain_data/" + filename);
        String userHome = System.getProperty("user.home");
        FileInputStream fis = new FileInputStream(userHome + "/crosschain_data/" + filename);
        Properties props = new Properties();
        props.load(fis);

        // I don't know if we are going to need all of this, but let's put it into the file as a starting point!
//        String nodeNumberStr = props.getProperty("NodeNumber");

        String thresholdStr = props.getProperty("Threshold");
        this.threshold = Integer.valueOf(thresholdStr);

        String numNodesStr = props.getProperty("NumNodes");
        this.numNodes = Integer.valueOf(numNodesStr);


        String nodePrivateKeyShareStr = props.getProperty("NodePrivateKeyShare");
        this.nodePrivateKeyShare = new BigInteger(nodePrivateKeyShareStr);

        String nodeIdStr = props.getProperty("NodeId");
        this.nodeId = new BigInteger(nodeIdStr);

        String sidechainPubKeyStr = props.getProperty("SidechainPubKey");
        byte[] sidechainPubKeyBytes = Base64.getDecoder().decode(sidechainPubKeyStr);
        this.sidechainPublicKey = BlsPoint.load(sidechainPubKeyBytes);

        // TODO need to work out what is there and read it in
        // TODO currently not used. Should be used to verify results from other sidechains.

//        String otherSidechainPubKeyStr = props.getProperty("OtherSidechainPubKey");
//        byte[] otherSidechainPubKeyBytes = Base64.getDecoder().decode(otherSidechainPubKeyStr);
//        this.otherSidechainPublicKey = BlsPoint.load(otherSidechainPubKeyBytes);

//        String otherSidechainPubKeyStr = props.getProperty("OtherSidechainPubKey");
//        byte[] otherSidechainPubKeyBytes = Base64.getDecoder().decode(otherSidechainPubKeyStr);
//        this.otherSidechainPublicKey = BlsPoint.load(otherSidechainPubKeyBytes);

        this.xValues = new ArrayList<BigInteger>();
        for (int i = 0; i < numNodes; i++) {
            String temp = props.getProperty("Xvalue" + i);
            this.xValues.add(new BigInteger(temp));
        }
    }

    public BlsPointSecretShare sign(final SubordinateViewResult toBeSigned) {
        BytesValue bytesToBeSigned = toBeSigned.getEncoded();
        BlsPoint partialSig = this.cryptoProvider.sign(this.nodePrivateKeyShare, bytesToBeSigned.extractArray());
        return new BlsPointSecretShare(this.nodeId, partialSig);
    }


    public BlsPoint combineSignatureShares(
            final BlsPointSecretShare localPartialSignature, final ArrayList<BlsPointSecretShare> otherNodePartialSignatures,
            final SubordinateViewResult toBeSigned) {
        if (otherNodePartialSignatures.size() <= this.threshold-1) {
            // TODO
            throw new Error("Not enough partial signatures to create a signature");
        }

        // TODO check that all partial signatures have unique x values.


        // Just grab the first threshold partial signatures.
        BlsPointSecretShare[] shares = new BlsPointSecretShare[this.threshold];
        shares[0] = localPartialSignature;
        for (int i=1; i < this.threshold; i++) {
            shares[i] = otherNodePartialSignatures.get(i);
        }

        // Do Lagrange interpolation to determine the group public key (the point for x=0).
        BlsPoint signature = this.thresholdScheme.calculateSecret(shares);

        // Verify the signature.
        boolean verified = this.cryptoProvider.verify(this.sidechainPublicKey, toBeSigned.getEncoded().extractArray(), signature);

        if (!verified) {
            // TODO try out other combinations of validators. Should determine which is the malicious validator
            throw new Error("Signature could not be verified");
        }
        return signature;

    }

}



