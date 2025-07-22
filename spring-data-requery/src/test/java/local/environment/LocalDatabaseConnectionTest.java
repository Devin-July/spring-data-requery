/*
 * Copyright 2018 Coupang Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package local.environment;

import com.zaxxer.hikari.HikariDataSource;
import io.requery.meta.EntityModel;
import io.requery.sql.TableCreationMode;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.requery.configs.AbstractRequeryConfiguration;
import org.springframework.data.requery.core.RequeryOperations;
import org.springframework.data.requery.domain.Models;
import org.springframework.data.requery.domain.sample.User;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration
public class LocalDatabaseConnectionTest {

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration extends AbstractRequeryConfiguration {

        @Override
        public EntityModel getEntityModel() {
            return Models.DEFAULT;
        }

        @Override
        public TableCreationMode getTableCreationMode() {
            return TableCreationMode.CREATE_NOT_EXISTS;
        }

        @Bean
        public DataSource dataSource() {
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setDriverClassName("org.h2.Driver");
            config.setJdbcUrl("jdbc:h2:mem:localtest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false");
            config.setUsername("sa");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            return new HikariDataSource(config);
        }
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RequeryOperations operations;

    @Test
    public void testH2DatabaseConnection() throws SQLException {
        assertThat(dataSource).isNotNull();
        
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection).isNotNull();
            assertThat(connection.isClosed()).isFalse();
            
            DatabaseMetaData metaData = connection.getMetaData();
            assertThat(metaData.getDatabaseProductName()).containsIgnoringCase("H2");
            
            log.info("Connected to H2 database: {}", metaData.getDatabaseProductVersion());
        }
    }

    @Test
    public void testConnectionPoolConfiguration() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertThat(hikariDataSource.getMaximumPoolSize()).isEqualTo(10);
        assertThat(hikariDataSource.getMinimumIdle()).isEqualTo(2);
        assertThat(hikariDataSource.getConnectionTimeout()).isEqualTo(30000);
    }

    @Test
    public void testSchemaCreation() {
        assertThat(operations).isNotNull();
        
        long userCount = operations.count(User.class).get().value();
        assertThat(userCount).isGreaterThanOrEqualTo(0);
        
        log.info("Schema validation successful, User table accessible with {} records", userCount);
    }

    @Test
    public void testDatabaseOperations() {
        User testUser = new User();
        testUser.setFirstname("LocalTest");
        testUser.setLastname("User");
        testUser.setEmailAddress("localtest@example.com");
        testUser.setActive(true);

        User savedUser = operations.insert(testUser);
        assertThat(savedUser.getId()).isNotNull();
        
        User foundUser = operations.findByKey(User.class, savedUser.getId());
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getFirstname()).isEqualTo("LocalTest");
        
        operations.delete(savedUser);
        
        User deletedUser = operations.findByKey(User.class, savedUser.getId());
        assertThat(deletedUser).isNull();
        
        log.info("Basic database operations test completed successfully");
    }

    @Test
    public void testConnectionPoolUnderLoad() throws SQLException {
        int connectionCount = 5;
        Connection[] connections = new Connection[connectionCount];
        
        try {
            for (int i = 0; i < connectionCount; i++) {
                connections[i] = dataSource.getConnection();
                assertThat(connections[i]).isNotNull();
                assertThat(connections[i].isClosed()).isFalse();
            }
            
            log.info("Successfully acquired {} connections from pool", connectionCount);
            
        } finally {
            for (Connection connection : connections) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        }
    }
}
