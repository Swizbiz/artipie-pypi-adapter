/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/python-adapter/LICENSE.txt
 */
package com.artipie.pypi.meta;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.llorllale.cactoos.matchers.MatcherOf;

/**
 * Test for {@link com.artipie.pypi.meta.PackageInfo.FromMetadata}.
 * @since 0.6
 * @checkstyle LeftCurlyCheck (500 lines)
 */
class PackageInfoFromMetadataTest {

    @ParameterizedTest
    @CsvSource({
        "my-project,0.3,Sample python project",
        "Another project,123-93,Another example project",
        "Very-very-difficult project,3,Calculates probability of the Earth being flat"
    })
    void readsMetadata(final String name, final String version, final String summary) {
        MatcherAssert.assertThat(
            new PackageInfo.FromMetadata(this.metadata(name, version, summary)),
            Matchers.allOf(
                new MatcherOf<>(info -> { return version.equals(info.version()); }),
                new MatcherOf<>(info -> { return name.equals(info.name()); }),
                new MatcherOf<>(info -> { return summary.equals(info.summary()); })
            )
        );
    }

    @Test
    void throwsExceptionIfNameNotFound() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new PackageInfo.FromMetadata("some text").name()
        );
    }

    @Test
    void throwsExceptionIfVersionNotFound() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new PackageInfo.FromMetadata("abc 123").version()
        );
    }

    @Test
    void throwsExceptionIfSummaryNotFound() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new PackageInfo.FromMetadata("some meta").version()
        );
    }

    private String metadata(final String name, final String version, final String summary) {
        return String.join(
            "\n",
            "Metadata-Version: 2.1",
            String.format("Name: %s", name),
            String.format("Version: %s", version),
            String.format("Summary: %s", summary),
            "Author: Someone",
            "Author-email: someone@example.com"
        );
    }

}
