/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.pypi.http;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.http.client.jetty.JettyClientSlices;
import com.artipie.http.rs.StandardRs;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.http.slice.SliceSimple;
import com.artipie.pypi.PypiContainer;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.net.URI;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.Testcontainers;

/**
 * Test for {@link PyProxySlice}.
 * @since 0.7
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
final class PyProxySliceCacheITCase {

    /**
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Jetty client.
     */
    private final JettyClientSlices client = new JettyClientSlices();

    /**
     * Server port.
     */
    private int port;

    /**
     * Vertx slice server instance.
     */
    private VertxSliceServer server;

    /**
     * Bad vertx slice server instance, always returns 404 status.
     */
    private VertxSliceServer bad;

    /**
     * Test storage.
     */
    private Storage storage;

    @BeforeEach
    void setUp() throws Exception {
        this.client.start();
        this.storage = new InMemoryStorage();
        this.bad = new VertxSliceServer(
            PyProxySliceCacheITCase.VERTX,
            new SliceSimple(StandardRs.NOT_FOUND)
        );
        this.server = new VertxSliceServer(
            PyProxySliceCacheITCase.VERTX,
            new LoggingSlice(
                new PyProxySlice(
                    this.client,
                    URI.create(String.format("http://localhost:%d", this.bad.start())),
                    this.storage
                )
            )
        );
        this.port = this.server.start();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void installsFromCache() throws IOException, InterruptedException {
        Testcontainers.exposeHostPorts(this.port);
        new TestResource("pypi_repo/alarmtime-0.1.5.tar.gz")
            .saveTo(this.storage, new Key.From("alarmtime/alarmtime-0.1.5.tar.gz"));
        this.storage.save(
            new Key.From("alarmtime"), new Content.From(this.indexHtml().getBytes())
        ).join();
        try (PypiContainer runtime = new PypiContainer()) {
            MatcherAssert.assertThat(
                runtime.bash(
                    String.format(
                        // @checkstyle LineLengthCheck (1 line)
                        "pip install --index-url %s --verbose --no-deps --trusted-host host.testcontainers.internal \"alarmtime\"",
                        runtime.localAddress(this.port)
                    )
                ),
                Matchers.containsString("Successfully installed alarmtime-0.1.5")
            );
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        this.client.stop();
        this.server.stop();
        this.bad.stop();
    }

    private String indexHtml() {
        return String.join(
            "\n", "<!DOCTYPE html>",
            "<html>",
            "  <head>",
            "    <title>Links for AlarmTime</title>",
            "  </head>",
            "  <body>",
            "    <h1>Links for AlarmTime</h1>",
            "    <a href=\"/alarmtime/alarmtime-0.1.5.tar.gz\">alarmtime-0.1.5.tar.gz</a><br/>",
            "</body>",
            "</html>"
        );
    }

}
