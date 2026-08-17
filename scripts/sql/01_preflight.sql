\pset pager off
\pset null '(null)'

-- OpenSQL Single과 클라이언트 세션의 기본 정보를 기록한다.
SELECT current_database() AS database_name,
       current_user AS database_user,
       current_setting('TimeZone') AS session_timezone,
       version() AS database_version;

-- pgvector 확장과 설치 버전을 확인한다.
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';

-- 애플리케이션이 Flyway로 적용한 마이그레이션 전체를 확인한다.
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
