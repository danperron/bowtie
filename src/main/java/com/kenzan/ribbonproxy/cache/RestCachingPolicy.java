/*
 * Copyright 2014, Charter Communications, All rights reserved.
 */
package com.kenzan.ribbonproxy.cache;

import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.client.http.HttpResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class RestCachingPolicy {

    private final List<Integer> CACHEABLE_STATUSES = Arrays.asList(
            200,        // OK 
            203,        // Non-Authoritative Information
            300,        // Multiple Choices
            301,        // Moved Permanently
            410         // Gone
    );

    private final List<String> UNACCEPTABLE_CACHE_CONTROL_VALUE = Arrays.asList(
             "no-store",
             "no-cache",
             "private"
    );

    private final List<String> ACCEPTABLE_CACHE_CONTROL_VALUE = Arrays.asList(
             "max-age",
             "must-revalidate",
             "proxy-revalidate",
             "public"
     );

    public boolean isCachable(HttpRequest httpRequest, HttpResponse httpResponse) {

        boolean acceptableToCache = false;

        // Request must be a GET
        if (httpRequest.getVerb() != Verb.GET) {
            return false;
        }

        // Acceptable to cache if one of the following headers
        if (!CACHEABLE_STATUSES.contains(httpResponse.getStatus())) {
            acceptableToCache = true;
        }

        final Optional<String> cacheControl = 
                Optional.ofNullable(httpResponse.getHeaders().get("Cache-Control"))
                    .map(list -> list.stream().findFirst().orElse(""));

        if (cacheControl.isPresent()) {
            // Explicitly cannot cache if the response has an unacceptable cache-control header
            if (UNACCEPTABLE_CACHE_CONTROL_VALUE.stream().anyMatch(a -> cacheControl.get().contains(a))) {
                return false;
            }
    
            // Explicitly can cache the response has an acceptable cache-control header
            if(ACCEPTABLE_CACHE_CONTROL_VALUE.stream().anyMatch(a -> cacheControl.get().contains(a))) {
                return true;
            }
        }

     // Explicitly can cache if the response has Expires header
        if (Optional.ofNullable(httpResponse.getHeaders().get("Expires"))
                .map(list -> list.stream().findFirst().isPresent()).isPresent()) {
            return true;
        }


        return acceptableToCache;
    }
}
