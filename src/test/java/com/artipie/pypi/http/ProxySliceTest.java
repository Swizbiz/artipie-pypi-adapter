/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/python-adapter/LICENSE.txt
 */
package com.artipie.pypi.http;

import com.artipie.asto.Content;
import com.artipie.asto.FailedCompletionStage;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.cache.FromRemoteCache;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.http.Headers;
import com.artipie.http.hm.RsHasBody;
import com.artipie.http.hm.RsHasHeaders;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.hm.SliceHasResponse;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsFull;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.slice.SliceSimple;
import org.cactoos.map.MapEntry;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Test for {@link ProxySlice}.
 * @since 0.7
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ProxySliceTest {

    /**
     * Test storage.
     */
    private Storage storage;

    @BeforeEach
    void init() {
        this.storage = new InMemoryStorage();
    }

    @Test
    void getsContentFromRemoteAndAdsItToCache() {
        final byte[] body = "some html".getBytes();
        final String key = "index";
        MatcherAssert.assertThat(
            "Returns body from remote",
            new ProxySlice(
                new SliceSimple(
                    new RsFull(
                        RsStatus.OK, new Headers.From("content-type", "smth"),
                        new Content.From(body)
                    )
                ),
                new FromRemoteCache(this.storage)
            ),
            new SliceHasResponse(
                Matchers.allOf(
                    new RsHasBody(body),
                    new RsHasHeaders(
                        new MapEntry<>("content-type", "smth"),
                        new MapEntry<>("Content-Length", "9")
                    )
                ),
                new RequestLine(RqMethod.GET, String.format("/%s", key))
            )
        );
        MatcherAssert.assertThat(
            "Stores index in cache",
            new BlockingStorage(this.storage).value(new Key.From(key)),
            new IsEqual<>(body)
        );
    }

    @ParameterizedTest
    @CsvSource({
        "my project versions list in html,text/html,my-project",
        "my project wheel,multipart/form-data,my-project.whl"
    })
    void getsFromCacheOnError(final String data, final String header, final String key) {
        final byte[] body = data.getBytes();
        this.storage.save(new Key.From(key), new Content.From(body)).join();
        MatcherAssert.assertThat(
            "Returns body from cache",
            new ProxySlice(
                new SliceSimple(new RsWithStatus(RsStatus.INTERNAL_ERROR)),
                new FromRemoteCache(this.storage)
            ),
            new SliceHasResponse(
                Matchers.allOf(
                    new RsHasStatus(RsStatus.OK), new RsHasBody(body),
                    new RsHasHeaders(
                        new MapEntry<>("content-type", header),
                        new MapEntry<>("Content-Length", String.valueOf(body.length))
                    )
                ),
                new RequestLine(RqMethod.GET, String.format("/%s", key))
            )
        );
        MatcherAssert.assertThat(
            "Data stays intact in cache",
            new BlockingStorage(this.storage).value(new Key.From(key)),
            new IsEqual<>(body)
        );
    }

    @Test
    void returnsNotFoundWhenRemoteReturnedBadRequest() {
        MatcherAssert.assertThat(
            "Status 400 returned",
            new ProxySlice(
                new SliceSimple(new RsWithStatus(RsStatus.BAD_REQUEST)),
                new FromRemoteCache(this.storage)
            ),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.NOT_FOUND),
                new RequestLine(RqMethod.GET, "/any")
            )
        );
        MatcherAssert.assertThat(
            "Cache storage is empty",
            this.storage.list(Key.ROOT).join().isEmpty(),
            new IsEqual<>(true)
        );
    }

    @ParameterizedTest
    @CsvSource({
        "My_Project,my-project",
        "My.Project.whl,My.Project.whl",
        "Johns.Project.tar.gz,Johns.Project.tar.gz",
        "AnotherIndex,anotherindex"
    })
    void normalisesNamesWhenNecessary(final String line, final String key) {
        final byte[] body = "python artifact".getBytes();
        MatcherAssert.assertThat(
            "Returns body from remote",
            new ProxySlice(
                new SliceSimple(
                    new RsFull(
                        RsStatus.OK, new Headers.From("content-type", "smth"),
                        new Content.From(body)
                    )
                ),
                new FromRemoteCache(this.storage)
            ),
            new SliceHasResponse(
                Matchers.allOf(
                    new RsHasBody(body),
                    new RsHasHeaders(
                        new MapEntry<>("content-type", "smth"),
                        new MapEntry<>("Content-Length", String.valueOf(body.length))
                    )
                ),
                new RequestLine(RqMethod.GET, String.format("/%s", line))
            )
        );
        MatcherAssert.assertThat(
            "Stores content in cache",
            new BlockingStorage(this.storage).value(new Key.From(key)),
            new IsEqual<>(body)
        );
    }

    @Test
    void returnsNotFoundOnRemoteAndCacheError() {
        MatcherAssert.assertThat(
            "Status 400 returned",
            new ProxySlice(
                new SliceSimple(new RsWithStatus(RsStatus.BAD_REQUEST)),
                (key, remote, cache) ->
                    new FailedCompletionStage<>(
                        new IllegalStateException("Failed to obtain item from cache")
                    )
            ),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.NOT_FOUND),
                new RequestLine(RqMethod.GET, "/anything")
            )
        );
        MatcherAssert.assertThat(
            "Cache storage is empty",
            this.storage.list(Key.ROOT).join().isEmpty(),
            new IsEqual<>(true)
        );
    }

}
