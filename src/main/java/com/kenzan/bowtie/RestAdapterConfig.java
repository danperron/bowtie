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

import com.google.common.collect.ImmutableList;
import com.kenzan.bowtie.annotation.Encoding;
import com.kenzan.bowtie.cache.RestCache;
import com.kenzan.bowtie.serializer.JacksonMessageSerializer;
import com.kenzan.bowtie.serializer.MessageSerializer;
import com.sun.jersey.api.client.filter.ClientFilter;
import java.util.Collection;

/***
 * <p>
 * Configuration object and builder for creating clients using the {@link RestAdapter}
 * </p>
 */
public class RestAdapterConfig {

    private final MessageSerializer messageSerializer;
    private final Encoding encoding;
    private final RestCache restCache;
    private final Collection<ClientFilter> filters;

    private RestAdapterConfig(final Builder builder) {
        this.messageSerializer = builder.messageSerializer;
        this.encoding = builder.encoding;
        this.restCache = builder.restCache;
        this.filters = builder.filtersBuilder.build();
    }

    public MessageSerializer getMessageSerializer() {
        return this.messageSerializer;
    }

    public Encoding getEncoding() {
        return this.encoding;
    }

    public RestCache getRestCache() {
        return this.restCache;
    }
    
    public Collection<ClientFilter> getFilters(){
        return this.filters;
    }

    public static RestAdapterConfig createDefault(){
        return new Builder()
        .withMessageSerializer(new JacksonMessageSerializer())
        .build();
    }

    public static Builder custom(){
        return new Builder();
    }

    public static class Builder {

        private MessageSerializer messageSerializer = null;
        private Encoding encoding = Encoding.none;
        private RestCache restCache = null;
        private ImmutableList.Builder<ClientFilter> filtersBuilder = ImmutableList.builder();

        private Builder() {

        }

        public Builder withMessageSerializer(MessageSerializer messageSerializer) {
            this.messageSerializer = messageSerializer;
            return this;
        }

        public Builder withEncoding(Encoding encoding) {
            this.encoding = encoding;
            return this;
        }

        public Builder withRestCache(RestCache restCache) {
            this.restCache = restCache;
            return this;
        }
        
        public Builder withFilters(final ClientFilter... filters){
            this.filtersBuilder.add(filters);
            return this;
        }

        public RestAdapterConfig build() {
            final RestAdapterConfig restAdapterConfig = new RestAdapterConfig(this);
            return restAdapterConfig;
        }
    }
}
