/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/python-adapter/LICENSE.txt
 */

package com.artipie.pypi.http;

import com.artipie.ArtipieException;
import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.streams.ContentAsStream;
import com.artipie.http.ArtipieHttpException;
import com.artipie.http.Headers;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.headers.ContentDisposition;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rq.multipart.RqMultipart;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rs.common.RsError;
import com.artipie.http.slice.KeyFromPath;
import com.artipie.pypi.NormalizedProjectName;
import com.artipie.pypi.meta.Metadata;
import com.artipie.pypi.meta.PackageInfo;
import com.artipie.pypi.meta.ValidFilename;
import com.jcabi.log.Logger;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Flowable;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.reactivestreams.Publisher;

/**
 * WheelSlice save and manage whl and tgz entries.
 *
 * @since 0.2
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
final class WheelSlice implements Slice {

    /**
     * The Storage.
     */
    private final Storage storage;

    /**
     * Ctor.
     *
     * @param storage Storage.
     */
    WheelSlice(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public Response response(
        final String line,
        final Iterable<Map.Entry<String, String>> iterable,
        final Publisher<ByteBuffer> publisher
    ) {
        final Key.From key = new Key.From(UUID.randomUUID().toString());
        // @checkstyle  (50 lines)
        return new AsyncResponse(
            this.filePart(new Headers.From(iterable), publisher, key).thenCompose(
                filename -> this.storage.value(key).thenCompose(
                    val -> new ContentAsStream<PackageInfo>(val).process(
                        input -> new Metadata.FromArchive(input, filename).read()
                    )
                ).thenCompose(
                    info -> {
                        final CompletionStage<RsStatus> res;
                        if (new ValidFilename(info, filename).valid()) {
                            res = this.storage.move(
                                key,
                                new Key.From(
                                    new KeyFromPath(new RequestLineFrom(line).uri().toString()),
                                    new NormalizedProjectName.Simple(info.name()).value(),
                                    filename
                                )
                            ).thenApply(ignored -> RsStatus.CREATED);
                        } else {
                            res = this.storage.delete(key)
                                .thenApply(nothing -> RsStatus.BAD_REQUEST);
                        }
                        return res.thenApply(RsWithStatus::new);
                    }
                )
            ).handle(
                (resp, throwable) -> {
                    Response res = resp;
                    if (throwable != null) {
                        res = new RsError(
                            new ArtipieHttpException(RsStatus.BAD_REQUEST, throwable)
                        );
                    }
                    return res;
                }
            )
        );
    }

    /**
     * File part from multipart body.
     * @param headers Request headers
     * @param body Request body
     * @param temp Temp key to save the part
     * @return Part with the file
     */
    private CompletionStage<String> filePart(final Headers headers,
        final Publisher<ByteBuffer> body, final Key temp) {
        return Flowable.fromPublisher(
            new RqMultipart(headers, body).inspect(
                (part, inspector) -> {
                    if (new ContentDisposition(part.headers()).fieldName().equals("content")) {
                        inspector.accept(part);
                    } else {
                        inspector.ignore(part);
                    }
                    final CompletableFuture<Void> res = new CompletableFuture<>();
                    res.complete(null);
                    return res;
                }
            )
        ).doOnNext(
            part -> Logger.debug(this, "WS: multipart request body parsed, part %s found", part)
        ).flatMapSingle(
            part -> SingleInterop.fromFuture(
                this.storage.save(temp, new Content.From(part))
                    // @checkstyle LineLengthCheck (1 line)
                    .thenRun(() -> Logger.debug(this, "WS: content saved to temp file `%s`", temp.string()))
                    .thenApply(nothing -> new ContentDisposition(part.headers()).fileName())
            )
        ).toList().map(
            items -> {
                if (items.isEmpty()) {
                    throw new ArtipieException("content part was not found");
                }
                if (items.size() > 1) {
                    throw new ArtipieException("multiple content parts were found");
                }
                return items.get(0);
            }
        ).to(SingleInterop.get());
    }
}
