/*
 * Copyright 2024 Marc Nuri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marcnuri.isotope.e2e;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Properties;

/**
 * JUnit 5 extension that manages the full Isotope Mail test environment:
 * - GreenMail (embedded IMAP/SMTP server)
 * - Spring Boot backend (server JAR)
 * - Frontend static file server (Python http.server with SPA fallback)
 * - Chrome WebDriver (headless, managed by WebDriverManager)
 */
public class IsotopeTestEnvironment implements BeforeAllCallback, AfterAllCallback {

    private static final Logger log = LoggerFactory.getLogger(IsotopeTestEnvironment.class);

    static final String USER1 = "user1@localhost";
    static final String USER2 = "user2@localhost";
    static final String PASSWORD = "password123";

    private static GreenMail greenMail;
    private static Process backendProcess;
    private static Process frontendProcess;
    private static WebDriver driver;

    private static int imapPort;
    private static int smtpPort;
    private static int backendPort;
    private static int frontendPort;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (greenMail != null) {
            return;
        }
        startGreenMail();
        seedTestData();
        startBackend();
        startFrontend();
        initWebDriver();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // WebDriver, frontend, and backend are cleaned up via shutdown hook
    }

    private void startGreenMail() {
        imapPort = findAvailablePort();
        smtpPort = findAvailablePort();

        final ServerSetup imapSetup = new ServerSetup(imapPort, "127.0.0.1", ServerSetup.PROTOCOL_IMAP);
        final ServerSetup smtpSetup = new ServerSetup(smtpPort, "127.0.0.1", ServerSetup.PROTOCOL_SMTP);

        greenMail = new GreenMail(new ServerSetup[]{imapSetup, smtpSetup});
        greenMail.start();

        // Create user accounts
        greenMail.setUser(USER1, "user1", PASSWORD);
        greenMail.setUser(USER2, "user2", PASSWORD);

        log.info("GreenMail started - IMAP port: {}, SMTP port: {}", imapPort, smtpPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cleanup();
        }));
    }

    private void seedTestData() throws MessagingException {
        // Seed emails for user1
        deliverMessage(USER1, USER2, "Welcome to Isotope", "This is a welcome email for testing.");
        deliverMessage(USER2, USER1, "Re: Welcome to Isotope", "Thanks for the welcome!");
        deliverMessage(USER2, USER1, "Meeting Tomorrow", "Let's meet tomorrow at 10am.");
        deliverMessage(USER1, USER2, "Project Update", "The project is going well.");
        deliverMessage(USER2, USER1, "Important Notice", "Please review the attached document.");
    }

    private void deliverMessage(String from, String to, String subject, String body)
            throws MessagingException {
        final Session session = Session.getInstance(new Properties());
        final MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject);
        message.setText(body);
        message.setSentDate(new Date());
        // GreenMail getUserByEmail looks up by email address (e.g. user1@localhost)
        greenMail.getUserManager().getUserByEmail(to).deliver(message);
    }

    private void startBackend() throws IOException, InterruptedException {
        backendPort = findAvailablePort();

        final String projectRoot = findProjectRoot();
        final String serverJar = findServerJar(projectRoot);

        log.info("Starting backend from JAR: {} on port {}", serverJar, backendPort);

        // Find Java executable - prefer JAVA_HOME, fall back to PATH
        final String javaExecutable = findJavaExecutable();

        final ProcessBuilder pb = new ProcessBuilder(
                javaExecutable, "-jar", serverJar,
                "--server.port=" + backendPort,
                "--spring.profiles.active=dev",
                "--server.use-forward-headers=true"
        );
        pb.environment().put("SPRING_MAIL_HOST", "127.0.0.1");
        pb.environment().put("TRUSTED_HOSTS", "");
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File(projectRoot + "/e2e-tests/target/backend.log"));

        // Set system properties for the IMAP/SMTP connection
        // The backend uses the credentials from the login request, not env vars
        // But we need to make sure SSL is disabled for test connections
        backendProcess = pb.start();

        waitForBackend();
        log.info("Backend started on port {}", backendPort);
    }

    private void startFrontend() throws IOException, InterruptedException {
        frontendPort = findAvailablePort();

        final String projectRoot = findProjectRoot();
        final String clientDist = projectRoot + "/client/dist";

        // Check if dist directory exists
        if (!new File(clientDist).exists()) {
            throw new IllegalStateException(
                    "Client dist directory not found at " + clientDist
                            + ". Build the client first with: cd client && npm run build");
        }

        log.info("Starting frontend file server from {} on port {}", clientDist, frontendPort);

        // Create a Python SPA server script that proxies /api/ to backend and falls back to index.html
        final Path spaServerScript = createSpaServerScript(clientDist, backendPort);

        final ProcessBuilder pb = new ProcessBuilder(
                "python3", spaServerScript.toAbsolutePath().toString(),
                String.valueOf(frontendPort)
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File(projectRoot + "/e2e-tests/target/frontend.log"));
        frontendProcess = pb.start();

        waitForFrontend();
        log.info("Frontend started on port {}", frontendPort);
    }

    private void initWebDriver() {
        WebDriverManager.chromedriver().setup();

        final ChromeOptions options = new ChromeOptions();
        // Use the real Chrome binary (not any wrapper scripts)
        final File chromeStable = new File("/usr/bin/google-chrome-stable");
        if (chromeStable.exists()) {
            options.setBinary(chromeStable.getAbsolutePath());
        }
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        driver = new ChromeDriver(options);
        log.info("Chrome WebDriver initialized (headless)");
    }

    private void waitForBackend() throws InterruptedException {
        final long start = System.currentTimeMillis();
        final long timeout = 60_000;
        while (System.currentTimeMillis() - start < timeout) {
            try {
                final HttpURLConnection conn = (HttpURLConnection) new URL(
                        "http://127.0.0.1:" + backendPort + "/v1/application/configuration"
                ).openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                if (conn.getResponseCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // Server not ready yet
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("Backend failed to start within " + timeout + "ms");
    }

    private void waitForFrontend() throws InterruptedException {
        final long start = System.currentTimeMillis();
        final long timeout = 15_000;
        while (System.currentTimeMillis() - start < timeout) {
            try {
                final HttpURLConnection conn = (HttpURLConnection) new URL(
                        "http://127.0.0.1:" + frontendPort + "/"
                ).openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                if (conn.getResponseCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // Server not ready yet
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Frontend failed to start within " + timeout + "ms");
    }

    private static void cleanup() {
        log.info("Cleaning up test environment...");
        if (driver != null) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("Error closing WebDriver", e);
            }
            driver = null;
        }
        if (frontendProcess != null) {
            frontendProcess.destroyForcibly();
            frontendProcess = null;
        }
        if (backendProcess != null) {
            backendProcess.destroyForcibly();
            backendProcess = null;
        }
        if (greenMail != null) {
            greenMail.stop();
            greenMail = null;
        }
    }

    static String findProjectRoot() {
        File dir = new File(System.getProperty("user.dir"));
        // Walk up until we find the directory containing both 'server' and 'client'
        while (dir != null) {
            if (new File(dir, "server").exists() && new File(dir, "client").exists()) {
                return dir.getAbsolutePath();
            }
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("Could not find project root (directory containing 'server' and 'client')");
    }

    private static String findServerJar(String projectRoot) {
        final File buildLibs = new File(projectRoot, "server/build/libs");
        if (!buildLibs.exists() || !buildLibs.isDirectory()) {
            throw new IllegalStateException(
                    "Server build directory not found at " + buildLibs.getAbsolutePath()
                            + ". Build the server first with: cd server && ./gradlew bootJar");
        }
        final File[] jars = buildLibs.listFiles((d, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            throw new IllegalStateException("No JAR files found in " + buildLibs.getAbsolutePath());
        }
        return jars[0].getAbsolutePath();
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not find available port", e);
        }
    }

    public static GreenMail getGreenMail() {
        return greenMail;
    }

    public static WebDriver getDriver() {
        return driver;
    }

    public static int getImapPort() {
        return imapPort;
    }

    public static int getSmtpPort() {
        return smtpPort;
    }

    public static int getBackendPort() {
        return backendPort;
    }

    public static int getFrontendPort() {
        return frontendPort;
    }

    public static String getFrontendUrl() {
        return "http://127.0.0.1:" + frontendPort;
    }

    /**
     * Finds the Java executable to use for running the backend.
     * Prefers Java 11 if available (for compatibility with older Spring Boot),
     * falls back to the current Java.
     */
    private static String findJavaExecutable() {
        // Check for Java 11 installation
        final File java11 = new File("/usr/lib/jvm/java-11-openjdk-amd64/bin/java");
        if (java11.exists()) {
            log.info("Using Java 11 for backend: {}", java11.getAbsolutePath());
            return java11.getAbsolutePath();
        }
        // Fall back to the java on PATH
        return "java";
    }

    /**
     * Returns the login URL pre-filled with GreenMail connection details for the given user.
     */
    public static String getLoginUrl(String user) {
        final String username = user.contains("@") ? user.substring(0, user.indexOf('@')) : user;
        return getFrontendUrl() + "/login"
                + "?serverHost=127.0.0.1"
                + "&serverPort=" + imapPort
                + "&user=" + username
                + "&imapSsl=false"
                + "&smtpHost=127.0.0.1"
                + "&smtpPort=" + smtpPort
                + "&smtpSsl=false";
    }

    /**
     * Creates a Python script that serves static files from the given directory
     * with API proxy to backend and SPA fallback (returns index.html for client-side routes).
     */
    private static Path createSpaServerScript(String directory, int apiBackendPort) throws IOException {
        final String script = String.join("\n",
                "import http.server",
                "import os",
                "import sys",
                "import urllib.request",
                "import urllib.error",
                "",
                "BACKEND_PORT = " + apiBackendPort,
                "",
                "class SPAHandler(http.server.SimpleHTTPRequestHandler):",
                "    def __init__(self, *args, **kwargs):",
                "        super().__init__(*args, directory='" + directory.replace("'", "\\'") + "', **kwargs)",
                "",
                "    def _proxy_to_backend(self):",
                "        # Strip /api prefix since backend serves at /v1/... not /api/v1/...",
                "        backend_path = self.path[4:] if self.path.startswith('/api/') else self.path",
                "        url = f'http://127.0.0.1:{BACKEND_PORT}{backend_path}'",
                "        try:",
                "            content_length = int(self.headers.get('Content-Length', 0))",
                "            body = self.rfile.read(content_length) if content_length > 0 else None",
                "            req = urllib.request.Request(url, data=body, method=self.command)",
                "            # Forward original Host header so Spring HATEOAS generates correct links",
                "            original_host = self.headers.get('Host', '')",
                "            for key, val in self.headers.items():",
                "                if key.lower() not in ('content-length',):",
                "                    req.add_header(key, val)",
                "            if original_host:",
                "                req.add_header('X-Forwarded-Host', original_host)",
                "                req.add_header('X-Forwarded-Proto', 'http')",
                "            resp = urllib.request.urlopen(req)",
                "            self.send_response(resp.status)",
                "            for key, val in resp.getheaders():",
                "                if key.lower() not in ('transfer-encoding',):",
                "                    self.send_header(key, val)",
                "            self.end_headers()",
                "            self.wfile.write(resp.read())",
                "        except urllib.error.HTTPError as e:",
                "            self.send_response(e.code)",
                "            for key, val in e.headers.items():",
                "                if key.lower() not in ('transfer-encoding',):",
                "                    self.send_header(key, val)",
                "            self.end_headers()",
                "            self.wfile.write(e.read())",
                "        except Exception as e:",
                "            self.send_error(502, f'Backend proxy error: {e}')",
                "",
                "    def do_GET(self):",
                "        if self.path.startswith('/api/') or self.path.startswith('/v1/'):",
                "            return self._proxy_to_backend()",
                "        path = self.translate_path(self.path)",
                "        if not os.path.exists(path) or (os.path.isdir(path) and not os.path.exists(os.path.join(path, 'index.html'))):",
                "            self.path = '/index.html'",
                "        return super().do_GET()",
                "",
                "    def do_POST(self):",
                "        return self._proxy_to_backend()",
                "",
                "    def do_PUT(self):",
                "        return self._proxy_to_backend()",
                "",
                "    def do_DELETE(self):",
                "        return self._proxy_to_backend()",
                "",
                "    def do_PATCH(self):",
                "        return self._proxy_to_backend()",
                "",
                "port = int(sys.argv[1])",
                "server = http.server.HTTPServer(('127.0.0.1', port), SPAHandler)",
                "print(f'SPA server with API proxy started on port {port}, backend on port {BACKEND_PORT}')",
                "server.serve_forever()"
        );

        final Path scriptFile = Files.createTempFile("isotope-spa-server-", ".py");
        Files.write(scriptFile, script.getBytes(StandardCharsets.UTF_8));
        scriptFile.toFile().deleteOnExit();
        return scriptFile;
    }
}
