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
package org.apache.karaf.vineyard.registry.commands;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;

/**
 * Command to delete an api in the registry.
 */
@Command(scope = "vineyard", name = "api-delete", description = "Delete an api in the Registry")
@Service
public class ApiDeleteCommand extends VineyardRegistryCommandSupport {
    
    @Argument(index = 0, name = "id", description = "Id of the api", required = true, multiValued = false)
    private String id;
    
    protected Object doExecute() throws Exception {
        
        getRegistryService().deleteApi(id);
        return null;
    }
}
