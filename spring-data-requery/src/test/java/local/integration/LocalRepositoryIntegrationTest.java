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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.requery.domain.sample.Role;
import org.springframework.data.requery.domain.sample.User;
import org.springframework.data.requery.repository.config.EnableRequeryRepositories;
import org.springframework.data.requery.repository.config.InfrastructureConfig;
import org.springframework.data.requery.repository.sample.RoleRepository;
import org.springframework.data.requery.repository.sample.UserRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration
@Transactional
public class LocalRepositoryIntegrationTest {

    @Configuration
    @EnableRequeryRepositories(basePackageClasses = { UserRepository.class, RoleRepository.class })
    static class TestConfiguration extends InfrastructureConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User testUser;
    private Role testRole;

    @Before
    public void setup() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
        
        testUser = createTestUser("Integration", "Test", "integration@example.com");
        testRole = createTestRole("TEST_ROLE");
        
        log.info("Integration test setup completed");
    }

    @Test
    public void testBasicCrudOperations() {
        User savedUser = userRepository.save(testUser);
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getFirstname()).isEqualTo("Integration");
        
        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmailAddress()).isEqualTo("integration@example.com");
        
        savedUser.setLastname("Updated");
        User updatedUser = userRepository.save(savedUser);
        assertThat(updatedUser.getLastname()).isEqualTo("Updated");
        
        userRepository.delete(updatedUser);
        Optional<User> deletedUser = userRepository.findById(savedUser.getId());
        assertThat(deletedUser).isNotPresent();
        
        log.info("Basic CRUD operations test completed successfully");
    }

    @Test
    public void testDerivedQueryMethods() {
        User user1 = createTestUser("John", "Doe", "john.doe@example.com");
        User user2 = createTestUser("Jane", "Doe", "jane.doe@example.com");
        User user3 = createTestUser("Bob", "Smith", "bob.smith@example.com");
        
        userRepository.saveAll(Arrays.asList(user1, user2, user3));
        
        List<User> doeUsers = userRepository.findByLastname("Doe");
        assertThat(doeUsers).hasSize(2);
        assertThat(doeUsers).extracting(User::getLastname).containsOnly("Doe");
        
        Optional<User> johnUser = userRepository.findByEmailAddress("john.doe@example.com");
        assertThat(johnUser).isPresent();
        assertThat(johnUser.get().getFirstname()).isEqualTo("John");
        
        List<User> activeUsers = userRepository.findByActiveTrue();
        assertThat(activeUsers).hasSize(3);
        
        log.info("Derived query methods test completed successfully");
    }

    @Test
    public void testPaginationAndSorting() {
        for (int i = 0; i < 25; i++) {
            User user = createTestUser("User" + i, "Lastname" + (i % 5), "user" + i + "@example.com");
            userRepository.save(user);
        }
        
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("firstname"));
        Page<User> firstPage = userRepository.findAll(pageRequest);
        
        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalElements()).isEqualTo(25);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.hasNext()).isTrue();
        
        Page<User> secondPage = userRepository.findAll(PageRequest.of(1, 10, Sort.by("firstname")));
        assertThat(secondPage.getContent()).hasSize(10);
        assertThat(secondPage.isFirst()).isFalse();
        assertThat(secondPage.hasNext()).isTrue();
        
        Page<User> lastPage = userRepository.findAll(PageRequest.of(2, 10, Sort.by("firstname")));
        assertThat(lastPage.getContent()).hasSize(5);
        assertThat(lastPage.isLast()).isTrue();
        
        log.info("Pagination and sorting test completed successfully");
    }

    @Test
    public void testBatchOperations() {
        List<User> batchUsers = Arrays.asList(
            createTestUser("Batch1", "User", "batch1@example.com"),
            createTestUser("Batch2", "User", "batch2@example.com"),
            createTestUser("Batch3", "User", "batch3@example.com"),
            createTestUser("Batch4", "User", "batch4@example.com"),
            createTestUser("Batch5", "User", "batch5@example.com")
        );
        
        Iterable<User> savedUsers = userRepository.saveAll(batchUsers);
        assertThat(savedUsers).hasSize(5);
        
        List<User> foundUsers = userRepository.findByLastname("User");
        assertThat(foundUsers).hasSize(5);
        
        userRepository.deleteAll(batchUsers);
        
        List<User> deletedUsers = userRepository.findByLastname("User");
        assertThat(deletedUsers).isEmpty();
        
        log.info("Batch operations test completed successfully");
    }

    @Test
    public void testEntityRelationships() {
        Role savedRole = roleRepository.save(testRole);
        assertThat(savedRole.getId()).isNotNull();
        
        testUser.getRoles().add(savedRole);
        User savedUser = userRepository.save(testUser);
        
        Optional<User> userWithRoles = userRepository.findById(savedUser.getId());
        assertThat(userWithRoles).isPresent();
        
        Set<Role> userRoles = userWithRoles.get().getRoles();
        assertThat(userRoles).hasSize(1);
        assertThat(userRoles.iterator().next().getName()).isEqualTo("TEST_ROLE");
        
        log.info("Entity relationships test completed successfully");
    }

    @Test
    public void testCustomQueryMethods() {
        User user1 = createTestUser("Alice", "Johnson", "alice@example.com");
        user1.setAge(25);
        User user2 = createTestUser("Bob", "Johnson", "bob@example.com");
        user2.setAge(35);
        User user3 = createTestUser("Charlie", "Brown", "charlie@example.com");
        user3.setAge(45);
        
        userRepository.saveAll(Arrays.asList(user1, user2, user3));
        
        List<User> johnsonUsers = userRepository.findByLastname("Johnson");
        assertThat(johnsonUsers).hasSize(2);
        
        List<User> olderUsers = userRepository.findByAgeGreaterThan(30);
        assertThat(olderUsers).hasSize(2);
        assertThat(olderUsers).extracting(User::getAge).containsExactlyInAnyOrder(35, 45);
        
        log.info("Custom query methods test completed successfully");
    }

    @Test
    public void testTransactionalBehavior() {
        long initialCount = userRepository.count();
        
        User user1 = createTestUser("Trans1", "User", "trans1@example.com");
        User user2 = createTestUser("Trans2", "User", "trans2@example.com");
        
        userRepository.save(user1);
        userRepository.save(user2);
        
        assertThat(userRepository.count()).isEqualTo(initialCount + 2);
        
        log.info("Transactional behavior test completed successfully");
    }

    @Test
    public void testRepositoryCount() {
        long initialCount = userRepository.count();
        
        userRepository.save(testUser);
        assertThat(userRepository.count()).isEqualTo(initialCount + 1);
        
        userRepository.delete(testUser);
        assertThat(userRepository.count()).isEqualTo(initialCount);
        
        log.info("Repository count test completed successfully");
    }

    @Test
    public void testExistsById() {
        User savedUser = userRepository.save(testUser);
        
        assertThat(userRepository.existsById(savedUser.getId())).isTrue();
        
        userRepository.delete(savedUser);
        
        assertThat(userRepository.existsById(savedUser.getId())).isFalse();
        
        log.info("ExistsById test completed successfully");
    }

    @Test
    public void testFindAllById() {
        User user1 = createTestUser("FindAll1", "User", "findall1@example.com");
        User user2 = createTestUser("FindAll2", "User", "findall2@example.com");
        User user3 = createTestUser("FindAll3", "User", "findall3@example.com");
        
        List<User> savedUsers = (List<User>) userRepository.saveAll(Arrays.asList(user1, user2, user3));
        List<Integer> userIds = Arrays.asList(
            savedUsers.get(0).getId(),
            savedUsers.get(1).getId(),
            savedUsers.get(2).getId()
        );
        
        Iterable<User> foundUsers = userRepository.findAllById(userIds);
        assertThat(foundUsers).hasSize(3);
        
        log.info("FindAllById test completed successfully");
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

    private Role createTestRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
