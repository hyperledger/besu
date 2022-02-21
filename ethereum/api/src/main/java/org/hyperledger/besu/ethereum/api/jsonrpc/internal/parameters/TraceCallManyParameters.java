package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TraceCallManyParameters {

    private final CallParameter jsonCallParameter;
    private final TraceTypeParameter traceTypeParameter;

    public TraceCallManyParameters(final CallParameter callParameter, final TraceTypeParameter traceTypeParameter) {
        this.jsonCallParameter = callParameter;
        this.traceTypeParameter = traceTypeParameter;
    }

    public CallParameter getJsonCallParameter() {
        return jsonCallParameter;
    }

    public TraceTypeParameter getTraceTypeParameter() {
        return traceTypeParameter;
    }

    public static List<TraceCallManyParameters> fromStringList(final List input) {
        final ArrayList<TraceCallManyParameters> parameterList = new ArrayList<>();
        input.forEach(p -> parameterList.add(parseParameter((Map)((List)p).get(0), (List<String>)((List)p).get(1))));
        return parameterList;
    }

    private static TraceCallManyParameters parseParameter(final Map callParamMap, final List<String>traceTypeList) {
        final TraceTypeParameter traceTypeParameter = new TraceTypeParameter(traceTypeList);
        final CallParameter callParameter = new CallParameter(
                Address.fromHexString((String) callParamMap.get("from")),
                Address.fromHexString((String) callParamMap.get("to")),
                Long.decode((String) callParamMap.get("gasLimit")),
                Wei.fromHexString((String)callParamMap.get("gasPrice")),
                Optional.ofNullable(Wei.fromHexString((String)callParamMap.get("maxPriorityFeePerGas"))),
                Optional.ofNullable(Wei.fromHexString((String)callParamMap.get("maxFeePerGas"))),
                Wei.fromHexString((String)callParamMap.get("value")),
                Bytes.fromHexString((String)callParamMap.get("payload")));
        return new TraceCallManyParameters(callParameter, traceTypeParameter);
    }
}
