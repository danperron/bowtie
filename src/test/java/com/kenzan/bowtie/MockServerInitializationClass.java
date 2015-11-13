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

import org.mockserver.client.server.MockServerClient;
import org.mockserver.initialize.ExpectationInitializer;
import org.mockserver.matchers.Times;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;


/***
 * <p>
 * Initialization class for the mock server.  Used to create mock API's for unit testing.
 * </p> 
 */
public class MockServerInitializationClass implements ExpectationInitializer {

    @Override
    public void initializeExpectations(MockServerClient mockServerClient) {
        
        getUser(mockServerClient);
        
        getUserAddress(mockServerClient);
        
        getUsers(mockServerClient);
        
        getUsersWithSystem(mockServerClient);
        
        emailUser(mockServerClient);
        
        deleteUser(mockServerClient);
        
        mutateUser(mockServerClient);
        
        getRoleUsers(mockServerClient);
        
        getCachedUser(mockServerClient);
        
    }

    private void emailUser(MockServerClient mockServerClient) {

        mockServerClient.
        dumpToLog().
        when(
            HttpRequest.request()
            .withMethod("POST")
            .withCookie(Cookie.cookie("session", "0a1bc2a7-11ef-4781-9c06-8d9b42719797"))
            .withCookie(Cookie.cookie("username", "jdoe"))
            .withPath("/user/email")
            .withBody("{\"name\":\"John Doe\"}"),
            Times.unlimited()
        ).respond(
            HttpResponse.response()
            .withStatusCode(200)
            
        );        
    }

    private void getUsers(MockServerClient mockServerClient) {

        mockServerClient
        .dumpToLog()
        .when(
            HttpRequest.request()
            .withMethod("GET")
            .withPath("/user")
            .withHeader(Header.header("X-SESSION-ID", "020835c7-cf7e-4ba5-b117-4402e5d79079"))
            .withQueryStringParameter(Parameter.param("byUsername", "jdoe")),
            Times.unlimited()
        ).respond(
            HttpResponse.response()
            .withStatusCode(200)
            .withBody("{ \"users\" : [{ \"name\" : \"John Doe\" }] }")
        );
    }

    private void getUser(MockServerClient mockServerClient) {

        mockServerClient
        .dumpToLog()
        .when(
            HttpRequest.request()
            .withMethod("GET")
            .withHeader(Header.header("X-SESSION-ID", "55892d6d-77df-4617-b728-6f5de97f5752"))
            .withPath("/user/jdoe"),
            Times.unlimited()
        ).respond(
            HttpResponse.response()
            .withStatusCode(200)
            .withBody("{ \"name\" : \"John Doe\" }")
        );
    }
    
    
    private void getCachedUser(MockServerClient mockServerClient) {

        mockServerClient
        .dumpToLog()
        .when(
            HttpRequest.request()
            .withMethod("GET")
            .withHeader(Header.header("X-SESSION-ID", "55892d6d-77df-4617-b728-6f5de97f5752"))
            .withPath("/user/bdoe"),
            Times.unlimited()
        ).respond(
            HttpResponse.response()
            .withStatusCode(200)
            .withHeader(Header.header("Cache-Control","no-transform,public,max-age=300,s-maxage=900"))
            .withBody("{ \"name\" : \"Bob Doe\" }")
        );
    }
    
    
    
    private void getUserAddress(MockServerClient mockServerClient) {

        mockServerClient
        .dumpToLog()
        .when(
            HttpRequest.request()
            .withMethod("GET")
            .withHeader(Header.header("Cache-Control", "no-cache"))
            .withPath("/user/address/jdoe"),
            Times.unlimited()
        ).respond(
            HttpResponse.response()
            .withStatusCode(200)
            .withBody("{ \"address\" : \"1060 W Addison St, Chicago, IL 60613\" }")
        );
    }
    
    private void deleteUser(MockServerClient mockServerClient) {

        mockServerClient
        .dumpToLog()
        .when(
            HttpRequest.request()
            .withMethod("DELETE")
            .withPath("/user/jdoe"),
            Times.unlimited()
        ).respond(
            HttpResponse.response()
            .withCookie(Cookie.cookie("logout", "now"))
            .withStatusCode(200)
        );
    }

    private void mutateUser(MockServerClient mockServerClient) {
    
        mockServerClient.
        dumpToLog().
        when(
            HttpRequest.request()
            .withMethod("PUT")
            .withCookie(Cookie.cookie("session", "aa8a2e85-412e-46a2-889f-b2c133a59c89"))
            .withPath("/user")
            .withBody("{\"name\":\"John Doe\"}"),
            Times.unlimited()
        ).respond(
            HttpResponse.response()
            .withStatusCode(200)
            
        );        
    }

    private void getUsersWithSystem(MockServerClient mockServerClient) {
    
        mockServerClient
        .dumpToLog()
        .when(
            HttpRequest.request()
            .withMethod("GET")
            .withPath("/user")
            .withHeader(Header.header("X-SESSION-ID", "020835c7-cf7e-4ba5-b117-4402e5d79079"))
            .withQueryStringParameter(Parameter.param("byUsername", "bbelcher"))
            .withQueryStringParameter(Parameter.param("bySystem", "email")),
            Times.unlimited()
        ).respond(
            HttpResponse.response()
            .withStatusCode(200)
            .withBody("{ \"users\" : [{ \"name\" : \"John Doe\" },{ \"name\" : \"Bob Belcher\" }] }")
        );
    }

    private void getRoleUsers(MockServerClient mockServerClient) {
    
        mockServerClient
        .dumpToLog()
        .when(
            HttpRequest.request()
            .withMethod("GET")
            .withPath("/user/role")
            .withQueryStringParameter(Parameter.param("byRole", "vanessa")),
            Times.unlimited()
        ).respond(
            HttpResponse.response()
            .withStatusCode(200)
            .withBody("{ \"users\" : [] }")
        );
    }   
    
    
}