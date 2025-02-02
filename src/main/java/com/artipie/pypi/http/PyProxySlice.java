/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/python-adapter/LICENSE.txt
 */
package com.artipie.pypi.http;

import com.artipie.asto.Storage;
import com.artipie.asto.cache.FromRemoteCache;
import com.artipie.http.Slice;
import com.artipie.http.client.ClientSlices;
import com.artipie.http.client.UriClientSlice;
import com.artipie.http.client.auth.AuthClientSlice;
import com.artipie.http.client.auth.Authenticator;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rt.ByMethodsRule;
import com.artipie.http.rt.RtRule;
import com.artipie.http.rt.RtRulePath;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.SliceSimple;
import java.net.URI;

/**
 * Python proxy slice.
 * @since 0.7
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class PyProxySlice extends Slice.Wrap {

    /**
     * New maven proxy without cache.
     * @param clients HTTP clients
     * @param remote Remote URI
     * @param storage Cache storage
     */
    public PyProxySlice(final ClientSlices clients, final URI remote, final Storage storage) {
        this(clients, remote, Authenticator.ANONYMOUS, storage);
    }

    /**
     * Ctor.
     * @param clients Http clients
     * @param remote Remote URI
     * @param auth Authenticator
     * @param cache Repository cache storage
     * @checkstyle ParameterNumberCheck (500 lines)
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public PyProxySlice(
        final ClientSlices clients,
        final URI remote,
        final Authenticator auth,
        final Storage cache
    ) {
        super(
            new SliceRoute(
                new RtRulePath(
                    new ByMethodsRule(RqMethod.GET),
                    new ProxySlice(
                        new AuthClientSlice(new UriClientSlice(clients, remote), auth),
                        new FromRemoteCache(cache)
                    )
                ),
                new RtRulePath(
                    RtRule.FALLBACK,
                    new SliceSimple(new RsWithStatus(RsStatus.METHOD_NOT_ALLOWED))
                )
            )
        );
    }

}
