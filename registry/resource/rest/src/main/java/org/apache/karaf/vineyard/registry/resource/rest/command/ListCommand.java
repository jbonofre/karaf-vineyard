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
package org.apache.karaf.vineyard.registry.resource.rest.command;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.table.ShellTable;
import org.apache.karaf.vineyard.common.ResourceRegistryService;
import org.apache.karaf.vineyard.common.RestResource;

@Service
@Command(
        scope = "vineyard",
        name = "resource-rest-list",
        description = "List of Rest Resources in the registry")
public class ListCommand implements Action {

    @Reference private ResourceRegistryService resourceRegistryService;

    @Override
    public Object execute() throws Exception {
        final ShellTable shellTable = new ShellTable();
        shellTable.column("ID");
        shellTable.column("Endpoint");
        shellTable.column("Method");
        shellTable.column("Path");
        shellTable.column("MediaType");
        shellTable.column("Description");
        for (Object item : resourceRegistryService.list()) {
            RestResource resource = (RestResource) item;
            shellTable
                    .addRow()
                    .addContent(
                            resource.getId(),
                            resource.getEndpoint(),
                            resource.getMethod(),
                            resource.getPath(),
                            resource.getMediaType(),
                            resource.getDescription());
        }
        shellTable.print(System.out);
        return null;
    }
}
