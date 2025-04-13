/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.services

import com.google.common.base.Preconditions
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.PluginJsonRpcMethod
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse
import org.hyperledger.besu.plugin.services.RpcEndpointService
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest
import org.hyperledger.besu.plugin.services.rpc.PluginRpcResponse
import org.hyperledger.besu.plugin.services.rpc.RpcResponseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Function
import java.util.stream.Collectors

/** The RPC endpoint service implementation.  */
class RpcEndpointServiceImpl
/** Default Constructor.  */
    : RpcEndpointService {
    private val rpcMethods: MutableMap<String, Function<PluginRpcRequest, *>> = HashMap()
    private var inProcessRpcMethods: Map<String, JsonRpcMethod>? = null

    /**
     * Init the service
     *
     * @param inProcessRpcMethods set of RPC methods that can be called
     */
    fun init(inProcessRpcMethods: Map<String, JsonRpcMethod>?) {
        this.inProcessRpcMethods = inProcessRpcMethods
    }

    override fun <T> registerRPCEndpoint(
        namespace: String,
        functionName: String,
        function: Function<PluginRpcRequest, T>
    ) {
        Preconditions.checkArgument(namespace.matches("\\p{Alnum}+".toRegex()), "Namespace must be only alpha numeric")
        Preconditions.checkArgument(
            functionName.matches("\\p{Alnum}+".toRegex()),
            "Function Name must be only alpha numeric"
        )
        Preconditions.checkNotNull(function)

        rpcMethods[namespace + "_" + functionName] = function
    }

    override fun call(methodName: String, params: Array<Any>): PluginRpcResponse {
        Preconditions.checkNotNull(
            inProcessRpcMethods,
            "Service not initialized yet, this method must be called after plugin 'beforeExternalServices' call completes"
        )

        LOG.atTrace()
            .setMessage("Calling method:{} with params:{}")
            .addArgument(methodName)
            .addArgument { params.contentToString() }
            .log()

        val method = inProcessRpcMethods!![methodName]
            ?: throw NoSuchElementException("Unknown or not enabled method: $methodName")

        val requestContext =
            JsonRpcRequestContext(JsonRpcRequest("2.0", methodName, params))
        val response = method.response(requestContext)
        return object : PluginRpcResponse {
            override fun getResult(): Any {
                return when (response.type) {
                    RpcResponseType.NONE, RpcResponseType.UNAUTHORIZED -> null
                    RpcResponseType.SUCCESS -> (response as JsonRpcSuccessResponse).result
                    RpcResponseType.ERROR -> (response as JsonRpcErrorResponse).error
                }!!
            }

            override fun getType(): RpcResponseType {
                return response.type
            }
        }
    }

    /**
     * Gets plugin methods.
     *
     * @param namespaces the namespaces collection
     * @return the Json Rpc Methods from plugins
     */
    fun getPluginMethods(
        namespaces: Collection<String>
    ): Map<String, JsonRpcMethod?> {
        return rpcMethods.entries.stream()
            .filter { entry: Map.Entry<String, Function<PluginRpcRequest, *>> ->
                namespaces.stream()
                    .anyMatch { namespace: String ->
                        entry
                            .key
                            .uppercase()
                            .startsWith(namespace.uppercase())
                    }
            }
            .map { entry: Map.Entry<String, Function<PluginRpcRequest, *>> ->
                PluginJsonRpcMethod(
                    entry.key,
                    entry.value
                )
            }
            .collect(
                Collectors.toMap(
                    Function { obj: PluginJsonRpcMethod -> obj.name },
                    Function { e: PluginJsonRpcMethod? -> e })
            )
    }

    /**
     * Checks if RPC methods belongs to a namespace
     *
     * @param namespace the namespace to check against
     * @return true if any of the RPC method starts with given namespace, false otherwise.
     */
    fun hasNamespace(namespace: String): Boolean {
        return rpcMethods.keys.stream()
            .anyMatch { key: String -> key.uppercase().startsWith(namespace.uppercase()) }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(RpcEndpointServiceImpl::class.java)
    }
}
