package com.pm.connecto.interest.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pm.connecto.interest.domain.Interest;

public interface InterestRepository extends JpaRepository<Interest, Long> {

	Optional<Interest> findByName(String name);

	boolean existsByName(String name);

	List<Interest> findByNameIn(List<String> names);
}
