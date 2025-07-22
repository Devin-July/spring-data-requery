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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.requery.core.RequeryOperations;
import org.springframework.data.requery.domain.sample.User;
import org.springframework.data.requery.repository.config.EnableRequeryRepositories;
import org.springframework.data.requery.repository.config.InfrastructureConfig;
import org.springframework.data.requery.repository.sample.UserRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration
@Transactional
public class LocalKotlinInteropTest {

    @Configuration
    @EnableRequeryRepositories(basePackageClasses = { UserRepository.class })
    static class TestConfiguration extends InfrastructureConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequeryOperations operations;

    private User testUser;

    @Before
    public void setup() {
        userRepository.deleteAll();
        
        testUser = createTestUser("Kotlin", "Interop", "kotlin@example.com");
        
        log.info("Kotlin interop test setup completed");
    }

    @Test
    public void testJavaRepositoryWithKotlinEntities() {
        User savedUser = userRepository.save(testUser);
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getFirstname()).isEqualTo("Kotlin");
        
        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getLastname()).isEqualTo("Interop");
        
        log.info("Java repository with Kotlin entities test completed successfully");
    }

    @Test
    public void testJavaOperationsWithKotlinEntities() {
        User savedUser = operations.insert(testUser);
        assertThat(savedUser.getId()).isNotNull();
        
        User foundUser = operations.findByKey(User.class, savedUser.getId());
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getEmailAddress()).isEqualTo("kotlin@example.com");
        
        foundUser.setLastname("UpdatedInterop");
        User updatedUser = operations.update(foundUser);
        assertThat(updatedUser.getLastname()).isEqualTo("UpdatedInterop");
        
        operations.delete(updatedUser);
        User deletedUser = operations.findByKey(User.class, savedUser.getId());
        assertThat(deletedUser).isNull();
        
        log.info("Java operations with Kotlin entities test completed successfully");
    }

    @Test
    public void testNullSafetyInterop() {
        testUser.setFirstname(null);
        User savedUser = userRepository.save(testUser);
        
        assertThat(savedUser.getFirstname()).isNull();
        assertThat(savedUser.getLastname()).isNotNull();
        
        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getFirstname()).isNull();
        
        log.info("Null safety interop test completed successfully");
    }

    @Test
    public void testCollectionInterop() {
        User user1 = createTestUser("Collection1", "Test", "collection1@example.com");
        User user2 = createTestUser("Collection2", "Test", "collection2@example.com");
        User user3 = createTestUser("Collection3", "Test", "collection3@example.com");
        
        userRepository.saveAll(java.util.Arrays.asList(user1, user2, user3));
        
        List<User> testUsers = userRepository.findByLastname("Test");
        assertThat(testUsers).hasSize(3);
        assertThat(testUsers).extracting(User::getLastname).containsOnly("Test");
        
        log.info("Collection interop test completed successfully");
    }

    @Test
    public void testDataClassInterop() {
        testUser.setAge(30);
        testUser.setActive(true);
        
        User savedUser = userRepository.save(testUser);
        
        assertThat(savedUser.getAge()).isEqualTo(30);
        assertThat(savedUser.getActive()).isTrue();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        
        log.info("Data class interop test completed successfully");
    }

    @Test
    public void testStringInterop() {
        String longText = "This is a very long text that tests string interoperability between Java and Kotlin entities. " +
                         "It should handle Unicode characters like: αβγδε, 한글, 日本語, and emojis: 🚀🎉✨";
        
        testUser.setFirstname(longText);
        User savedUser = userRepository.save(testUser);
        
        assertThat(savedUser.getFirstname()).isEqualTo(longText);
        
        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getFirstname()).isEqualTo(longText);
        
        log.info("String interop test completed successfully");
    }

    @Test
    public void testTimestampInterop() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        testUser.setCreatedAt(now);
        
        User savedUser = userRepository.save(testUser);
        assertThat(savedUser.getCreatedAt()).isNotNull();
        
        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getCreatedAt()).isEqualTo(now);
        
        log.info("Timestamp interop test completed successfully");
    }

    @Test
    public void testBooleanInterop() {
        testUser.setActive(false);
        User savedUser = userRepository.save(testUser);
        
        assertThat(savedUser.getActive()).isFalse();
        
        List<User> inactiveUsers = userRepository.findByActiveFalse();
        assertThat(inactiveUsers).hasSize(1);
        assertThat(inactiveUsers.get(0).getId()).isEqualTo(savedUser.getId());
        
        log.info("Boolean interop test completed successfully");
    }

    @Test
    public void testNumericInterop() {
        testUser.setAge(42);
        User savedUser = userRepository.save(testUser);
        
        assertThat(savedUser.getAge()).isEqualTo(42);
        
        List<User> olderUsers = userRepository.findByAgeGreaterThan(40);
        assertThat(olderUsers).hasSize(1);
        assertThat(olderUsers.get(0).getAge()).isEqualTo(42);
        
        log.info("Numeric interop test completed successfully");
    }

    @Test
    public void testQueryMethodInterop() {
        User user1 = createTestUser("Query", "Method", "query1@example.com");
        User user2 = createTestUser("Query", "Method", "query2@example.com");
        
        userRepository.saveAll(java.util.Arrays.asList(user1, user2));
        
        List<User> queryUsers = userRepository.findByFirstnameAndLastname("Query", "Method");
        assertThat(queryUsers).hasSize(2);
        
        Optional<User> singleUser = userRepository.findByEmailAddress("query1@example.com");
        assertThat(singleUser).isPresent();
        assertThat(singleUser.get().getFirstname()).isEqualTo("Query");
        
        log.info("Query method interop test completed successfully");
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
