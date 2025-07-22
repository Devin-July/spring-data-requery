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

package local.integration;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.requery.configs.TestRequeryConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.requery.core.RequeryOperations;
import org.springframework.data.requery.core.RequeryTransactionManager;
import org.springframework.data.requery.domain.sample.User;
import org.springframework.data.requery.mapping.RequeryMappingContext;
import org.springframework.data.requery.repository.config.EnableRequeryRepositories;
import org.springframework.data.requery.repository.sample.UserRepository;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { TestRequeryConfiguration.class })
@EnableRequeryRepositories(basePackageClasses = { UserRepository.class })
@EnableTransactionManagement
@Transactional
public class LocalStarterIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequeryOperations operations;

    @Autowired
    private RequeryMappingContext mappingContext;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    public void testSpringBootApplicationContextStartup() {
        assertThat(applicationContext).isNotNull();
        
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        assertThat(beanNames).isNotEmpty();
        
        log.info("Spring Boot application context started successfully with {} beans", beanNames.length);
    }

    @Test
    public void testAutoConfigurationBeanCreation() {
        assertThat(operations).isNotNull();
        assertThat(dataSource).isNotNull();
        assertThat(transactionManager).isNotNull();
        assertThat(mappingContext).isNotNull();
        assertThat(userRepository).isNotNull();
        
        assertThat(transactionManager).isInstanceOf(RequeryTransactionManager.class);
        
        log.info("Auto-configuration beans created successfully");
    }

    @Test
    public void testRepositoryAutoConfiguration() {
        assertThat(userRepository).isNotNull();
        
        long initialCount = userRepository.count();
        assertThat(initialCount).isGreaterThanOrEqualTo(0);
        
        log.info("Repository auto-configuration working correctly");
    }

    @Test
    public void testDataSourceAutoConfiguration() {
        assertThat(dataSource).isNotNull();
        
        try {
            assertThat(dataSource.getConnection()).isNotNull();
            log.info("DataSource auto-configuration working correctly");
        } catch (Exception e) {
            log.error("DataSource configuration failed", e);
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testTransactionManagerAutoConfiguration() {
        assertThat(transactionManager).isNotNull();
        assertThat(transactionManager).isInstanceOf(RequeryTransactionManager.class);
        
        RequeryTransactionManager requeryTxManager = (RequeryTransactionManager) transactionManager;
        assertThat(requeryTxManager.getDataSource()).isEqualTo(dataSource);
        
        log.info("Transaction manager auto-configuration working correctly");
    }

    @Test
    public void testMappingContextAutoConfiguration() {
        assertThat(mappingContext).isNotNull();
        assertThat(mappingContext.getPersistentEntities()).isNotEmpty();
        
        log.info("Mapping context auto-configuration working with {} entities", 
                mappingContext.getPersistentEntities().size());
    }

    @Test
    public void testRequeryOperationsAutoConfiguration() {
        assertThat(operations).isNotNull();
        assertThat(operations.getEntityModel()).isNotNull();
        assertThat(operations.getEntityModel().getEntities()).isNotEmpty();
        
        log.info("RequeryOperations auto-configuration working with {} entities", 
                operations.getEntityModel().getEntities().size());
    }

    @Test
    public void testPropertyBasedConfiguration() {
        User testUser = createTestUser("Property", "Test", "property@example.com");
        User savedUser = userRepository.save(testUser);
        
        assertThat(savedUser.getId()).isNotNull();
        
        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmailAddress()).isEqualTo("property@example.com");
        
        log.info("Property-based configuration test completed successfully");
    }

    @Test
    public void testSpringBootTestIntegration() {
        assertThat(applicationContext.getEnvironment()).isNotNull();
        assertThat(applicationContext.getEnvironment().getActiveProfiles()).isNotNull();
        
        log.info("Spring Boot test integration working correctly");
    }

    @Test
    public void testTransactionalBehaviorInSpringBoot() {
        long initialCount = userRepository.count();
        
        User testUser = createTestUser("Transactional", "SpringBoot", "transactional@example.com");
        userRepository.save(testUser);
        
        assertThat(userRepository.count()).isEqualTo(initialCount + 1);
        
        log.info("Transactional behavior in Spring Boot test completed successfully");
    }

    @Test
    public void testRepositoryMethodsInSpringBoot() {
        User user1 = createTestUser("SpringBoot1", "User", "springboot1@example.com");
        User user2 = createTestUser("SpringBoot2", "User", "springboot2@example.com");
        
        userRepository.saveAll(java.util.Arrays.asList(user1, user2));
        
        java.util.List<User> springBootUsers = userRepository.findByLastname("User");
        assertThat(springBootUsers).hasSize(2);
        
        Optional<User> specificUser = userRepository.findByEmailAddress("springboot1@example.com");
        assertThat(specificUser).isPresent();
        assertThat(specificUser.get().getFirstname()).isEqualTo("SpringBoot1");
        
        log.info("Repository methods in Spring Boot test completed successfully");
    }

    @Test
    public void testApplicationContextBeanLookup() {
        assertThat(applicationContext.getBean(RequeryOperations.class)).isNotNull();
        assertThat(applicationContext.getBean(DataSource.class)).isNotNull();
        assertThat(applicationContext.getBean(PlatformTransactionManager.class)).isNotNull();
        assertThat(applicationContext.getBean(RequeryMappingContext.class)).isNotNull();
        assertThat(applicationContext.getBean(UserRepository.class)).isNotNull();
        
        log.info("Application context bean lookup test completed successfully");
    }

    @Test
    public void testSpringBootAutoConfigurationProperties() {
        String[] requeryBeans = applicationContext.getBeanNamesForType(RequeryOperations.class);
        assertThat(requeryBeans).isNotEmpty();
        
        String[] repositoryBeans = applicationContext.getBeanNamesForType(UserRepository.class);
        assertThat(repositoryBeans).isNotEmpty();
        
        log.info("Spring Boot auto-configuration properties test completed successfully");
    }

    private User createTestUser(String firstname, String lastname, String email) {
        User user = new User();
        user.setFirstname(firstname);
        user.setLastname(lastname);
        user.setEmailAddress(email);
        user.setActive(true);
        user.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return user;
    }
}
