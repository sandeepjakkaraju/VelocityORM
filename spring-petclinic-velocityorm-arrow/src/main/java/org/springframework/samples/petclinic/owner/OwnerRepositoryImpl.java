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
import java.util.Optional;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.velocityorm.core.VelocityORM;
import com.velocityorm.core.query.Query;

@Repository
public class OwnerRepositoryImpl implements OwnerRepository {

	private final com.velocityorm.core.repository.Repository<Owner, Integer> ownerRepo;

	private final com.velocityorm.core.repository.Repository<Pet, Integer> petRepo;

	private final com.velocityorm.core.repository.Repository<Visit, Integer> visitRepo;

	private final com.velocityorm.core.repository.Repository<PetType, Integer> petTypeRepo;

	public OwnerRepositoryImpl(VelocityORM orm) {
		this.ownerRepo = orm.repository(Owner.class);
		this.petRepo = orm.repository(Pet.class);
		this.visitRepo = orm.repository(Visit.class);
		this.petTypeRepo = orm.repository(PetType.class);
	}

	@Override
	@Transactional
	public Owner save(Owner owner) {
		try {
			Owner saved = ownerRepo.save(owner);
			for (Pet pet : owner.getPets()) {
				pet.setOwnerId(saved.getId());
				if (pet.getType() != null && pet.getType().getId() != null) {
					pet.setTypeId(pet.getType().getId());
				}
				Pet savedPet = petRepo.save(pet);
				pet.setId(savedPet.getId());
				for (Visit visit : pet.getVisits()) {
					visit.setPetId(savedPet.getId());
					Visit savedVisit = visitRepo.save(visit);
					visit.setId(savedVisit.getId());
				}
			}
			return saved;
		}
		catch (SQLException ex) {
			throw new DataAccessResourceFailureException("Failed to save owner", ex);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Owner> findById(Integer id) {
		try {
			Optional<Owner> optionalOwner = ownerRepo.query().where(Owner::getId).eq(id).one();
			if (optionalOwner.isEmpty()) {
				return Optional.empty();
			}
			Owner owner = optionalOwner.get();
			loadPets(owner);
			return Optional.of(owner);
		}
		catch (SQLException ex) {
			throw new DataAccessResourceFailureException("Failed to find owner by id", ex);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Owner> findByLastNameStartingWith(String lastName, Pageable pageable) {
		try {
			String pattern = lastName + "%";
			long total = ownerRepo.query().where(Owner::getLastName).like(pattern).count();
			var query = ownerRepo.query()
				.where(Owner::getLastName)
				.like(pattern)
				.orderBy(Owner::getLastName, Query.Direction.ASC);
			List<Owner> owners;
			if (pageable.isPaged()) {
				owners = query.limit(pageable.getPageSize()).offset((int) pageable.getOffset()).list();
			}
			else {
				owners = query.list();
			}
			return new PageImpl<>(owners, pageable, total);
		}
		catch (SQLException ex) {
			throw new DataAccessResourceFailureException("Failed to find owners by last name", ex);
		}
	}

	private void loadPets(Owner owner) throws SQLException {
		List<Pet> pets = petRepo.query()
			.where(Pet::getOwnerId)
			.eq(owner.getId())
			.orderBy(Pet::getName, Query.Direction.ASC)
			.list();
		owner.getPets().clear();
		for (Pet pet : pets) {
			if (pet.getTypeId() != null) {
				petTypeRepo.query().where(PetType::getId).eq(pet.getTypeId()).one().ifPresent(pet::setType);
			}
			loadVisits(pet);
			owner.getPets().add(pet);
		}
	}

	private void loadVisits(Pet pet) throws SQLException {
		List<Visit> visits = visitRepo.query()
			.where(Visit::getPetId)
			.eq(pet.getId())
			.orderBy(Visit::getDate, Query.Direction.ASC)
			.list();
		pet.getVisits().clear();
		pet.getVisits().addAll(visits);
	}

}
