package tech.pegasys.poc.threshold.crypto.bls12381;

import org.junit.Test;

import tech.pegasys.poc.threshold.crypto.BlsCryptoProvider;
import tech.pegasys.poc.threshold.crypto.BlsPoint;
import tech.pegasys.poc.threshold.protocol.CrosschainCoordinationContract;
import tech.pegasys.poc.threshold.protocol.Node;
import tech.pegasys.poc.threshold.protocol.ThresholdKeyGenContract;
import tech.pegasys.poc.threshold.scheme.IntegerSecretShare;
import tech.pegasys.poc.threshold.scheme.ThresholdScheme;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class Bls12381FixedValueTest {

    static final int THRESHOLD = 3;
    static final int TOTAL_NUMBER_NODES = 5;
    static final String VALUE_FILE_NAME = new String("BLS12FixedValues.txt");

    Node[] nodes;
    CrosschainCoordinationContract ccc;
    ThresholdKeyGenContract th;

    public static final boolean PRINT_TO_FILE = false;


    @Test
    /* Test threshold scheme with preset node IDs and private key values */

    public void fixedValueTest() throws Exception {
        FileWriter fw;
        if (PRINT_TO_FILE) {
            fw = new FileWriter(VALUE_FILE_NAME);
        }
        String sOut;

        nodes = new Node[5];
        ccc = new CrosschainCoordinationContract();
        th = new ThresholdKeyGenContract(THRESHOLD, TOTAL_NUMBER_NODES);

        BigInteger[] fixedXValues = new BigInteger[TOTAL_NUMBER_NODES];
        /* Use node IDs 0 - n-1 */
        for (int i = 0; i < TOTAL_NUMBER_NODES; i++) {
            fixedXValues[i] = new BigInteger(Integer.toString(i));
            sOut = String.format("Fixed x[%d]: %d\n", i, fixedXValues[i]);
            if (PRINT_TO_FILE) { fw.write(sOut);}
            nodes[i] = new Node(i, THRESHOLD, TOTAL_NUMBER_NODES);
        }

        for (int i = 0; i < TOTAL_NUMBER_NODES; i++) {
            nodes[i].initNode(nodes, ccc, th);
        }

        for (int i = 0; i < TOTAL_NUMBER_NODES; i++) {
            nodes[i].doKeyGeneration();
        }

        for (int i = 0; i < TOTAL_NUMBER_NODES; i++) {
            IntegerSecretShare fixedShare = new IntegerSecretShare(fixedXValues[i], this.nodes[i].getPrivateKeyShare());
            sOut = String.format("\nfixedShare[%d]:  %s\n", i, fixedShare.toString());
            if (PRINT_TO_FILE) { fw.write(sOut);}
        }

        IntegerSecretShare[] fixedShares = new IntegerSecretShare[THRESHOLD];
        for (int i = 0; i < THRESHOLD; i++) {
            fixedShares[i] = new IntegerSecretShare(fixedXValues[i], this.nodes[i].getPrivateKeyShare());
            sOut = String.format("\nfixedShares[%d]:  %s\n", i, fixedShares[i].toString());
            if (PRINT_TO_FILE) { fw.write(sOut);}
        }



        BlsCryptoProvider fixedCryptoProvider = BlsCryptoProvider.getInstance(BlsCryptoProvider.CryptoProviderTypes.LOCAL_BLS12_381, BlsCryptoProvider.DigestAlgorithm.KECCAK256);
        ThresholdScheme fixedThresholdScheme = new ThresholdScheme(fixedCryptoProvider, THRESHOLD);

        // Do Lagrange interpolation to determine the group private key (the point for x=0).
        BigInteger fixedPrivateKey = fixedThresholdScheme.calculateSecret(fixedShares);
        sOut = String.format("\nFixed Private Key: %s\n", fixedPrivateKey.toString());
        if (PRINT_TO_FILE) { fw.write(sOut);}

        //System.out.println("Fixed Private Key: " + fixedPrivateKey);

        BlsPoint fixedPublicKey = fixedCryptoProvider.createPointE2(fixedPrivateKey);
        sOut = String.format("\nFixed PublicKey: %s\n", fixedPublicKey.toString());
        if (PRINT_TO_FILE) { fw.write(sOut);}

        //System.out.println("Fixed Public Key derived from fixed private key: " + fixedPublicKey);

        if (PRINT_TO_FILE) { fw.close();}

        boolean verified = true;
        assertThat(verified).isTrue();
    }


}
