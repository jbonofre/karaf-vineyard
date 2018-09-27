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
package org.apache.karaf.vineyard.gateway.api;

import org.apache.karaf.vineyard.common.API;

import java.util.Map;

/**
 * Describe gateway service.
 */
public interface GatewayService {

    /**
     * Register a API into the gateway.
     *
     * @param api The registration to perform.
     */
    void register(API api) throws Exception;

    /**
     * Disable a API resource in the gateway. The service is not removed and the gateway
     * keeps the metrics for this service, however the service is not accessible for
     * the gateway clients.
     *
     * @param id The registration id.
     */
    void disable(String id) throws Exception;

    /**
     * Enable a API resource in the gateway.
     *
     * @param id The registration id.
     */
    void enable(String id) throws Exception;

    /**
     * Remove a API from the gateway. All data about the service, including metrics are
     * deleted as well.
     *
     * @param id The registration id.
     */
    void remove(String id) throws Exception;

    /**
     * Get the status of the given API resource.
     *
     * @param id The API resource id.
     * @return The current status.
     */
    String status(String id);

    /**
     * Retrieve the metrics for a given registration (as number of call, average response time,
     * ...).
     *
     * @param id The registration id.
     * @return THe service metrics.
     */
    Map<String, Object> metrics(String id);

    /**
     * Add processing on the service, before calling the actual backend endpoint.
     *
     * @param id The registration id.
     * @param processing The processing definition.
     */
    void addProcessing(String id, Object processing);

}
