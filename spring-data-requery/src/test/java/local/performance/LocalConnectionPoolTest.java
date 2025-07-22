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

package local.performance;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration
public class LocalConnectionPoolTest {

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
            config.setJdbcUrl("jdbc:h2:mem:pooltest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false");
            config.setUsername("sa");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(5000);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(900000);
            config.setLeakDetectionThreshold(60000);
            
            return new HikariDataSource(config);
        }
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RequeryOperations operations;

    @Test
    public void testConnectionPoolConfiguration() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertThat(hikariDataSource.getMaximumPoolSize()).isEqualTo(10);
        assertThat(hikariDataSource.getMinimumIdle()).isEqualTo(2);
        assertThat(hikariDataSource.getConnectionTimeout()).isEqualTo(5000);
        
        log.info("Connection pool configured correctly - Max: {}, Min: {}, Timeout: {}ms",
                hikariDataSource.getMaximumPoolSize(),
                hikariDataSource.getMinimumIdle(),
                hikariDataSource.getConnectionTimeout());
    }

    @Test
    public void testConnectionPoolMetrics() {
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
        
        assertThat(poolMXBean).isNotNull();
        
        int activeConnections = poolMXBean.getActiveConnections();
        int idleConnections = poolMXBean.getIdleConnections();
        int totalConnections = poolMXBean.getTotalConnections();
        
        log.info("Pool Metrics - Active: {}, Idle: {}, Total: {}", 
                activeConnections, idleConnections, totalConnections);
        
        assertThat(totalConnections).isGreaterThanOrEqualTo(hikariDataSource.getMinimumIdle());
        assertThat(totalConnections).isLessThanOrEqualTo(hikariDataSource.getMaximumPoolSize());
        assertThat(activeConnections + idleConnections).isEqualTo(totalConnections);
    }

    @Test
    public void testConnectionAcquisitionPerformance() throws SQLException {
        int connectionCount = 5;
        List<Long> acquisitionTimes = new ArrayList<>();
        List<Connection> connections = new ArrayList<>();
        
        try {
            for (int i = 0; i < connectionCount; i++) {
                long startTime = System.currentTimeMillis();
                Connection connection = dataSource.getConnection();
                long acquisitionTime = System.currentTimeMillis() - startTime;
                
                acquisitionTimes.add(acquisitionTime);
                connections.add(connection);
                
                assertThat(connection).isNotNull();
                assertThat(connection.isClosed()).isFalse();
            }
            
            double averageTime = acquisitionTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            
            log.info("Connection acquisition performance - Average: {}ms, Times: {}", 
                    averageTime, acquisitionTimes);
            
            assertThat(averageTime).isLessThan(1000);
            
        } finally {
            for (Connection connection : connections) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        }
    }

    @Test
    public void testConcurrentConnectionUsage() throws Exception {
        int threadCount = 8;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    performDatabaseOperations(threadId, operationsPerThread);
                } catch (Exception e) {
                    log.error("Thread {} failed", threadId, e);
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        
        log.info("Concurrent connection usage test completed successfully with {} threads", threadCount);
    }

    @Test
    public void testConnectionPoolUnderLoad() throws Exception {
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
        
        int initialActive = poolMXBean.getActiveConnections();
        int initialTotal = poolMXBean.getTotalConnections();
        
        List<Connection> connections = new ArrayList<>();
        
        try {
            for (int i = 0; i < hikariDataSource.getMaximumPoolSize(); i++) {
                Connection connection = dataSource.getConnection();
                connections.add(connection);
                
                Thread.sleep(10);
            }
            
            int activeUnderLoad = poolMXBean.getActiveConnections();
            int totalUnderLoad = poolMXBean.getTotalConnections();
            
            log.info("Pool under load - Initial Active: {}, Under Load Active: {}, Total: {}", 
                    initialActive, activeUnderLoad, totalUnderLoad);
            
            assertThat(activeUnderLoad).isEqualTo(hikariDataSource.getMaximumPoolSize());
            assertThat(totalUnderLoad).isEqualTo(hikariDataSource.getMaximumPoolSize());
            
        } finally {
            for (Connection connection : connections) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
            
            Thread.sleep(100);
            
            int finalActive = poolMXBean.getActiveConnections();
            log.info("Pool after cleanup - Active: {}", finalActive);
            assertThat(finalActive).isLessThanOrEqualTo(initialActive + 1);
        }
    }

    @Test
    public void testConnectionLeakDetection() throws SQLException, InterruptedException {
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        
        Connection leakedConnection = dataSource.getConnection();
        assertThat(leakedConnection).isNotNull();
        
        Thread.sleep(100);
        
        HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
        int activeConnections = poolMXBean.getActiveConnections();
        
        log.info("Connection leak test - Active connections: {}", activeConnections);
        
        leakedConnection.close();
        
        Thread.sleep(100);
        
        int activeAfterClose = poolMXBean.getActiveConnections();
        log.info("After closing leaked connection - Active connections: {}", activeAfterClose);
        
        assertThat(activeAfterClose).isLessThan(activeConnections);
    }

    private void performDatabaseOperations(int threadId, int operationCount) {
        for (int i = 0; i < operationCount; i++) {
            User user = new User();
            user.setFirstname("Thread" + threadId);
            user.setLastname("User" + i);
            user.setEmailAddress("thread" + threadId + "_user" + i + "@example.com");
            user.setActive(true);
            
            User savedUser = operations.insert(user);
            assertThat(savedUser.getId()).isNotNull();
            
            User foundUser = operations.findByKey(User.class, savedUser.getId());
            assertThat(foundUser).isNotNull();
            
            operations.delete(savedUser);
        }
        
        log.debug("Thread {} completed {} operations", threadId, operationCount);
    }
}
