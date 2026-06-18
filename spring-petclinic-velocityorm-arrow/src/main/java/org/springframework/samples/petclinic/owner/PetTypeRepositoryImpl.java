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
package org.springframework.samples.petclinic.owner;

import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.query.Query;

@Repository
public class PetTypeRepositoryImpl implements PetTypeRepository {

	private final com.velocityorm.core.repository.Repository<PetType, Integer> petTypeRepo;

	public PetTypeRepositoryImpl(VelocityORM orm) {
		this.petTypeRepo = orm.repository(PetType.class);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PetType> findPetTypes() {
		try {
			return petTypeRepo.query().orderBy(PetType::getName, Query.Direction.ASC).list();
		}
		catch (SQLException ex) {
			throw new DataAccessResourceFailureException("Failed to find pet types", ex);
		}
	}

}
