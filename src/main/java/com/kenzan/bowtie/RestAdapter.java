/**
 * Copyright (C) 2015 Kenzan (labs@kenzan.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kenzan.bowtie;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.kenzan.bowtie.annotation.Encoding;
import com.kenzan.bowtie.http.JerseyInvocationHandler;
import com.kenzan.bowtie.log.LoggerFilter;
import com.netflix.client.ClientFactory;
import com.netflix.niws.client.http.RestClient;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;

/***
 * <p>
 * Main class for instantiating client instances.
 * </p>
 * 
 * Use the default config for most cases (REST/JSON):
 * 
 * <pre>
 * final RestAdapter restAdapter = RestAdapter.getNamedAdapter(&quot;sample-client&quot;);
 * fakeClient = restAdapter.create(FakeClient.class);
 * </pre>
 * 
 * Or use a custom config:
 * 
 * <pre>
 * final RestAdapter restAdapter2 = RestAdapter.getNamedAdapter(
 *         &quot;sample-client&quot;,
 *         RestAdapterConfig.custom()
 *                 .withMessageSerializer(new JacksonMessageSerializer())
 *                 .withEncoding(Encoding.gzip).build());
 * 
 * fakeClient2 = restAdapter2.create(FakeClient.class);
 * </pre>
 *
 */
public class RestAdapter {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(RestAdapter.class);

    private String namedClient;
    private RestAdapterConfig restAdapterConfig;

    private RestAdapter(String namedClient, RestAdapterConfig restAdapterConfig) {
        this.namedClient = namedClient;
        this.restAdapterConfig = restAdapterConfig;
    }

    public static RestAdapter getNamedAdapter(String namedAdapter) {
        return new RestAdapter(namedAdapter, RestAdapterConfig.createDefault());
    }

    public static RestAdapter getNamedAdapter(String namedAdapter,
            RestAdapterConfig restAdapterConfig) {
        return new RestAdapter(namedAdapter, restAdapterConfig);
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> clientClass) {

        Preconditions.checkNotNull(this.namedClient, "NamedClient required");
        Preconditions.checkNotNull(
                this.restAdapterConfig.getMessageSerializer(),
                "MessageSerializer required");

        LOGGER.info("Using NamedClient {}", this.namedClient);
        LOGGER.info("Using MessageSerializer {}",
                this.restAdapterConfig.getMessageSerializer());

        final RestClient restClient = (RestClient) ClientFactory
                .getNamedClient(namedClient);
        restClient.getJerseyClient().addFilter(new LoggerFilter());

        if (this.restAdapterConfig.getEncoding() == Encoding.gzip) {
            restClient.getJerseyClient().addFilter(
                    new GZIPContentEncodingFilter());
        }
        
        this.restAdapterConfig.getFilters()
                .forEach((f) -> restClient.getJerseyClient().addFilter(f));
        
        InvocationHandler invocationHandler = new JerseyInvocationHandler(
                restClient, this.restAdapterConfig);
        Object proxyInstance = Proxy.newProxyInstance(
                ClassLoader.getSystemClassLoader(),
                new Class<?>[] { clientClass }, invocationHandler);
        return (T) proxyInstance;
    }
}
