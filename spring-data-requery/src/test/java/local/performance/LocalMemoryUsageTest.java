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
import org.springframework.data.requery.core.RequeryOperations;
import org.springframework.data.requery.domain.sample.Role;
import org.springframework.data.requery.domain.sample.User;
import org.springframework.data.requery.repository.config.EnableRequeryRepositories;
import org.springframework.data.requery.repository.config.InfrastructureConfig;
import org.springframework.data.requery.repository.sample.UserRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration
@Transactional
public class LocalMemoryUsageTest {

    @Configuration
    @EnableRequeryRepositories(basePackageClasses = { UserRepository.class })
    static class TestConfiguration extends InfrastructureConfig {
    }

    @Autowired
    private UserRepository repository;

    @Autowired
    private RequeryOperations operations;

    private MemoryMXBean memoryMXBean;
    private List<GarbageCollectorMXBean> gcMXBeans;

    @Before
    public void setup() {
        repository.deleteAll();
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        System.gc();
        
        log.info("Memory usage test setup completed");
    }

    @Test
    public void testMemoryUsageForBasicOperations() {
        MemoryUsage beforeHeap = memoryMXBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();
        
        User testUser = createTestUser("Memory", "Test", "memory@example.com");
        User savedUser = repository.save(testUser);
        
        MemoryUsage afterHeap = memoryMXBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();
        
        long memoryIncrease = afterUsed - beforeUsed;
        
        log.info("Memory usage for basic operations - Before: {}MB, After: {}MB, Increase: {}MB",
                beforeUsed / (1024 * 1024), afterUsed / (1024 * 1024), memoryIncrease / (1024 * 1024));
        
        assertThat(savedUser.getId()).isNotNull();
        assertThat(memoryIncrease).isLessThan(10 * 1024 * 1024);
    }

    @Test
    public void testMemoryUsageForBulkOperations() {
        System.gc();
        MemoryUsage beforeHeap = memoryMXBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();
        
        List<User> bulkUsers = createTestDataset(1000);
        repository.saveAll(bulkUsers);
        
        MemoryUsage afterSaveHeap = memoryMXBean.getHeapMemoryUsage();
        long afterSaveUsed = afterSaveHeap.getUsed();
        
        List<User> allUsers = repository.findAll();
        
        MemoryUsage afterLoadHeap = memoryMXBean.getHeapMemoryUsage();
        long afterLoadUsed = afterLoadHeap.getUsed();
        
        long saveMemoryIncrease = afterSaveUsed - beforeUsed;
        long loadMemoryIncrease = afterLoadUsed - afterSaveUsed;
        
        log.info("Memory usage for bulk operations - Save increase: {}MB, Load increase: {}MB",
                saveMemoryIncrease / (1024 * 1024), loadMemoryIncrease / (1024 * 1024));
        
        assertThat(allUsers).hasSize(1000);
        assertThat(saveMemoryIncrease).isLessThan(100 * 1024 * 1024);
        assertThat(loadMemoryIncrease).isLessThan(50 * 1024 * 1024);
    }

    @Test
    public void testGarbageCollectionBehavior() {
        long initialGcCount = getTotalGcCount();
        long initialGcTime = getTotalGcTime();
        
        for (int i = 0; i < 100; i++) {
            List<User> tempUsers = createTestDataset(50);
            repository.saveAll(tempUsers);
            repository.deleteAll(tempUsers);
        }
        
        System.gc();
        
        long finalGcCount = getTotalGcCount();
        long finalGcTime = getTotalGcTime();
        
        long gcCountIncrease = finalGcCount - initialGcCount;
        long gcTimeIncrease = finalGcTime - initialGcTime;
        
        log.info("GC behavior - GC count increase: {}, GC time increase: {}ms", 
                gcCountIncrease, gcTimeIncrease);
        
        assertThat(gcCountIncrease).isGreaterThanOrEqualTo(0);
        assertThat(gcTimeIncrease).isGreaterThanOrEqualTo(0);
        assertThat(gcTimeIncrease).isLessThan(10000);
    }

    @Test
    public void testEntityCachingMemoryImpact() {
        System.gc();
        MemoryUsage beforeHeap = memoryMXBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();
        
        User testUser = createTestUser("Cache", "Test", "cache@example.com");
        User savedUser = repository.save(testUser);
        
        for (int i = 0; i < 100; i++) {
            repository.findById(savedUser.getId());
        }
        
        MemoryUsage afterHeap = memoryMXBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();
        
        long memoryIncrease = afterUsed - beforeUsed;
        
        log.info("Entity caching memory impact - Memory increase: {}KB", memoryIncrease / 1024);
        
        assertThat(memoryIncrease).isLessThan(5 * 1024 * 1024);
    }

    @Test
    public void testMemoryLeakDetection() {
        System.gc();
        MemoryUsage initialHeap = memoryMXBean.getHeapMemoryUsage();
        long initialUsed = initialHeap.getUsed();
        
        for (int cycle = 0; cycle < 5; cycle++) {
            List<User> cycleUsers = createTestDataset(200);
            repository.saveAll(cycleUsers);
            
            List<User> loadedUsers = repository.findAll();
            assertThat(loadedUsers.size()).isGreaterThanOrEqualTo(200);
            
            repository.deleteAll(cycleUsers);
            
            System.gc();
            
            MemoryUsage cycleHeap = memoryMXBean.getHeapMemoryUsage();
            long cycleUsed = cycleHeap.getUsed();
            
            log.debug("Cycle {} memory usage: {}MB", cycle, cycleUsed / (1024 * 1024));
        }
        
        System.gc();
        MemoryUsage finalHeap = memoryMXBean.getHeapMemoryUsage();
        long finalUsed = finalHeap.getUsed();
        
        long memoryGrowth = finalUsed - initialUsed;
        
        log.info("Memory leak detection - Initial: {}MB, Final: {}MB, Growth: {}MB",
                initialUsed / (1024 * 1024), finalUsed / (1024 * 1024), memoryGrowth / (1024 * 1024));
        
        assertThat(memoryGrowth).isLessThan(20 * 1024 * 1024);
    }

    private List<User> createTestDataset(int size) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            users.add(createTestUser("Memory" + i, "User", "memory" + i + "@example.com"));
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

    private long getTotalGcCount() {
        return gcMXBeans.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .sum();
    }

    private long getTotalGcTime() {
        return gcMXBeans.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .sum();
    }
}
