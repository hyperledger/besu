package tech.pegasys.poc.threshold.crypto.bls12381;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import tech.pegasys.poc.threshold.crypto.BlsCryptoProvider;
import tech.pegasys.poc.threshold.crypto.BlsPoint;
import tech.pegasys.poc.threshold.crypto.CryptoProviderTest;

import java.math.BigInteger;

public class Bls12381CryptoProviderTest extends CryptoProviderTest {
    @Test
    public void signVerifyHappyCase() {
    signVerifyHappyCase(BlsCryptoProvider.CryptoProviderTypes.LOCAL_BLS12_381);
}

    @Test
    public void signVerifyBadVerifyData() {
        signVerifyBadVerifyData(BlsCryptoProvider.CryptoProviderTypes.LOCAL_BLS12_381);
    }

    @Test
    public void signVerifyBadPublicKey() {
            signVerifyHappyCase(BlsCryptoProvider.CryptoProviderTypes.LOCAL_BLS12_381);
    }

}
