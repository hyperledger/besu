package tech.pegasys.poc.threshold.crypto.bls12381;
import org.junit.Test;
import tech.pegasys.poc.threshold.crypto.BlsCryptoProvider;
import tech.pegasys.poc.threshold.crypto.BlsPoint;
import tech.pegasys.poc.threshold.crypto.PointWrapperTest;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class Bls12381PointWrapperTest {

    // Check that add and multiply work correctly.
    @Test
    public void addMul() {

        BigInteger three = BigInteger.valueOf(3);
        BigInteger seven = BigInteger.valueOf(7);
        BigInteger fourteen = BigInteger.valueOf(14);
        BigInteger twentyone = BigInteger.valueOf(21);


        BlsCryptoProvider cryptoProvider = BlsCryptoProvider.getInstance(BlsCryptoProvider.CryptoProviderTypes.LOCAL_BLS12_381, BlsCryptoProvider.DigestAlgorithm.KECCAK256);
        BlsPoint p3 = cryptoProvider.createPointE1(three);
        BlsPoint p7 = cryptoProvider.createPointE1(seven);
        BlsPoint p14 = cryptoProvider.createPointE1(fourteen);
        BlsPoint p21 = cryptoProvider.createPointE1(twentyone);


        BlsPoint p3x7 = p3.scalarMul(seven);
        BlsPoint p7x3 = p7.scalarMul(three);
        p7 = cryptoProvider.createPointE1(seven);
        BlsPoint p21sum = p7.add(p14);

        assertThat(p21.equals(p3x7)).isTrue();
        assertThat(p21.equals(p7x3)).isTrue();
        assertThat(p21.equals(p21sum)).isTrue();

    }


    @Test
    public void loadStore() {
        BigInteger privateKey = BigInteger.TEN;
        BlsCryptoProvider cryptoProvider = BlsCryptoProvider.getInstance(BlsCryptoProvider.CryptoProviderTypes.LOCAL_BLS12_381, BlsCryptoProvider.DigestAlgorithm.KECCAK256);
        BlsPoint point = cryptoProvider.createPointE1(privateKey);

        byte[] data = point.store();
        BlsPoint newPoint = BlsPoint.load(data);

        assertThat(newPoint.equals(point)).isTrue();

    }

}
