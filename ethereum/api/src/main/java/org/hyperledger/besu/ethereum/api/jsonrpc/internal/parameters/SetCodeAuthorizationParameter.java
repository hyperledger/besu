package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.CodeDelegation;
import org.hyperledger.besu.crypto.SECPSignature;
import org.apache.tuweni.bytes.Bytes;

import java.math.BigInteger;

public class SetCodeAuthorizationParameter {
    
    private final String chainId;
    private final String address;
    private final String nonce;
    private final String yParity;
    private final String r;
    private final String s;
    
    @JsonCreator
    public SetCodeAuthorizationParameter(
        @JsonProperty("chainId") final String chainId,
        @JsonProperty("address") final String address,
        @JsonProperty("nonce") final String nonce,
        @JsonProperty("yParity") final String yParity,
        @JsonProperty("r") final String r,
        @JsonProperty("s") final String s) {
        this.chainId = chainId;
        this.address = address;
        this.nonce = nonce;
        this.yParity = yParity;
        this.r = r;
        this.s = s;
    }
    
    public CodeDelegation toAuthorization() {
        BigInteger rValue = new BigInteger(r.substring(2), 16);
        BigInteger sValue = new BigInteger(s.substring(2), 16);
        byte yParityByte = (byte) Integer.parseInt(yParity.substring(2), 16);
        
        // Convert yParity to v (27 or 28 for legacy, or 0/1 for EIP-155)
        BigInteger v = BigInteger.valueOf(yParityByte);
        
        SECPSignature signature = SECPSignature.create(rValue, sValue, yParityByte, v);
        
        return new org.hyperledger.besu.ethereum.core.CodeDelegation(
            new BigInteger(chainId.substring(2), 16),
            Address.fromHexString(address),
            Long.decode(nonce),
            signature
        );
    }
    
    // Getters...
}