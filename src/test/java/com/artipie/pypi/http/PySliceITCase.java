/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/python-adapter/LICENSE.txt
 */
package com.artipie.pypi.http;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.http.auth.Authentication;
import com.artipie.http.auth.Permissions;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.pypi.PypiDeployment;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.StringContains;
import org.hamcrest.text.StringContainsInOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * A test which ensures {@code pip} console tool compatibility with the adapter.
 *
 * @since 0.4
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@DisabledOnOs(OS.WINDOWS)
public final class PySliceITCase {

    /**
     * Vertx.
     */
    private Vertx vertx;

    /**
     * Vertx slice server.
     */
    private VertxSliceServer server;

    /**
     * Test storage.
     */
    private Storage asto;

    /**
     * Pypi container.
     */
    @RegisterExtension
    private final PypiDeployment container = new PypiDeployment();

    @BeforeEach
    void start() {
        this.asto = new InMemoryStorage();
        this.vertx = Vertx.vertx();
    }

    @AfterEach
    void stop() {
        if (this.server != null) {
            this.server.stop();
        }
        if (this.vertx != null) {
            this.vertx.close();
        }
    }

    @Test
    void canPublishAndInstallWithAuth() throws Exception {
        final String user = "alladin";
        final String pswd = "opensesame";
        this.startServer(
            (identity, perm) -> user.equals(identity.name())
                && ("download".equals(perm) || "upload".equals(perm)),
            new Authentication.Single(user, pswd)
        );
        final String alarmtime = "pypi_repo/alarmtime-0.1.5.tar.gz";
        this.container.putBinaryToContainer(new TestResource(alarmtime).asBytes(), alarmtime);
        MatcherAssert.assertThat(
            "AlarmTime successfully uploaded",
            this.container.bash(
                String.format(
                    "python3 -m twine upload --repository-url %s -u %s -p %s --verbose %s",
                    this.container.localAddress(), user, pswd, alarmtime
                )
            ),
            new StringContainsInOrder(
                new ListOf<String>("Uploading alarmtime-0.1.5.tar.gz", "100%")
            )
        );
        MatcherAssert.assertThat(
            "AlarmTime found in storage",
            this.asto.exists(new Key.From("alarmtime/alarmtime-0.1.5.tar.gz")).join(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "AlarmTime successfully installed",
            this.container.bash(
                String.format(
                    // @checkstyle LineLengthCheck (1 line)
                    "pip install --index-url %s --no-deps --trusted-host host.testcontainers.internal alarmtime",
                    this.container.localAddress(user, pswd)
                )
            ),
            new StringContains("Successfully installed alarmtime-0.1.5")
        );
    }

    @Test
    void canPublishAndInstallIfNameIsNotNormalized() throws Exception {
        this.startServer();
        final String abtest = "pypi_repo/ABtests-0.0.2.1-py2.py3-none-any.whl";
        this.container.putBinaryToContainer(new TestResource(abtest).asBytes(), abtest);
        MatcherAssert.assertThat(
            "ABtests successfully uploaded",
            this.container.bash(
                String.format(
                    "python3 -m twine upload --repository-url %s -u any -p any --verbose %s",
                    this.container.localAddress(), abtest
                )
            ),
            new StringContainsInOrder(
                new ListOf<String>(
                    "Uploading ABtests-0.0.2.1-py2.py3-none-any.whl", "100%"
                )
            )
        );
        MatcherAssert.assertThat(
            "ABtests found in storage",
            this.asto.exists(new Key.From("abtests/ABtests-0.0.2.1-py2.py3-none-any.whl")).join(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "ABtests successfully installed",
            this.container.bash(
                String.format(
                    // @checkstyle LineLengthCheck (1 line)
                    "pip install --index-url %s --no-deps --trusted-host host.testcontainers.internal ABtests",
                    this.container.localAddress()
                )
            ),
            new StringContains("Successfully installed ABtests-0.0.2.1")
        );
    }

    @Test
    void canPublishSeveralPackages() throws Exception {
        this.startServer();
        final String zip = "pypi_repo/artipie-sample-0.2.zip";
        this.container.putBinaryToContainer(new TestResource(zip).asBytes(), zip);
        final String tar = "pypi_repo/artipie-sample-0.2.tar.gz";
        this.container.putBinaryToContainer(new TestResource(tar).asBytes(), tar);
        final String whl = "pypi_repo/artipie_sample-0.2-py3-none-any.whl";
        this.container.putBinaryToContainer(new TestResource(whl).asBytes(), whl);
        MatcherAssert.assertThat(
            this.container.bash(
                String.format(
                    // @checkstyle LineLengthCheck (1 line)
                    "python3 -m twine upload --repository-url %s -u any -p any --verbose pypi_repo/*",
                    this.container.localAddress()
                )
            ),
            Matchers.allOf(
                // @checkstyle LineLengthCheck (3 lines)
                new StringContainsInOrder(new ListOf<String>("Uploading artipie-sample-0.2.zip", "100%")),
                new StringContainsInOrder(new ListOf<String>("Uploading artipie-sample-0.2.tar.gz", "100%")),
                new StringContainsInOrder(new ListOf<String>("Uploading artipie_sample-0.2-py3-none-any.whl", "100%"))
            )
        );
    }

    @Test
    void canInstallWithVersion() throws Exception {
        this.putPackages();
        this.startServer();
        MatcherAssert.assertThat(
            this.container.bash(
                String.format(
                    // @checkstyle LineLengthCheck (1 line)
                    "pip install --index-url %s --no-deps --trusted-host host.testcontainers.internal \"alarmtime==0.1.5\"",
                    this.container.localAddress()
                )
            ),
            Matchers.containsString("Successfully installed alarmtime-0.1.5")
        );
    }

    @Test
    void canSearch() throws Exception {
        this.putPackages();
        this.startServer();
        MatcherAssert.assertThat(
            this.container.bash(
                String.format(
                    "pip search alarmtime --index %s", this.container.localAddress()
                )
            ),
            Matchers.stringContainsInOrder("AlarmTime", "0.1.5")
        );
    }

    private void putPackages() {
        new TestResource("pypi_repo/alarmtime-0.1.5.tar.gz")
            .saveTo(this.asto, new Key.From("alarmtime", "alarmtime-0.1.5.tar.gz"));
    }

    private void startServer(final Permissions perms, final Authentication auth) {
        this.server = new VertxSliceServer(
            this.vertx,
            new LoggingSlice(new PySlice(this.asto, perms, auth)),
            this.container.port()
        );
        this.server.start();
    }

    private void startServer() {
        this.startServer(Permissions.FREE, Authentication.ANONYMOUS);
    }

}
