package com.horizon.policyforge.web;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The {@code instance} of every problem+json (NFR-04.1, contract C7): a fixed URN {@code urn:policyforge:problem:<slug>}
 * that is never derived from the request. Spring fills a missing instance with the request path, and a path can carry
 * anything a caller typed (an application ID, a policy number), so every writer of a problem sets one of these first.
 * The slug is the last segment of the problem {@code type} ({@code /problems/not-found} gives {@code not-found}). A
 * framework problem has the type {@code about:blank}, so its slug is the hyphenated, lower-case reason phrase of its
 * status ({@code method-not-allowed}). Slugs hold only the letters a-z and hyphens.
 */
final class ProblemInstances {

    static final String PREFIX = "urn:policyforge:problem:";

    private static final URI ABOUT_BLANK = URI.create("about:blank");
    private static final String FALLBACK_SLUG = "error";

    private ProblemInstances() {
    }

    /** The URN for a slug. */
    static URI of(String slug) {
        return URI.create(PREFIX + slug);
    }

    /**
     * The URN of a problem with this {@code type} and {@code status}: from the type's last segment, or from the
     * status's reason phrase when the type is absent or {@code about:blank}.
     */
    static URI of(URI type, HttpStatusCode status) {
        if (type == null || ABOUT_BLANK.equals(type)) {
            return ofStatus(status);
        }
        String path = type.getPath();
        String slug = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        return of(slugOf(slug));
    }

    private static URI ofStatus(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return of(known == null ? FALLBACK_SLUG : slugOf(known.getReasonPhrase()));
    }

    /** Lower case, with every run of characters other than a-z turned into one hyphen and none at either end. */
    private static String slugOf(String text) {
        String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? FALLBACK_SLUG : slug;
    }
}
