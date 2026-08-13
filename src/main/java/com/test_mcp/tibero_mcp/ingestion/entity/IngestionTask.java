package com.test_mcp.tibero_mcp.ingestion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ingestion_tasks")
@Getter
@NoArgsConstructor
// 문서 버전별 Outbox 작업. PROCESSING은 영구 상태가 아니라 worker 소유권을 가진 lease다.
public class IngestionTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "document_version", nullable = false)
  private Integer documentVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IngestionTaskStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "claimed_by")
  private String claimedBy;

  @Column(name = "heartbeat_at")
  private Instant heartbeatAt;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  public IngestionTask(Long documentId, Integer documentVersion) {
    this.documentId = documentId;
    this.documentVersion = documentVersion;
    this.status = IngestionTaskStatus.PENDING;
    this.createdAt = Instant.now();
    this.nextAttemptAt = this.createdAt;
  }

  // claim과 lease 기록을 함께 남겨, 다른 인스턴스가 같은 작업을 처리하지 못하게 한다.
  public void markProcessing(String workerId, Instant now, Instant leaseExpiresAt) {
    this.status = IngestionTaskStatus.PROCESSING;
    this.startedAt = now;
    this.attemptCount++;
    this.claimedBy = workerId;
    this.heartbeatAt = now;
    this.leaseExpiresAt = leaseExpiresAt;
  }

  // 만료 회수 뒤에는 claimedBy가 비워지므로, 종료된 옛 워커는 lease를 다시 연장할 수 없다.
  public boolean renewLease(String workerId, Instant now, Instant leaseExpiresAt) {
    if (this.status != IngestionTaskStatus.PROCESSING || !workerId.equals(this.claimedBy)) {
      return false;
    }

    this.heartbeatAt = now;
    this.leaseExpiresAt = leaseExpiresAt;
    return true;
  }

  // 완료·실패 전이 전에 호출해 lease를 잃은 워커가 최신 검색 버전을 덮어쓰지 못하게 한다.
  public boolean isClaimedBy(String workerId) {
    return this.status == IngestionTaskStatus.PROCESSING && workerId.equals(this.claimedBy);
  }

  public void markEmbedded() {
    this.status = IngestionTaskStatus.EMBEDDED;
    this.lastError = null;
    clearLease();
  }

  public void markFailed(String lastError) {
    this.status = IngestionTaskStatus.FAILED;
    this.lastError = lastError;
    clearLease();
  }

  // 재시도는 새로운 claim이 필요하므로 기존 worker 소유권을 지운다.
  public void scheduleRetry(Instant nextAttemptAt, String lastError) {
    this.status = IngestionTaskStatus.PENDING;
    this.nextAttemptAt = nextAttemptAt;
    this.lastError = lastError;
    this.startedAt = null;
    clearLease();
  }

  // 운영자의 명시적 재처리는 이전 자동 재시도와 분리된 새 처리 사이클이므로 시도 횟수를 초기화한다.
  public void retryManually(Instant nextAttemptAt) {
    this.status = IngestionTaskStatus.PENDING;
    this.attemptCount = 0;
    this.nextAttemptAt = nextAttemptAt;
    this.lastError = null;
    this.startedAt = null;
    clearLease();
  }

  private void clearLease() {
    this.claimedBy = null;
    this.heartbeatAt = null;
    this.leaseExpiresAt = null;
  }
}
