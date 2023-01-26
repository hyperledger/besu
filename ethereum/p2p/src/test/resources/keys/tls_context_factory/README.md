Following instructions were used to set up various certificates and keystores.

The certificates hierarchy 
~~~
                 root.ca.besu.com
                        | 
                 inter.ca.besu.com
                        |      
compa.ca.besu.com                compb.ca.besu.com
       |                               |
avocado.compa.besu.com        banana.compb.besu.com
apricot.compa.besu.com        blueberry.compb.besu.com 
~~~

CompanyA clients
Client Avocado is valid. (backed by PKCS11/nssdb as well)
Client Apricot is in certificate revoke list and hence is invalid.

CompanyB clients
Client Banana is valid.
Client BlueBerry is in certificate revoke list and hence is invalid.

Company420 has different root and inter CA and hence its clients are not trusted and invalid.
Client BadApple is not trusted.



## Lib NSS Setup Instructions:
Linux: `sudo apt install libnss3-tools`
Mac OS: 
1. `brew install nss`
2. Create symlink for libnss3 and lonsoftokn3 in your JDK's lib (or update LD_LIBRARY_PATH globally)
~~~
ln -s /opt/homebrew/lib/libnss3.dylib <jdk_path>/lib/libnss3.dylib
ln -s /opt/homebrew/lib/libsoftokn3.dylib <jdk_path>/lib/libsoftokn3.dylib
~~~

## Root CA, InterCA, CompA CA, CompB CA
All the CA keystores will be generated in `ca_certs` directory. 

Generate Root CA (validity 100 years)
~~~
export ROOT_CA_KS=root_ca.p12
export INTER_CA_KS=inter_ca.p12
export COMPA_CA_KS=compa_ca.p12
export COMPB_CA_KS=compb_ca.p12
~~~
~~~
keytool -genkeypair -alias root_ca -dname "CN=root.ca.besu.com" -ext bc:c -keyalg RSA -keysize 4096 \
-sigalg SHA256WithRSA -validity 36500 \
-storepass test123 \
-keystore $ROOT_CA_KS

keytool -genkeypair -alias inter_ca -dname "CN=inter.ca.besu.com" \
-ext bc:c=ca:true,pathlen:1 -ext ku:c=dS,kCS,cRLs \
-keyalg RSA -keysize 4096 -sigalg SHA256WithRSA -validity 36500 \
-storepass test123 \
-keystore $INTER_CA_KS

keytool -genkeypair -alias compa_ca -dname "CN=compa.ca.besu.com" \
-ext bc:c=ca:true,pathlen:0 -ext ku:c=dS,kCS,cRLs \
-keyalg RSA -keysize 4096 -sigalg SHA256WithRSA -validity 36500 \
-storepass test123 \
-keystore $COMPA_CA_KS

keytool -genkeypair -alias compb_ca -dname "CN=compb.ca.besu.com" \
-ext bc:c=ca:true,pathlen:0 -ext ku:c=dS,kCS,cRLs \
-keyalg RSA -keysize 4096 -sigalg SHA256WithRSA -validity 36500 \
-storepass test123 \
-keystore $COMPB_CA_KS
~~~

CSR, Signing and re-import
~~~
keytool -storepass test123 -keystore $ROOT_CA_KS -alias root_ca -exportcert -rfc > root_ca.pem

keytool -storepass test123 -keystore $INTER_CA_KS -certreq -alias inter_ca \
| keytool -storepass test123 -keystore $ROOT_CA_KS -gencert -alias root_ca \
-ext bc:c=ca:true,pathlen:1 -ext ku:c=dS,kCS,cRLs -rfc > inter_ca.pem

cat root_ca.pem >> inter_ca.pem

keytool -keystore $INTER_CA_KS -importcert -alias inter_ca \
-storepass test123 -noprompt -file ./inter_ca.pem

keytool -storepass test123 -keystore $COMPA_CA_KS -certreq -alias compa_ca \
| keytool -storepass test123 -keystore $INTER_CA_KS -gencert -alias inter_ca \
-ext bc:c=ca:true,pathlen:0 -ext ku:c=dS,kCS,cRLs -rfc > compa_ca.pem

keytool -storepass test123 -keystore $COMPB_CA_KS -certreq -alias compb_ca \
| keytool -storepass test123 -keystore $INTER_CA_KS -gencert -alias inter_ca \
-ext bc:c=ca:true,pathlen:0 -ext ku:c=dS,kCS,cRLs -rfc > compb_ca.pem

cat root_ca.pem >> compa_ca.pem
cat root_ca.pem >> compb_ca.pem

keytool -keystore $COMPA_CA_KS -importcert -alias compa_ca \
-storepass test123 -noprompt -file ./compa_ca.pem

keytool -keystore $COMPB_CA_KS -importcert -alias compb_ca \
-storepass test123 -noprompt -file ./compb_ca.pem
~~~

---

