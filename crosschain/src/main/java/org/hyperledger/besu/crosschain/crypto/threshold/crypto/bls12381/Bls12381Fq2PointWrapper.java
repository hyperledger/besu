package tech.pegasys.poc.threshold.crypto.bls12381;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import tech.pegasys.poc.threshold.crypto.BlsCryptoProvider;

import tech.pegasys.poc.threshold.crypto.BlsPoint;
import tech.pegasys.poc.threshold.crypto.altbn128.AltBn128Fq2PointWrapper;

import java.math.BigInteger;

public class Bls12381Fq2PointWrapper implements BlsPoint {
    ECP2 point;

    Bls12381Fq2PointWrapper(org.apache.milagro.amcl.BLS381.ECP2 point) {
        this.point = point;

    }

    // Add a point to this point.
    public BlsPoint add(BlsPoint obj) {
        if (!(obj instanceof Bls12381Fq2PointWrapper)) {
            throw new RuntimeException("incorrect point addition with BLS12 point");
        }
        this.point.add(((Bls12381Fq2PointWrapper) obj).point);
        return new Bls12381Fq2PointWrapper(this.point);
    }

    // Multiple this point by a scalar.
    public BlsPoint scalarMul(BigInteger scalar) {
        org.apache.milagro.amcl.BLS381.BIG scBig = Bls12381Util.BIGFromBigInteger(scalar);
        return new Bls12381Fq2PointWrapper(this.point.mul(scBig));
    }



    // Return true if this point is the point at infinity.
    public boolean isAtInfinity() {
        return this.point.is_infinity();
    }

    public BlsPoint negate() {
        ECP2 p;
        p = new ECP2();
        this.point.copy(p);
        p.neg();
        return new Bls12381Fq2PointWrapper(p);
    }
    private static final int WORD_LEN = 48;
    public static final int STORED_LEN = WORD_LEN+WORD_LEN+WORD_LEN+WORD_LEN;
    public byte[] store() {

        byte[] output = new byte[4*BIG.MODBYTES];
        FP2 x = this.point.getX();

        BIG xr = x.getA();
        xr.tobytearray(output, 0);

        BIG xi = x.getB();
        xi.tobytearray(output, BIG.MODBYTES);

        FP2 y = this.point.getY();

        BIG yr = y.getA();
        yr.tobytearray(output, 2*BIG.MODBYTES);

        BIG yi = y.getB();
        yi.tobytearray(output, 3*BIG.MODBYTES);

        return output;
    }

    public static Bls12381Fq2PointWrapper load(byte[] data) throws Exception {
        if (data.length != STORED_LEN) {
            throw new Error("BLS12Fq2 Point data incorrect length. Should be " + STORED_LEN + ", is " + data.length);
        }

        BIG xr = BIG.frombytearray(data, 0);
        BIG xi = BIG.frombytearray(data, BIG.MODBYTES);
        FP2 x = new FP2(xr, xi);

        BIG yr = BIG.frombytearray(data, 2*BIG.MODBYTES);
        BIG yi = BIG.frombytearray(data, 3*BIG.MODBYTES);
        FP2 y = new FP2(yr, yi);

        ECP2 point = new ECP2(x, y);

        Bls12381Fq2PointWrapper w = new Bls12381Fq2PointWrapper(point);

       return w;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Bls12381Fq2PointWrapper)) {
            return false;
        }

        return  this.point.equals(((Bls12381Fq2PointWrapper) obj).point);
    }

    @Override
    public int hashCode() {
        return this.point.hashCode();
    }

    @Override
    public String toString() {
        return this.point.toString();
    }

}
