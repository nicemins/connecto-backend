package com.pm.connecto.language.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pm.connecto.language.domain.Language;

public interface LanguageRepository extends JpaRepository<Language, Long> {

	Optional<Language> findByCode(String code);
}