## Company A Client certificates

Truststore for CompanyA clients. Generated under `compa`
~~~
export OU=compa

keytool -import -trustcacerts -alias root_ca \
-file ../ca_certs/root_ca.pem -keystore truststore.p12 \
-storepass test123 -noprompt

keytool -import -trustcacerts -alias inter_ca \
-file ../ca_certs/inter_ca.pem -keystore truststore.p12 \
-storepass test123 -noprompt

keytool -import -trustcacerts -alias ${OU}_ca \
-file ../ca_certs/${OU}_ca.pem -keystore truststore.p12 \
-storepass test123 -noprompt

~~~

Cd to appropriate `compa/client` directory and generate the certificates.
Modify the export command.
~~~
export CLIENT=avocado

keytool -genkeypair -keystore $CLIENT.p12 -storepass test123 -alias $CLIENT \
-keyalg EC -groupname secp256r1 -validity 36500 \
-dname "CN=$CLIENT.$OU.besu.com, OU=$OU, O=Besu, L=Brisbane, ST=QLD, C=AU" \
-ext san=dns:localhost,ip:127.0.0.1

keytool -storepass test123 -keystore "$CLIENT.p12" -certreq -alias $CLIENT \
| keytool -storepass test123 -keystore "../../ca_certs/${OU}_ca.p12" -gencert -alias ${OU}_ca \
-ext ku:c=dS,nR,kE -ext eku=sA,cA -rfc > "$CLIENT.pem"

cat ../../ca_certs/root_ca.pem >> $CLIENT.pem

keytool -keystore $CLIENT.p12 -importcert -alias $CLIENT \
-storepass test123 -noprompt -file ./$CLIENT.pem
~~~

Follow above steps for compb and appropriate clients.


## NSS database setup
The nss database will be setup in `compa/client` directories.

Initialize empty nss database. Modify client and OU export.
~~~
export CLIENT=avocado
export OU=compa

mkdir nssdb
echo "test123" > nsspin.txt
certutil -N -d sql:nssdb -f nsspin.txt
touch ./nssdb/secmod.db
~~~

Import client1 keystore into nss
~~~
pk12util -i ./$CLIENT.p12 -d sql:nssdb -k nsspin.txt -W test123
certutil -M -n "CN=root.ca.besu.com" -t CT,C,C -d sql:nssdb -f ./nsspin.txt
certutil -M -n "CN=inter.ca.besu.com" -t CT,C,C -d sql:nssdb -f ./nsspin.txt
certutil -M -n "CN=${OU}.ca.besu.com" -t CT,C,C -d sql:nssdb -f ./nsspin.txt

certutil -d sql:nssdb -f nsspin.txt -L
~~~

PKCS11 Configuration file
~~~
cat <<EOF >./pkcs11.cfg
name = NSScrypto-${OU}-${CLIENT}
nssSecmodDirectory = ./src/test/resources/keys/tls_context_factory/${OU}/${CLIENT}/nssdb
nssDbMode = readOnly
nssModule = keystore
showInfo = true
EOF
~~~

## CRL certificate
As mentioned at the start of README, `apricot` from compa and `blueberry` from compb's certificates are in revoke list.
The crl list is maintained under `ca_certs/crl`.

The apricot will be revoked by compa_ca and blueberry will be revoked by compb_ca. We would temporarily need to 
export private keys of compa_ca and compb_ca to create the CRL.

Following operations are performed under `ca_certs/crl`. 
_Note: On Mac OS, using `gnutls-certtool`. On Linux, `certtool` can be used._

~~~
openssl pkcs12 -nodes -in ../compa_ca.p12 -out compa_ca_key.pem -passin pass:test123 -nocerts
openssl pkcs12 -nodes -in ../compa_ca.p12 -out compa_ca.pem -passin pass:test123 -nokeys
openssl pkcs12 -nodes -in ../compb_ca.p12 -out compb_ca_key.pem -passin pass:test123 -nocerts
openssl pkcs12 -nodes -in ../compb_ca.p12 -out compb_ca.pem -passin pass:test123 -nokeys

openssl pkcs12 -nokeys -clcerts -in ../../compa/apricot/apricot.p12 -out apricot.pem -passin pass:test123
openssl pkcs12 -nokeys -clcerts -in ../../compb/blueberry/blueberry.p12 -out blueberry.pem -passin pass:test123

gnutls-certtool --generate-crl --load-ca-privkey ./compa_ca_key.pem --load-ca-certificate ./compa_ca.pem \
--load-certificate ./apricot.pem >> crl.pem
gnutls-certtool --generate-crl --load-ca-privkey ./compb_ca_key.pem --load-ca-certificate ./compb_ca.pem \
--load-certificate ./blueberry.pem >> crl.pem

keytool -printcrl -file ./crl.pem

rm *_ca_key.pem
rm *_ca.pem
rm apricot.pem
rm blueberry.pem
~~~
