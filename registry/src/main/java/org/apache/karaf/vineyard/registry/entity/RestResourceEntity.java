/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.vineyard.registry.entity;

import java.io.Serializable;
import java.util.Collection;
import javax.persistence.*;

@Entity
@Table(name = "REST_RESOURCE", schema = "VINEYARD")
public class RestResourceEntity implements Serializable {

    @Id private String id;

    private String description;

    private String path;

    private String method;

    private String version;

    private String accept;

    private String mediaType;

    @ManyToMany
    @JoinTable(
            name = "REST_RES_POLICY",
            joinColumns = @JoinColumn(name = "REST_RES_ID"),
            inverseJoinColumns = @JoinColumn(name = "POLICY_ID"))
    private Collection<PolicyEntity> policies;

    /** The response of the Resource, it could be static */
    private String response;

    /** The endpoint consumed by the Resource */
    private String endpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    private ApiEntity api;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ApiEntity getApi() {
        return api;
    }

    public void setApi(ApiEntity api) {
        this.api = api;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAccept() {
        return accept;
    }

    public void setAccept(String accept) {
        this.accept = accept;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Collection<PolicyEntity> getPolicies() {
        return policies;
    }

    public void setPolicies(Collection<PolicyEntity> policies) {
        this.policies = policies;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}
