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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.requery.domain.sample.User;
import org.springframework.data.requery.repository.config.EnableRequeryRepositories;
import org.springframework.data.requery.repository.config.InfrastructureConfig;
import org.springframework.data.requery.repository.sample.UserRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration
@Transactional
public class LocalTransactionTest {

    @Configuration
    @EnableRequeryRepositories(basePackageClasses = { UserRepository.class })
    static class TestConfiguration extends InfrastructureConfig {
    }

    @Autowired
    private UserRepository repository;

    private User testUser;

    @Before
    public void setup() {
        repository.deleteAll();
        
        testUser = new User();
        testUser.setFirstname("Transaction");
        testUser.setLastname("Test");
        testUser.setEmailAddress("transaction@example.com");
        testUser.setActive(true);
        testUser.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
    }

    @Test
    public void testTransactionalRollback() {
        long initialCount = repository.count();
        
        try {
            repository.save(testUser);
            assertThat(repository.count()).isEqualTo(initialCount + 1);
            
            throw new RuntimeException("Simulated exception for rollback test");
            
        } catch (RuntimeException e) {
            log.info("Expected exception caught: {}", e.getMessage());
        }
        
        assertThat(repository.count()).isEqualTo(initialCount);
        log.info("Transaction rollback test completed successfully");
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testTransactionPropagation() {
        long initialCount = repository.count();
        
        User savedUser = repository.save(testUser);
        assertThat(savedUser.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(initialCount + 1);
        
        log.info("Transaction propagation test completed successfully");
    }

    @Test
    public void testTransactionalUpdate() {
        User savedUser = repository.save(testUser);
        assertThat(savedUser.getId()).isNotNull();
        
        String originalLastname = savedUser.getLastname();
        savedUser.setLastname("UpdatedLastname");
        
        User updatedUser = repository.save(savedUser);
        assertThat(updatedUser.getLastname()).isEqualTo("UpdatedLastname");
        assertThat(updatedUser.getLastname()).isNotEqualTo(originalLastname);
        
        User foundUser = repository.findById(savedUser.getId()).orElse(null);
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getLastname()).isEqualTo("UpdatedLastname");
        
        log.info("Transactional update test completed successfully");
    }

    @Test
    public void testTransactionalDelete() {
        User savedUser = repository.save(testUser);
        assertThat(savedUser.getId()).isNotNull();
        
        long countAfterSave = repository.count();
        
        repository.delete(savedUser);
        
        long countAfterDelete = repository.count();
        assertThat(countAfterDelete).isEqualTo(countAfterSave - 1);
        
        assertThat(repository.findById(savedUser.getId())).isNotPresent();
        
        log.info("Transactional delete test completed successfully");
    }

    @Test
    public void testBatchOperationTransaction() {
        User user1 = new User();
        user1.setFirstname("Batch1");
        user1.setLastname("User");
        user1.setEmailAddress("batch1@example.com");
        user1.setActive(true);
        user1.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));

        User user2 = new User();
        user2.setFirstname("Batch2");
        user2.setLastname("User");
        user2.setEmailAddress("batch2@example.com");
        user2.setActive(true);
        user2.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));

        long initialCount = repository.count();
        
        repository.saveAll(java.util.Arrays.asList(user1, user2));
        
        assertThat(repository.count()).isEqualTo(initialCount + 2);
        
        log.info("Batch operation transaction test completed successfully");
    }

    @Test
    public void testReadOnlyTransaction() {
        User savedUser = repository.save(testUser);
        assertThat(savedUser.getId()).isNotNull();
        
        testReadOnlyOperation(savedUser.getId());
        
        log.info("Read-only transaction test completed successfully");
    }

    @Transactional(readOnly = true)
    private void testReadOnlyOperation(Integer userId) {
        User foundUser = repository.findById(userId).orElse(null);
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getFirstname()).isEqualTo("Transaction");
    }

    @Test
    public void testNestedTransactionRollback() {
        long initialCount = repository.count();
        
        assertThatThrownBy(() -> {
            User outerUser = repository.save(testUser);
            assertThat(outerUser.getId()).isNotNull();
            
            performNestedTransactionWithException();
            
        }).isInstanceOf(RuntimeException.class);
        
        assertThat(repository.count()).isEqualTo(initialCount);
        
        log.info("Nested transaction rollback test completed successfully");
    }

    @Transactional(propagation = Propagation.NESTED)
    private void performNestedTransactionWithException() {
        User nestedUser = new User();
        nestedUser.setFirstname("Nested");
        nestedUser.setLastname("User");
        nestedUser.setEmailAddress("nested@example.com");
        nestedUser.setActive(true);
        nestedUser.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        
        repository.save(nestedUser);
        
        throw new RuntimeException("Nested transaction exception");
    }
}
