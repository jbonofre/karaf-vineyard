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
package org.apache.karaf.vineyard.registry.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

import javax.sql.DataSource;

import org.apache.karaf.vineyard.common.Environment;
import org.apache.karaf.vineyard.common.Maintainer;
import org.apache.karaf.vineyard.common.Service;
import org.apache.karaf.vineyard.registry.api.RegistryService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the service processing, storing the services into a database.
 */
@Component(
        name = "org.apache.karaf.vineyard.registry.storage.sqlService",
        immediate = true
)
public class SqlRegistryService implements RegistryService {

    private final static Logger LOGGER = LoggerFactory.getLogger(SqlRegistryService.class);

    //TODO private final static String createTableQueryGenericTemplate = "";
    //TODO private final static String createTableQueryMySQLTemplate = "";
    
    private final static String[] createTableQueryDerbyTemplate = new String[] 
            {
                    "CREATE SCHEMA VINEYARD",
                    
                    "CREATE TABLE VINEYARD.ENVIRONMENT(id SMALLINT NOT NULL GENERATED BY DEFAULT AS IDENTITY " 
                            + " CONSTRAINT ENVIRONMENT_PK PRIMARY KEY, name VARCHAR(200) NOT NULL, description VARCHAR(8192), "
                            + " scope VARCHAR(200))",
                            
                    "CREATE TABLE VINEYARD.MAINTAINER(name VARCHAR(200) CONSTRAINT MAINTAINER_PK PRIMARY KEY, "
                            + " email VARCHAR(200), team VARCHAR(200))",
                            
                    "CREATE TABLE VINEYARD.DATAFORMAT(id SMALLINT NOT NULL GENERATED BY DEFAULT AS IDENTITY "
                            + " CONSTRAINT DATAFORMAT_PK PRIMARY KEY, name VARCHAR(200) NOT NULL, sample VARCHAR(8192), " 
                            + " dataschema VARCHAR(8192))",
                            
                    "CREATE TABLE VINEYARD.ENDPOINT(location VARCHAR(200) NOT NULL CONSTRAINT ENDPOINT_PK PRIMARY KEY, "
                            + " eptinput SMALLINT, eptoutput SMALLINT, "
                            + " CONSTRAINT ENDPT_INPUT_DTFM_FK FOREIGN KEY (eptinput) REFERENCES VINEYARD.DATAFORMAT (id),"
                            + " CONSTRAINT ENDPT_OUTPUT_DTFM_FK FOREIGN KEY (eptoutput) REFERENCES VINEYARD.DATAFORMAT (id))",
                            
                    "CREATE TABLE VINEYARD.SERVICE(id SMALLINT NOT NULL GENERATED BY DEFAULT AS IDENTITY "
                            + " CONSTRAINT SERVICE_PK PRIMARY KEY, name VARCHAR(200) NOT NULL, description VARCHAR(8192))",
                            
                    "CREATE TABLE VINEYARD.X_ENV_MNT(id_environment SMALLINT, name_maintainer VARCHAR(200), "
                            + " role VARCHAR(200), "
                            + " CONSTRAINT X_ENV_MNT_PK PRIMARY KEY (id_environment, name_maintainer), "
                            + " CONSTRAINT X_ENV_MNT_ENV_FK FOREIGN KEY (id_environment) REFERENCES VINEYARD.ENVIRONMENT (id), "
                            + " CONSTRAINT X_ENV_MNT_MNT_FK FOREIGN KEY (name_maintainer) REFERENCES VINEYARD.MAINTAINER (name))",
                            
                    "CREATE TABLE VINEYARD.X_SRV_ENV(id_service SMALLINT, id_environment SMALLINT, "
                            + " state VARCHAR(200), version VARCHAR(50), endpoint VARCHAR(200), gateway VARCHAR(200), "
                            + " CONSTRAINT X_SRV_ENV_PK PRIMARY KEY (id_service, id_environment),"
                            + " CONSTRAINT X_SRV_ENV_SRV_FK FOREIGN KEY (id_service) REFERENCES VINEYARD.SERVICE (id), "
                            + " CONSTRAINT X_SRV_ENV_ENV_FK FOREIGN KEY (id_environment) REFERENCES VINEYARD.ENVIRONMENT (id), "
                            + " CONSTRAINT X_SRV_ENV_ENDPT_FK FOREIGN KEY (endpoint) REFERENCES VINEYARD.ENDPOINT (location), "
                            + " CONSTRAINT X_SRV_ENV_GTW_FK FOREIGN KEY (gateway) REFERENCES VINEYARD.ENDPOINT (location))",
                            
                    "CREATE TABLE VINEYARD.X_SRV_ENV_META(id_service SMALLINT, id_environment SMALLINT, "
                            + " metakey VARCHAR(200), metavalue VARCHAR(200), "
                            + " CONSTRAINT X_SRV_ENV_META_PK PRIMARY KEY (id_service, id_environment),"
                            + " CONSTRAINT X_SRV_ENV_META_FK FOREIGN KEY (id_service, id_environment) "
                            + " REFERENCES VINEYARD.X_SRV_ENV (id_service, id_environment))"
            };
    
