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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.requery.core.RequeryOperations;
import org.springframework.data.requery.core.RequeryTransactionManager;
import org.springframework.data.requery.mapping.RequeryMappingContext;
import org.springframework.data.requery.repository.config.EnableRequeryRepositories;
import org.springframework.data.requery.repository.config.InfrastructureConfig;
import org.springframework.data.requery.repository.sample.UserRepository;
import org.springframework.data.requery.repository.support.RequeryRepositoryFactoryBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration
public class LocalConfigurationTest {

    @Configuration
    @EnableRequeryRepositories(basePackageClasses = { UserRepository.class })
    static class TestConfiguration extends InfrastructureConfig {
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RequeryOperations operations;

    @Autowired
    private RequeryMappingContext mappingContext;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Test
    public void testApplicationContextLoading() {
        assertThat(applicationContext).isNotNull();
        log.info("Application context loaded successfully with {} beans", 
                applicationContext.getBeanDefinitionCount());
    }

    @Test
    public void testCoreBeansWiring() {
        assertThat(operations).isNotNull();
        assertThat(dataSource).isNotNull();
        assertThat(transactionManager).isNotNull();
        assertThat(transactionManager).isInstanceOf(RequeryTransactionManager.class);
        
        log.info("Core Requery beans wired successfully");
    }

    @Test
    public void testRepositoryScanning() {
        assertThat(userRepository).isNotNull();
        
        String[] repositoryBeans = applicationContext.getBeanNamesForType(RequeryRepositoryFactoryBean.class);
        assertThat(repositoryBeans).isNotEmpty();
        
        log.info("Repository scanning successful, found {} repository factory beans", repositoryBeans.length);
    }

    @Test
    public void testMappingContextConfiguration() {
        assertThat(mappingContext).isNotNull();
        assertThat(mappingContext.getPersistentEntities()).isNotEmpty();
        
        log.info("Mapping context configured with {} persistent entities", 
                mappingContext.getPersistentEntities().size());
    }

    @Test
    public void testRepositoryFactoryBeanCreation() {
        String[] factoryBeans = applicationContext.getBeanNamesForType(RequeryRepositoryFactoryBean.class);
        assertThat(factoryBeans).isNotEmpty();
        
        for (String beanName : factoryBeans) {
            Object factoryBean = applicationContext.getBean(beanName);
            assertThat(factoryBean).isInstanceOf(RequeryRepositoryFactoryBean.class);
        }
        
        log.info("Repository factory beans created successfully: {}", (Object[]) factoryBeans);
    }

    @Test
    public void testBeanDependencyInjection() {
        assertThat(userRepository).isNotNull();
        
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(0);
        
        log.info("Repository dependency injection working correctly");
    }

    @Test
    public void testTransactionManagerConfiguration() {
        assertThat(transactionManager).isNotNull();
        assertThat(transactionManager).isInstanceOf(RequeryTransactionManager.class);
        
        RequeryTransactionManager requeryTxManager = (RequeryTransactionManager) transactionManager;
        assertThat(requeryTxManager.getDataSource()).isEqualTo(dataSource);
        
        log.info("Transaction manager configured correctly with data source");
    }

    @Test
    public void testRequeryOperationsConfiguration() {
        assertThat(operations).isNotNull();
        assertThat(operations.getEntityModel()).isNotNull();
        assertThat(operations.getEntityModel().getEntities()).isNotEmpty();
        
        log.info("RequeryOperations configured with {} entities", 
                operations.getEntityModel().getEntities().size());
    }
}
