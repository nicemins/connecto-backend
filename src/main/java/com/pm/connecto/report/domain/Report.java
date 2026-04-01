package com.pm.connecto.report.domain;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reports",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_report_reporter_reported_session",
			columnNames = {"reporter_id", "reported_id", "session_id"})
	},
	indexes = {
		@Index(name = "idx_report_reporter_id", columnList = "reporter_id"),
		@Index(name = "idx_report_reported_id", columnList = "reported_id")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reporter_id", nullable = false)
	private User reporter;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reported_id", nullable = false)
	private User reported;

	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	@Column(length = 500)
	private String reason;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}

	@Builder
	public Report(User reporter, User reported, Long sessionId, String reason) {
		this.reporter = reporter;
		this.reported = reported;
		this.sessionId = sessionId;
		this.reason = reason;
	}
}
