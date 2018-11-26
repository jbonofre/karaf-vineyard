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

import java.util.Collection;
import org.apache.karaf.vineyard.common.API;
import org.apache.karaf.vineyard.common.ApiGatewayService;
import org.apache.karaf.vineyard.common.Resource;
import org.apache.karaf.vineyard.common.ResourceGatewayService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component(service = ApiGatewayService.class, immediate = true)
public class ApiGatewayServiceImpl implements ApiGatewayService {

    private BundleContext bundleContext;

    @Activate
    public void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public void publish(API api) throws Exception {
        for (Resource resource : api.getResources()) {
            // looking for the gateway service corresponding to the resource type
            Collection<ServiceReference<ResourceGatewayService>> references =
                    bundleContext.getServiceReferences(
                            ResourceGatewayService.class, "(type=" + resource.getType() + ")");
            // TODO retrieve the concrete resource services and publish resources
        }
    }

    @Override
    public void delete(String apiId) throws Exception {}
}