    /** Select queries */
    private final static String selectEnvironmentSql = 
            "select id, name, description, scope "
            + "from VINEYARD.ENVIRONMENT";
    private final static String selectMaintainerSql = 
            "select name, email, team "
            + "from VINEYARD.MAINTAINER";
    private final static String selectDataformatSql = 
            "select id, name, sample, dataschema "
            + "from VINEYARD.DATAFORMAT";
    private final static String selectEndpointSql = 
            "select location, eptinput, eptoutput "
            + "from VINEYARD.ENDPOINT";
    private final static String selectServiceSql = 
            "select id, name, description "
            + "from VINEYARD.SERVICE";
    private final static String selectMaintainerForEnvironmentSql = 
            "select id_environment, name_maintainer, role "
            + "from VINEYARD.X_ENV_MNT";
    private final static String selectEnvironmentForServiceSql = 
            "select id_service, id_environment, state, version, endpoint, gateway "
            + "from VINEYARD.X_SRV_ENV";
    private final static String selectMetadataEnvironmentForServiceSql = 
            "select id_service, id_environment, metakay, metavalue "
            + "from VINEYARD.X_SRV_ENV_META";
    
    /** Insert queries */
    private final static String insertEnvironmentSql = 
            "insert into VINEYARD.ENVIRONMENT "
            + "(name, description, scope) "
            + "values (?, ?, ?)";
    private final static String insertMaintainerSql = 
            "insert into VINEYARD.MAINTAINER "
            + "(name, email, team) "
            + "values (?, ?, ?, ?)";
    private final static String insertDataformatSql = 
            "insert into VINEYARD.DATAFORMAT "
            + "(name, sample, dataschema) "
            + "values (?, ?, ?)";
    private final static String insertEndpointSql = 
            "insert into VINEYARD.ENDPOINT "
            + "(location, eptinput, eptoutput) "
            + "values (?, ?, ?, ?)";
    private final static String insertServiceSql = 
            "insert into VINEYARD.SERVICE "
            + "(name, description) "
            + "values (?, ?)";
    private final static String insertMaintainerForEnvironmentSql = 
            "insert into VINEYARD.X_ENV_MNT "
            + "(id_environment, name_maintainer) values (?, ?)";
    private final static String insertEnvironmentForServiceSql = 
            "insert into VINEYARD.X_SRV_ENV "
            + "(id_service, id_environment, state, version, endpoint, gateway) "
            + "values (?, ?, ?, ?, ?, ?)";
    private final static String insertMetadataEnvironmentForServiceSql = 
            "insert into VINEYARD.X_SRV_ENV_META "
            + "(id_service, id_environment, metakay, metavalue) "
            + "values (?, ?, ?, ?)";
    
