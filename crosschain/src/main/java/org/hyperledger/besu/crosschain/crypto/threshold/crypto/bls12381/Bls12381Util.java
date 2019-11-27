package tech.pegasys.poc.threshold.crypto.bls12381;

import org.apache.milagro.amcl.*;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.DBIG;

import java.math.BigInteger;


public class Bls12381Util {

    /* Convert a java BigInteger to a milagro BIG */
    public static org.apache.milagro.amcl.BLS381.BIG BIGFromBigInteger(BigInteger bi)
    {

        byte[] biBytes = bi.toByteArray();
        byte[] bigBytes = new byte[BIG.MODBYTES];
        if (biBytes.length >= BIG.MODBYTES)
            System.arraycopy(biBytes, 0, bigBytes, 0, BIG.MODBYTES);
        else
            System.arraycopy(biBytes, 0, bigBytes, BIG.MODBYTES - biBytes.length, biBytes.length);

      org.apache.milagro.amcl.BLS381.BIG b = org.apache.milagro.amcl.BLS381.BIG.fromBytes(bigBytes);
      return(b);
    }

    /* Convert milagro BIG to a java BigInteger */
    public static BigInteger BigIntegerFromBIG(org.apache.milagro.amcl.BLS381.BIG big)
    {
        byte[] bigBytes = new byte[BIG.MODBYTES];

        big.tobytearray(bigBytes, 0);
        BigInteger b = new BigInteger(bigBytes);

        return(b);
    }

    /* Convert a java BigInteger to a milagro DBIG */
    public static org.apache.milagro.amcl.BLS381.DBIG DBIGFromBigInteger(BigInteger bi)
    {
        BIG b = BIGFromBigInteger(bi);
        DBIG d = new DBIG(b);

        return(d);
    }


}
