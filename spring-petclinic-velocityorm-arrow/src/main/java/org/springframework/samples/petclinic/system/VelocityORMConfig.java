/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.system;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;

import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.cache.CaffeineCacheProvider;
import com.velocityorm.core.dialect.PostgresDialect;
import com.velocityorm.core.security.EncryptionService;

@Configuration(proxyBeanMethods = false)
public class VelocityORMConfig {

	@Bean
	@DependsOn("dataSourceScriptDatabaseInitializer")
	public VelocityORM velocityORM(DataSource dataSource) throws Exception {
		TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(dataSource);
		VelocityORM orm = new VelocityORM.Builder().dataSource(proxy)
			.dialect(new PostgresDialect())
			.cacheProvider(new CaffeineCacheProvider())
			.encryptionService(new EncryptionService("petclinic_encryption_key!!"))
			.build();
		orm.bootstrap(List.of(Owner.class, Pet.class, PetType.class, Visit.class, Vet.class, Specialty.class));
		return orm;
	}

}
