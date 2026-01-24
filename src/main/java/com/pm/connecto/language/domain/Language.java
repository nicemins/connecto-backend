package com.pm.connecto.language.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "languages", indexes = {
	@Index(name = "idx_language_code", columnList = "code", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Language {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 10)
	private String code;

	@Column(nullable = false, length = 50)
	private String name;

	public Language(String code, String name) {
		this.code = code;
		this.name = name;
	}
}
