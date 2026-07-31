# TLS setup

1. Obtain a certificate with Let's Encrypt, for example with `certbot certonly`.
2. Export the certificate and private key into PKCS12:
   `openssl pkcs12 -export -in fullchain.pem -inkey privkey.pem -out smechain.p12 -name smechain`
3. Start SMEChain with:
   `JAVA_OPTS="-Dkeystore.path=/path/to/smechain.p12 -Dkeystore.password=changeit" ./gradlew run`
4. For validator keys, use a separate PKCS12 keystore and set `SMECHAIN_KEYSTORE_PASSWORD`.

If no TLS properties are supplied, SMEChain falls back to HTTP for local development and prints a warning.
