package com.pm.connecto.profile.domain;

import java.time.LocalDateTime;

import com.pm.connecto.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "profiles", indexes = {
	@Index(name = "idx_profile_user_id", columnList = "user_id", unique = true),
	@Index(name = "idx_profile_nickname", columnList = "nickname", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(nullable = false, unique = true, length = 50)
	private String nickname;

	@Column(length = 500)
	private String profileImageUrl;

	@Column(length = 500)
	private String bio;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	@Builder
	public Profile(User user, String nickname, String profileImageUrl, String bio) {
		this.user = user;
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
		this.bio = bio;
	}

	public void updateNickname(String nickname) {
		if (nickname != null && !nickname.isBlank()) {
			this.nickname = nickname;
		}
	}

	public void updateProfileImageUrl(String profileImageUrl) {
		this.profileImageUrl = profileImageUrl;
	}

	public void updateBio(String bio) {
		this.bio = bio;
	}

	public void update(String nickname, String profileImageUrl, String bio) {
		updateNickname(nickname);
		updateProfileImageUrl(profileImageUrl);
		updateBio(bio);
	}
}