    /** Update queries */
    private final static String updateEnvironmentSql = 
            "update VINEYARD.ENVIRONMENT "
            + "set name = ?, description = ?, scope = ? "
            + "where id = ?";
    private final static String updateMaintainerSql = 
            "update VINEYARD.MAINTAINER "
            + "set email = ?, team = ? "
            + "where name = ?";
    private final static String updateDataformatSql = 
            "update VINEYARD.DATAFORMAT "
            + "set name = ?, sample = ?, dataschema = ? "
            + "where id = ?";
    private final static String updateEndpointSql = 
            "update VINEYARD.ENDPOINT "
            + "set eptinput = ?, eptoutput = ? "
            + "where location = ?";
    private final static String updateServiceSql = 
            "update VINEYARD.SERVICE "
            + "set name = ?, description = ? "
            + "where id = ?";
    private final static String updateEnvironmentForServiceSql = 
            "update VINEYARD.X_SRV_ENV "
            + "set state = ?, version = ?, endpoint = ?, gateway = ? "
            + "where id_service = ? and id_environment = ?";
    private final static String updateMetadataEnvironmentForServiceSql = 
            "update VINEYARD.X_SRV_ENV_META "
            + "set metakey = ?, metavalue = ? "
            + "where id_service = ? and id_environment = ?";
    
    /** Delete queries */
    private final static String deleteEnvironmentSql = 
            "delete from VINEYARD.ENVIRONMENT "
            + "where id = ?";
    private final static String deleteMaintainerSql = 
            "delete from VINEYARD.MAINTAINER "
            + "where name = ?";
    private final static String deleteDataformatSql = 
            "delete from VINEYARD.DATAFORMAT "
            + "where id = ?";
    private final static String deleteEndpointSql = 
            "delete from VINEYARD.ENDPOINT "
            + "where location = ?";
    private final static String deleteServiceSql = 
            "delete from VINEYARD.SERVICE "
            + "where id = ?";
    private final static String deleteMaintainerForEnvironmentSql = 
            "delete from VINEYARD.X_ENV_MNT ";
    private final static String deleteEnvironmentForServiceSql = 
            "delete from VINEYARD.X_SRV_ENV ";
    private final static String deleteMetadataEnvironmentForServiceSql = 
            "delete from VINEYARD.X_SRV_ENV_META ";
            
    @Reference(target = "(osgi.jndi.service.name=jdbc/vineyard)")
    private DataSource dataSource;

    private String dialect;

    @Activate
    public void activate(ComponentContext context) {
        open(context.getProperties());
    }
    
