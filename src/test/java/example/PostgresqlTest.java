package example;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/*
次のような、hostssl と hostnossl ユーザが存在している前提のテストコードです。

echo "hostssl all user_hostssl all md5" >> "${PGDATA}/pg_hba.conf"
echo "hostnossl all user_hostnossl all md5" >> "${PGDATA}/pg_hba.conf"

 */
class PostgresqlTest {

    private static String _jdbcUrl;

    private static Path _certificateFilePath;

    private static PostgreSQL13Container _container;

    private SSLContext defaultSslContext;

    @BeforeAll
    static void beforeAll() throws Exception {
        final URL resource = Thread.currentThread().getContextClassLoader().getResource("docker/postgresql");
        final Path dir = Paths.get(resource.toURI());
        final Path keyStorePath = dir.resolve("server.p12");
        final Path certificatePath = dir.resolve("server.crt");
        final Path privateKeyPath = dir.resolve("server.key");
        for (final Path path : Arrays.asList(keyStorePath, certificatePath, privateKeyPath)) {
            Files.deleteIfExists(path);
        }

        /*
         * PostgreSQLサーバ側の鍵ペアと証明書を作成する。
         */
        final KeytoolWrapper keytool = new KeytoolWrapper();
        final String alias = "postgresql13";
        final String password = "password1";
        final String storeType = "PKCS12";
        keytool.execute(
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-dname", "CN=Web Server1, OU=Unit1, O=Organization1, L=City1,S=State1,C=US",
                "-ext", "SAN=dns:localhost",
                "-keypass", password,
                "-keystore", keyStorePath.toString(),
                "-storetype", storeType,
                "-storepass", password,
                "-validity", "3650"
        );

        /*
        証明書をファイルへ取り出す。
        このファイルは、PostgreSQLサーバ側と、JDBCクライアント側で使用する。

        keytool -exportcert -alias servercert1 -file server-rfc.cer -rfc -keystore server.jks -storepass password
         */
        keytool.execute(
                "-exportcert",
                "-alias", alias,
                "-file", certificatePath.toString(),
                "-rfc",
                "-keystore", keyStorePath.toString(),
                "-storepass", password
        );

        final KeyStore keyStore = KeyStore.getInstance(storeType);
        try (InputStream is = Files.newInputStream(keyStorePath)) {
            keyStore.load(is, password.toCharArray());
        }

        /*
         * Private鍵をファイルへ取り出す
         * PostgreSQLサーバ側で使用する。
         */
        final PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
        PemFiles.write(privateKeyPath, privateKey.getEncoded(), "PRIVATE KEY");

        /*
         * 証明書とPrivate鍵を使用するよう設定した PostgreSQL を起動する。
         */
        final PostgreSQL13Container container = new PostgreSQL13Container();
        _container = container;

        container.withFileSystemBind(dir.toString(), "/docker-entrypoint-initdb.d");
        container.start();

        _jdbcUrl = "jdbc:postgresql://localhost:" + container.getPort() + "/postgres";
        // JDBCクライアントが使うため、証明書のパスを記録しておく
        _certificateFilePath = certificatePath;
    }

    @AfterAll
    static void afterAll() throws Exception {
        _container.close();
    }

    @BeforeEach
    void setUp(final TestInfo testInfo) throws Exception {
        System.out.println();
        System.out.println(String.format("==== START: %s ====", testInfo.getDisplayName()));

        defaultSslContext = SSLContext.getDefault();
    }

    @AfterEach
    void tearDown() {
        SSLContext.setDefault(defaultSslContext);
    }

    /*
     * hostnossl に設定してあるため、SSL OFFで接続できる。
     */
    @Test
    void ssl_disable_hostnossl() throws Exception {
        final Connection conn = DriverManager.getConnection(
                _jdbcUrl +
                        "?sslmode=disable",
                "user_hostnossl", "password");

        printProperties(conn);
        conn.close();
    }

    /*
     * hostssl に設定してあるため、SSL OFFでは接続できない。
     */
    @Test
    void ssl_disable_hostssl() throws Exception {
        final SQLException exception = assertThrows(SQLException.class,
                () -> DriverManager.getConnection(
                        _jdbcUrl +
                                "?sslmode=disable",
                        "user_hostssl", "password"));
        final Throwable cause = exception.getCause();
        assertThat(cause, is(nullValue()));
    }

    /*
     * hostssl であれば、サーバの証明書ありで接続できる。
     */
    @Test
    void ssl_verifyFull_hostssl_withCertificate() throws Exception {
        setupCertificate(_certificateFilePath);

        final Connection conn = DriverManager.getConnection(
                _jdbcUrl
                        + "?sslmode=verify-full" +
                        "&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
                "user_hostssl", "password");

        printProperties(conn);
        conn.close();
    }

    /*
     * hostnossl では、サーバの証明書ありでも接続できない。
     */
    @Test
    void ssl_verifyFull_hostnossl_withCertificate() throws Exception {
        setupCertificate(_certificateFilePath);

        final SQLException exception = assertThrows(SQLException.class,
                () -> DriverManager.getConnection(
                        _jdbcUrl
                                + "?sslmode=verify-full" +
                                "&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
                        "user_hostnossl", "password"));
        final Throwable cause = exception.getCause();
        assertThat(cause, is(nullValue()));
    }

    /*
     * hostssl であっても、証明書なしでは接続できない。
     */
    @Test
    void ssl_verifyFull_hostssl_withoutCertificate() throws Exception {
        final SQLException exception = assertThrows(SQLException.class,
                () -> DriverManager.getConnection(
                        _jdbcUrl
                                + "?sslmode=verify-full" +
                                "&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory",
                        "user_hostssl", "password"));
        final Throwable cause = exception.getCause();
        assertThat(cause.getClass(), is(SSLHandshakeException.class));
    }

    private void setupCertificate(final Path certificatePath)
            throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException,
            KeyManagementException {

        final Certificate certificate = loadCertificate(certificatePath);

        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null);
        keyStore.setCertificateEntry("pg13", certificate);
        final TrustManager[] trustManagers = getTrustManagers(keyStore);

        final SSLContext sslContext = SSLContext.getInstance("SSL");
        // client側ではTrustManagerだけあれば良い。
        sslContext.init(null, trustManagers, SecureRandom.getInstanceStrong());
        // org.postgresql.ssl.DefaultJavaSSLFactory では SSLContext.getDefault() が使われる
        SSLContext.setDefault(sslContext);
    }

    private static Certificate loadCertificate(final Path path) throws CertificateException, IOException {
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (final InputStream is = Files.newInputStream(path)) {
            return factory.generateCertificate(is);
        }
    }

    private TrustManager[] getTrustManagers(final KeyStore keyStore)
            throws NoSuchAlgorithmException, KeyStoreException {
        final TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        return factory.getTrustManagers();
    }

    private void printProperties(final Connection conn) throws SQLException {
        final Map<String, String> map = new LinkedHashMap<>();
        collect(conn, map::put);
        map.forEach((k, v) -> System.out.println(k + " = " + v));
    }

    private void collect(final Connection conn, final BiConsumer<String, String> consumer) throws SQLException {
        final DatabaseMetaData metaData = conn.getMetaData();
        consumer.accept("URL", metaData.getURL());
        consumer.accept("URL", metaData.getURL());
        consumer.accept("DatabaseProductName", metaData.getDatabaseProductName());
        consumer.accept("DatabaseProductVersion", metaData.getDatabaseProductVersion());
        consumer.accept("DriverName", metaData.getDriverName());
        consumer.accept("DriverVersion", metaData.getDriverVersion());
    }

}
