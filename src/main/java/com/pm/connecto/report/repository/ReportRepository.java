package com.pm.connecto.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pm.connecto.report.domain.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

	boolean existsByReporterIdAndReportedIdAndSessionId(Long reporterId, Long reportedId, Long sessionId);
}
