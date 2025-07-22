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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.requery.annotation.Query;
import org.springframework.data.requery.core.RequeryOperations;
import org.springframework.data.requery.domain.sample.Role;
import org.springframework.data.requery.domain.sample.User;
import org.springframework.data.requery.repository.RequeryRepository;
import org.springframework.data.requery.repository.config.EnableRequeryRepositories;
import org.springframework.data.requery.repository.config.InfrastructureConfig;
import org.springframework.data.requery.repository.support.RequeryRepositoryFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration
@Transactional
public class LocalQueryPerformanceTest {

    @Configuration
    @EnableRequeryRepositories
    static class TestConfiguration extends InfrastructureConfig {
    }

    @Autowired
    private RequeryOperations operations;

    private PerformanceTestRepository repository;
    private List<User> testUsers;
    private static final int SMALL_DATASET_SIZE = 100;
    private static final int LARGE_DATASET_SIZE = 1000;

    interface PerformanceTestRepository extends RequeryRepository<User, Integer> {
        
        List<User> findByLastname(String lastname);
        
        List<User> findByFirstnameContaining(String firstname);
        
        List<User> findByAgeGreaterThan(Integer age);
        
        Page<User> findByActiveTrue(PageRequest pageRequest);
        
        @Query("SELECT * FROM User u WHERE u.lastname LIKE ?1 ORDER BY u.firstname")
        List<User> findByLastnameLikeCustomQuery(String lastname);
        
        @Query("SELECT COUNT(*) FROM User u WHERE u.active = true")
        Long countActiveUsersCustomQuery();
    }

    @Before
    public void setup() {
        repository = new RequeryRepositoryFactory(operations).getRepository(PerformanceTestRepository.class);
        repository.deleteAll();
        
        testUsers = createTestDataset(SMALL_DATASET_SIZE);
        repository.saveAll(testUsers);
        
        log.info("Setup completed with {} test users", testUsers.size());
    }

    @Test
    public void testBasicCrudPerformance() {
        long startTime = System.currentTimeMillis();
        
        User testUser = createTestUser("Performance", "Test", "perf@example.com");
        User savedUser = repository.save(testUser);
        
        long saveTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        User foundUser = repository.findById(savedUser.getId()).orElse(null);
        long findTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        foundUser.setLastname("Updated");
        repository.save(foundUser);
        long updateTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        repository.delete(foundUser);
        long deleteTime = System.currentTimeMillis() - startTime;
        
        log.info("CRUD Performance - Save: {}ms, Find: {}ms, Update: {}ms, Delete: {}ms", 
                saveTime, findTime, updateTime, deleteTime);
        
        assertThat(saveTime).isLessThan(1000);
        assertThat(findTime).isLessThan(100);
        assertThat(updateTime).isLessThan(1000);
        assertThat(deleteTime).isLessThan(1000);
    }

    @Test
    public void testDerivedQueryPerformance() {
        long startTime = System.currentTimeMillis();
        List<User> users = repository.findByLastname("TestLastname");
        long derivedQueryTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        List<User> containingUsers = repository.findByFirstnameContaining("Test");
        long containingQueryTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        List<User> ageUsers = repository.findByAgeGreaterThan(25);
        long ageQueryTime = System.currentTimeMillis() - startTime;
        
        log.info("Derived Query Performance - Exact: {}ms, Containing: {}ms, GreaterThan: {}ms", 
                derivedQueryTime, containingQueryTime, ageQueryTime);
        
        assertThat(derivedQueryTime).isLessThan(1000);
        assertThat(containingQueryTime).isLessThan(1000);
        assertThat(ageQueryTime).isLessThan(1000);
        
        assertThat(users).isNotNull();
        assertThat(containingUsers).isNotNull();
        assertThat(ageUsers).isNotNull();
    }

    @Test
    public void testCustomQueryPerformance() {
        long startTime = System.currentTimeMillis();
        List<User> customQueryUsers = repository.findByLastnameLikeCustomQuery("Test%");
        long customQueryTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        Long activeCount = repository.countActiveUsersCustomQuery();
        long countQueryTime = System.currentTimeMillis() - startTime;
        
        log.info("Custom Query Performance - Like Query: {}ms, Count Query: {}ms", 
                customQueryTime, countQueryTime);
        
        assertThat(customQueryTime).isLessThan(1000);
        assertThat(countQueryTime).isLessThan(500);
        
        assertThat(customQueryUsers).isNotNull();
        assertThat(activeCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    public void testPaginationPerformance() {
        long startTime = System.currentTimeMillis();
        Page<User> firstPage = repository.findByActiveTrue(PageRequest.of(0, 10));
        long firstPageTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        Page<User> secondPage = repository.findByActiveTrue(PageRequest.of(1, 10));
        long secondPageTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        Page<User> sortedPage = repository.findByActiveTrue(
            PageRequest.of(0, 10, Sort.by("lastname")));
        long sortedPageTime = System.currentTimeMillis() - startTime;
        
        log.info("Pagination Performance - First: {}ms, Second: {}ms, Sorted: {}ms", 
                firstPageTime, secondPageTime, sortedPageTime);
        
        assertThat(firstPageTime).isLessThan(1000);
        assertThat(secondPageTime).isLessThan(1000);
        assertThat(sortedPageTime).isLessThan(1000);
        
        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(secondPage.getContent()).hasSize(10);
        assertThat(sortedPage.getContent()).hasSize(10);
    }

    @Test
    public void testBulkOperationPerformance() {
        List<User> bulkUsers = createTestDataset(50);
        
        long startTime = System.currentTimeMillis();
        repository.saveAll(bulkUsers);
        long bulkSaveTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        repository.deleteAll(bulkUsers);
        long bulkDeleteTime = System.currentTimeMillis() - startTime;
        
        log.info("Bulk Operation Performance - Save: {}ms, Delete: {}ms", 
                bulkSaveTime, bulkDeleteTime);
        
        assertThat(bulkSaveTime).isLessThan(5000);
        assertThat(bulkDeleteTime).isLessThan(5000);
    }

    @Test
    public void testLargeDatasetPerformance() {
        repository.deleteAll();
        
        List<User> largeDataset = createTestDataset(LARGE_DATASET_SIZE);
        
        long startTime = System.currentTimeMillis();
        repository.saveAll(largeDataset);
        long largeSaveTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        long totalCount = repository.count();
        long countTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        List<User> allUsers = repository.findAll();
        long findAllTime = System.currentTimeMillis() - startTime;
        
        log.info("Large Dataset Performance ({} records) - Save: {}ms, Count: {}ms, FindAll: {}ms", 
                LARGE_DATASET_SIZE, largeSaveTime, countTime, findAllTime);
        
        assertThat(largeSaveTime).isLessThan(30000);
        assertThat(countTime).isLessThan(1000);
        assertThat(findAllTime).isLessThan(10000);
        
        assertThat(totalCount).isEqualTo(LARGE_DATASET_SIZE);
        assertThat(allUsers).hasSize(LARGE_DATASET_SIZE);
    }

    private List<User> createTestDataset(int size) {
        List<User> users = new ArrayList<>();
        Random random = new Random();
        
        for (int i = 0; i < size; i++) {
            User user = createTestUser(
                "TestFirstname" + i,
                "TestLastname" + (i % 10),
                "test" + i + "@example.com"
            );
            user.setAge(20 + random.nextInt(50));
            users.add(user);
        }
        
        return users;
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