    public void open(Dictionary<String, Object> config) {
        this.dialect = getValue(config, "dialect", "derby");
        LOGGER.debug("Dialect {} ", this.dialect);
        LOGGER.debug("Datasource {} ", this.dataSource);
        try (Connection connection = dataSource.getConnection()) {
            createTables(connection);
        } catch (Exception e) {
            LOGGER.error("Error creating table ", e);
        }
    }

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String) config.get(key);
        return (value != null) ? value : defaultValue;
    }

    private void createTables(Connection connection) {

        DatabaseMetaData dbm;
        ResultSet tables;
        
        try {
            dbm = connection.getMetaData();
            
            tables = dbm.getTables(null, "VINEYARD", "SERVICE", null);
            if (!tables.next()) {
                LOGGER.info("Tables does not exist");
                // Tables does not exist so we create all the tables
                String[] createTemplate = null;
                if (dialect.equalsIgnoreCase("mysql")) {
                    //TODO createTableQueryMySQLTemplate;
                } else if (dialect.equalsIgnoreCase("derby")) {
                    createTemplate = createTableQueryDerbyTemplate;
                } else {
                    //TODO createTableQueryGenericTemplate;
                }
                try (Statement createStatement = connection.createStatement()) {
                    for (int cpt = 0; cpt < createTemplate.length; cpt++) {
                        createStatement.addBatch(createTemplate[cpt]);
                    }
                    if (createStatement.executeBatch().length == 0) {
                        throw new SQLException("No table has been created !");
                    }
                    LOGGER.info("Schema and tables has been created");
                } catch (SQLException exception) {
                    LOGGER.error("Can't create tables", exception);
                }
            } else {
                LOGGER.info("Tables already exist");
            }
        } catch (SQLException exception) {
            LOGGER.error("Can't verify tables existence", exception);
        }
    }

    @Override
    public void add(Service service) {
        try (Connection connection = dataSource.getConnection()) {
            
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            try (PreparedStatement insertStatement = 
                    connection.prepareStatement(insertServiceSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    // set values
                    insertStatement.setString(1, service.name);
                    insertStatement.setString(2, service.description);
                    insertStatement.executeUpdate();
                    // TODO insert extra content
                    
                    int newId = 0;
                    ResultSet rs = insertStatement.getGeneratedKeys();
                    
                    if (rs.next()) {
                        newId = rs.getInt(1);
                    }
                    
                    connection.commit();
                    
                    service.id = String.valueOf(newId);
                    LOGGER.debug("Service created with id = {}", newId);
            
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't insert service with name {}", service.name, exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
    }

    @Override
    public void delete(Service service) {
        delete(service.id);
    }

    @Override
    public void delete(String id) {
        try (Connection connection = dataSource.getConnection()) {
            
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            try (PreparedStatement deleteStatement = 
                    connection.prepareStatement(deleteServiceSql)) {
                    // where values
                    deleteStatement.setString(1, id);
                    deleteStatement.executeUpdate();
                    // TODO delete extra content
                    connection.commit();
                    LOGGER.debug("Service deleted with id = {}", id);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't delete service with name {}", id, exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
    }

    @Override
    public void update(Service service) {
        try (Connection connection = dataSource.getConnection()) {
            
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            try (PreparedStatement updateStatement = 
                    connection.prepareStatement(updateServiceSql)) {
                    // set values
                    updateStatement.setString(1, service.name);
                    updateStatement.setString(2, service.description);
                    // where values
                    updateStatement.setString(3, service.id);
                    updateStatement.executeUpdate();
                    // TODO update extra content
                    connection.commit();
                    LOGGER.debug("Service updated with id = {}", service.id);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't udpate service with name {}", service.name, exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
    }

    @Override
    public Service get(String id) {
        
        try (Connection connection = dataSource.getConnection()) {
            
            String sqlQuery = selectServiceSql + " where id = ?";
            
            try (PreparedStatement selectStatement = connection.prepareStatement(sqlQuery)) {
                    selectStatement.setString(1, id);
                    ResultSet rs = selectStatement.executeQuery();
                    
                    if (rs.next()) {
                        Service service = new Service();
                        service.id = rs.getString("id");
                        service.name = rs.getString("name");
                        service.description = rs.getString("description");
                        // TODO get extra content
                        return service;
                    }
            
            } catch (SQLException exception) {
                LOGGER.error("Can't find service with id {}", id, exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
        return null;
    }

    @Override
    public List<Service> getAllServices() {
        
        List<Service> services = new ArrayList<>();
        
        try (Connection connection = dataSource.getConnection()) {
            
            try (PreparedStatement selectStatement = connection.prepareStatement(selectServiceSql)) {
                    ResultSet rs = selectStatement.executeQuery();
                    
                    while (rs.next()) {
                        Service service = new Service();
                        service.id = rs.getString("id");
                        service.name = rs.getString("name");
                        service.description = rs.getString("description");
                        services.add(service);
                        // TODO get extra content
                    }
            
            } catch (SQLException exception) {
                LOGGER.error("Can't retreive the services", exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
        return services;
    }

    @Override
    public void addEnvironment(Environment environment) {
        try (Connection connection = dataSource.getConnection()) {
            
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            try (PreparedStatement insertStatement = 
                    connection.prepareStatement(insertEnvironmentSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    // set values
                    insertStatement.setString(1, environment.name);
                    insertStatement.setString(2, environment.description);
                    insertStatement.setString(3, environment.scope);
                    insertStatement.executeUpdate();
                    // TODO insert extra content
                    
                    int newId = 0;
                    ResultSet rs = insertStatement.getGeneratedKeys();
                    
                    if (rs.next()) {
                        newId = rs.getInt(1);
                    }
                    
                    connection.commit();
                    
                    environment.setId(String.valueOf(newId));
                    LOGGER.debug("Environment created with id = {}", newId);
            
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't insert environment with name {}", environment.name, exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
    }

    @Override
    public void deleteEnvironment(Environment environment) {
        deleteEnvironment(environment.getId());
    }

    @Override
    public void deleteEnvironment(String id) {
        try (Connection connection = dataSource.getConnection()) {
            
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            String sqlQuery = deleteMetadataEnvironmentForServiceSql + 
                    "where id_environment = ?";
            
            try (PreparedStatement deleteStatement = 
                    connection.prepareStatement(sqlQuery)) {
                    // where values
                    deleteStatement.setString(1, id);
                    deleteStatement.executeUpdate();
                    
                    LOGGER.debug("Environment deleted with id = {}", id);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't delete environment with name {}", id, exception);
                throw exception;
            }
            
            sqlQuery = deleteEnvironmentForServiceSql + 
                    "where id_environment = ?";
            
            try (PreparedStatement deleteStatement = 
                    connection.prepareStatement(sqlQuery)) {
                    // where values
                    deleteStatement.setString(1, id);
                    deleteStatement.executeUpdate();
                    
                    LOGGER.debug("Environment deleted with id = {}", id);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't delete environment with name {}", id, exception);
                throw exception;
            }
            
            try (PreparedStatement deleteStatement = 
                    connection.prepareStatement(deleteEnvironmentSql)) {
                    // where values
                    deleteStatement.setString(1, id);
                    deleteStatement.executeUpdate();
                    
                    LOGGER.debug("Environment deleted with id = {}", id);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't delete environment with name {}", id, exception);
                throw exception;
            }
            
            connection.commit();
            
        } catch (Exception exception) {
            LOGGER.error("Error when deleting environment", exception);
        }
    }

    @Override
    public void updateEnvironment(Environment environment) {
        try (Connection connection = dataSource.getConnection()) {
            
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            try (PreparedStatement updateStatement = 
                    connection.prepareStatement(updateEnvironmentSql)) {
                    // set values
                    updateStatement.setString(1, environment.name);
                    updateStatement.setString(2, environment.description);
                    updateStatement.setString(3, environment.scope);
                    // where values
                    updateStatement.setString(4, environment.getId());
                    updateStatement.executeUpdate();
                    connection.commit();
                    LOGGER.debug("Environment updated with id = {}", environment.getId());
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't udpate environment with name {}", environment.name, exception);
            }
            
            //TODO update extra content
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
    }

    @Override
    public Environment getEnvironment(String id) {
        try (Connection connection = dataSource.getConnection()) {
            
            String sqlQuery = selectEnvironmentSql + " where id = ?";
            
            try (PreparedStatement selectStatement = connection.prepareStatement(sqlQuery)) {
                    selectStatement.setString(1, id);
                    ResultSet rs = selectStatement.executeQuery();
                    
                    if (rs.next()) {
                        Environment environment = new Environment();
                        environment.name = rs.getString("name");
                        environment.description = rs.getString("description");
                        environment.scope = rs.getString("scope");
                        // TODO get extra content
                        return environment;
                    }
            
            } catch (SQLException exception) {
                LOGGER.error("Can't find environment with id {}", id, exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
        return null;
    }

    @Override
    public List<Environment> getAllEnvironments() {
        List<Environment> environments = new ArrayList<>();
        
        try (Connection connection = dataSource.getConnection()) {
            
            try (PreparedStatement selectStatement = connection.prepareStatement(selectEnvironmentSql)) {
                    ResultSet rs = selectStatement.executeQuery();
                    
                    while (rs.next()) {
                        Environment environment = new Environment();
                        environment.name = rs.getString("name");
                        environment.description = rs.getString("description");
                        environments.add(environment);
                        // TODO get extra content
                    }
            
            } catch (SQLException exception) {
                LOGGER.error("Can't retreive the environments", exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
        return environments;
    }

    @Override
    public void addMaintainer(Maintainer maintainer) {
        try (Connection connection = dataSource.getConnection()) {
            
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            try (PreparedStatement insertStatement = 
                    connection.prepareStatement(insertMaintainerSql)) {
                    // set values
                    insertStatement.setString(1, maintainer.name);
                    insertStatement.setString(2, maintainer.email);
                    insertStatement.setString(3, maintainer.team);
                    insertStatement.executeUpdate();
                    
                    connection.commit();
                    LOGGER.debug("Maintainer created with name = {}", maintainer.name);
            
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't insert maintainer with name {}", maintainer.name, exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
    }

    @Override
    public void deleteMaintainer(Maintainer maintainer) {
        deleteMaintainer(maintainer.name);
    }

    @Override
    public void deleteMaintainer(String name) {
        try (Connection connection = dataSource.getConnection()) {
            
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            String sqlQuery = deleteMaintainerForEnvironmentSql + 
                    "where name_maintainer = ?";
            
            try (PreparedStatement deleteStatement = 
                    connection.prepareStatement(sqlQuery)) {
                    // where values
                    deleteStatement.setString(1, name);
                    deleteStatement.executeUpdate();
                    
                    LOGGER.debug("Maintainer deleted with name = {}", name);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't delete maintainer with name {}", name, exception);
                throw exception;
            }
            
            try (PreparedStatement deleteStatement = 
                    connection.prepareStatement(deleteMaintainerSql)) {
                    // where values
                    deleteStatement.setString(1, name);
                    deleteStatement.executeUpdate();
                    
                    LOGGER.debug("Maintainer deleted with name = {}", name);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't delete maintainer with name {}", name, exception);
                throw exception;
            }
            
            connection.commit();
            
        } catch (Exception exception) {
            LOGGER.error("Error when deleting maintainer", exception);
        }
    }

    @Override
    public void updateMaintainer(Maintainer maintainer) {
        try (Connection connection = dataSource.getConnection()) {
            
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            
            try (PreparedStatement updateStatement = 
                    connection.prepareStatement(updateMaintainerSql)) {
                    // set values
                    updateStatement.setString(1, maintainer.email);
                    updateStatement.setString(2, maintainer.team);
                    // where values
                    updateStatement.setString(3, maintainer.name);
                    updateStatement.executeUpdate();
                    connection.commit();
                    LOGGER.debug("Maintainer updated with name = {}", maintainer.name);
            } catch (SQLException exception) {
                connection.rollback();
                LOGGER.error("Can't udpate maintainer with name {}", maintainer.name, exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
    }

    @Override
    public Maintainer getMaintainer(String name) {
        try (Connection connection = dataSource.getConnection()) {
            
            String sqlQuery = selectMaintainerSql + " where name = ?";
            
            try (PreparedStatement selectStatement = connection.prepareStatement(sqlQuery)) {
                    selectStatement.setString(1, name);
                    ResultSet rs = selectStatement.executeQuery();
                    
                    if (rs.next()) {
                        Maintainer maintainer = new Maintainer();
                        maintainer.email = rs.getString("email");
                        maintainer.team = rs.getString("team");
                        return maintainer;
                    }
            
            } catch (SQLException exception) {
                LOGGER.error("Can't find maintainer with name {}", name, exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
        return null;
    }

    @Override
    public List<Maintainer> getAllMaintainers() {
        List<Maintainer> maintainers = new ArrayList<>();
        
        try (Connection connection = dataSource.getConnection()) {
            
            try (PreparedStatement selectStatement = connection.prepareStatement(selectMaintainerSql)) {
                    ResultSet rs = selectStatement.executeQuery();
                    
                    while (rs.next()) {
                        Maintainer maintainer = new Maintainer();
                        maintainer.name = rs.getString("name");
                        maintainer.email = rs.getString("email");
                        maintainer.team = rs.getString("team");
                        maintainers.add(maintainer);
                    }
            
            } catch (SQLException exception) {
                LOGGER.error("Can't retreive the maintainers", exception);
            }
            
        } catch (Exception exception) {
            LOGGER.error("Error getting connection ", exception);
        }
        return maintainers;
    }
}
