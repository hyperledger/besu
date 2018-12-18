description: Pantheon JSON-RPC API reference
<!--- END of page meta data -->

# JSON-RPC API overview

The Pantheon JSON-RPC API uses the [JSON](http://json.org/) (RFC 4627) data format, which can represent objects and data fields as collections of name/value pairs, in a relatively readable, hierarchical form. Values have specific data types such as QUANTITIES (decimal integers, hexadecimal numbers, strings) and UNFORMATTED DATA (byte arrays, account addresses, hashes, and bytecode arrays).

RPC is the remote procedure call protocol (RFC 1831), which is stateless and transport agnostic in that the concepts can be used within the same process, over sockets, over HTTP, or in many various message passing environments.

* [Using the Pantheon JSON-RPC API](Using-JSON-RPC-API.md)
* [JSON-RPC API Methods](JSON-RPC-API-Methods.md)
* [JSON-RPC API Objects](JSON-RPC-API-Objects.md)