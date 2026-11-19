package com.blockchaincb.demo.shared;

import com.blockchaincb.demo.exception.ApiException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** Uses ONLY public patients/grants. SQL identifiers are constants, never client input. */
@Repository
public class SharedGrantRepository {
  private final JdbcTemplate jdbc;

  public SharedGrantRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Contract(boolean doctorReferencesUser) {}

  public record Grant(
      long id,
      String patientCode,
      String status,
      boolean revoked,
      String expiresAt,
      boolean expired) {}

  public record VisiblePatient(long id, String fullName, String patientCode) {}

  private static ApiException unsupported() {
    return new ApiException(503, "SHARED_SCHEMA_UNVERIFIED");
  }

  /**
   * Read metadata before every operation, so a changed FK cannot silently change identity
   * semantics.
   */
  public Contract contract() {
    try {
      return jdbc.execute(
          (org.springframework.jdbc.core.ConnectionCallback<Contract>)
              c -> {
                var m = c.getMetaData();
                if (!Set.of("H2", "PostgreSQL").contains(m.getDatabaseProductName()))
                  throw unsupported();
                String schema = "H2".equals(m.getDatabaseProductName()) ? "PUBLIC" : "public";
                String patients = "PUBLIC".equals(schema) ? "PATIENTS" : "patients";
                String grants = "PUBLIC".equals(schema) ? "ACCESS_GRANTS" : "access_grants";
                checkColumns(m, schema, patients, Set.of("id", "user_id", "patient_code"), false);
                checkColumns(
                    m,
                    schema,
                    grants,
                    Set.of("id", "patient_id", "doctor_id", "status", "revoked", "expires_at"),
                    true);
                if (!target(m, schema, patients, "user_id").equals("public.users.id")
                    || !target(m, schema, grants, "patient_id").equals("public.patients.id"))
                  throw unsupported();
                String doctor = target(m, schema, grants, "doctor_id");
                if (!Set.of("public.users.id", "public.doctors.id").contains(doctor))
                  throw unsupported();
                boolean uniqueCode = false;
                var indexes = new HashMap<String, List<String>>();
                try (var r = m.getIndexInfo(null, schema, patients, true, false)) {
                  while (r.next()) {
                    String name = r.getString("INDEX_NAME"), col = r.getString("COLUMN_NAME");
                    if (name != null && col != null && r.getString("FILTER_CONDITION") == null)
                      indexes
                          .computeIfAbsent(name, k -> new ArrayList<>())
                          .add(col.toLowerCase(Locale.ROOT));
                  }
                }
                uniqueCode =
                    indexes.values().stream()
                        .anyMatch(cols -> cols.equals(List.of("patient_code")));
                if (!uniqueCode) throw unsupported();
                return new Contract(doctor.equals("public.users.id"));
              });
    } catch (org.springframework.dao.DataAccessException e) {
      throw new ApiException(503, "SHARED_METADATA_UNAVAILABLE");
    }
  }

  private void checkColumns(
      DatabaseMetaData m, String schema, String table, Set<String> required, boolean insert)
      throws SQLException {
    var found = new HashSet<String>();
    try (var r = m.getColumns(null, schema, table, null)) {
      while (r.next()) {
        if (!table.equalsIgnoreCase(r.getString("TABLE_NAME"))) continue;
        String col = r.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
        found.add(col);
        int type = r.getInt("DATA_TYPE");
        if (col.equals("patient_code")
            && !Set.of(Types.VARCHAR, Types.LONGVARCHAR, Types.CHAR).contains(type))
          throw unsupported();
        // pgjdbc reports PostgreSQL bool as Types.BIT; H2 reports Types.BOOLEAN.
        // Requiring bool/boolean TYPE_NAME keeps PostgreSQL bit strings unsupported.
        if (insert
            && col.equals("revoked")
            && (!Set.of(Types.BOOLEAN, Types.BIT).contains(type)
                || !Set.of("bool", "boolean")
                    .contains(String.valueOf(r.getString("TYPE_NAME")).toLowerCase(Locale.ROOT))))
          throw unsupported();
        if (insert
            && col.equals("expires_at")
            && !Set.of(Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE).contains(type))
          throw unsupported();
        if (insert
            && col.equals("id")
            && r.getString("COLUMN_DEF") == null
            && !"YES".equals(r.getString("IS_AUTOINCREMENT"))) throw unsupported();
        if (Set.of("id", "patient_id", "doctor_id", "user_id").contains(col)
            && type != Types.BIGINT) throw unsupported();
        if (insert
            && col.equals("expires_at")
            && r.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls) throw unsupported();
        if (insert
            && !required.contains(col)
            && r.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls
            && r.getString("COLUMN_DEF") == null
            && !"YES".equals(r.getString("IS_AUTOINCREMENT"))
            && !"YES".equals(r.getString("IS_GENERATEDCOLUMN"))) throw unsupported();
      }
    }
    if (!found.containsAll(required)) throw unsupported();
  }

  String target(DatabaseMetaData m, String schema, String table, String column)
      throws SQLException {
    var groups = new HashMap<String, Map<String, String>>();
    try (var r = m.getImportedKeys(null, schema, table)) {
      while (r.next()) {
        groups
            .computeIfAbsent(r.getString("FK_NAME"), k -> new HashMap<>())
            .put(
                r.getString("FKCOLUMN_NAME").toLowerCase(Locale.ROOT),
                (r.getString("PKTABLE_SCHEM")
                        + "."
                        + r.getString("PKTABLE_NAME")
                        + "."
                        + r.getString("PKCOLUMN_NAME"))
                    .toLowerCase(Locale.ROOT));
      }
    }
    var targets = new HashSet<String>();
    for (var group : groups.values())
      if (group.containsKey(column)) {
        if (group.size() != 1) throw unsupported();
        targets.add(group.get(column));
      }
    if (targets.size() != 1) throw unsupported();
    return targets.iterator().next();
  }

  public void requireReadLocked(long patient, long doctor) {
    var active =
        jdbc.query(
            "SELECT id FROM public.access_grants WHERE patient_id=? AND doctor_id=? AND"
                + " status='ACTIVE' AND revoked=false AND (expires_at IS NULL OR"
                + " expires_at>CURRENT_TIMESTAMP) ORDER BY id FOR UPDATE",
            (r, n) -> r.getLong(1),
            patient,
            doctor);
    if (active.isEmpty()) throw new ApiException(403, "PATIENT_READ_PERMISSION_REQUIRED");
  }

  public Optional<Long> lockPatient(String code) {
    // Serialises concurrent Doctor requests across JVMs without adding a production constraint.
    var rows =
        jdbc.query(
            "SELECT id FROM public.patients WHERE patient_code=? FOR UPDATE",
            (r, n) -> r.getLong(1),
            code);
    if (rows.size() > 1) throw unsupported();
    return rows.stream().findFirst();
  }

  public boolean patientAccountValid(long patient) {
    return jdbc.queryForObject(
            "SELECT count(*) FROM public.patients p JOIN public.users u ON u.id=p.user_id WHERE"
                + " p.id=? AND u.role='PATIENT' AND u.enabled=true",
            Long.class,
            patient)
        == 1;
  }

  public List<Grant> open(long patient, long doctor) {
    return jdbc.query(
        "SELECT g.id,p.patient_code,g.status,g.revoked,g.expires_at, (g.expires_at IS NOT NULL AND"
            + " g.expires_at<=CURRENT_TIMESTAMP) AS expired FROM public.access_grants g JOIN"
            + " public.patients p ON p.id=g.patient_id WHERE g.patient_id=? AND g.doctor_id=? AND"
            + " g.revoked=false AND g.status IN ('PENDING','ACTIVE') AND (g.expires_at IS NULL OR"
            + " g.expires_at>CURRENT_TIMESTAMP) ORDER BY g.id FOR UPDATE",
        this::grant,
        patient,
        doctor);
  }

  public long insertPending(long patient, long doctor) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO public.access_grants(patient_id,doctor_id,status,revoked,expires_at)"
                      + " VALUES (?,?,'PENDING',false,NULL)",
                  new String[] {"id"});
          p.setLong(1, patient);
          p.setLong(2, doctor);
          return p;
        },
        key);
    if (key.getKey() == null) throw unsupported();
    return key.getKey().longValue();
  }

  private Grant grant(ResultSet r, int row) throws SQLException {
    // Do not invent UTC/offset for the team's timestamp WITHOUT time zone.
    // Eligibility and the expired flag are both evaluated by the database clock.
    int type = r.getMetaData().getColumnType(r.findColumn("expires_at"));
    Object expiry =
        type == Types.TIMESTAMP_WITH_TIMEZONE
            ? r.getObject("expires_at", OffsetDateTime.class)
            : r.getObject("expires_at", LocalDateTime.class);
    return new Grant(
        r.getLong("id"),
        r.getString("patient_code"),
        r.getString("status"),
        r.getBoolean("revoked"),
        expiry == null ? null : expiry.toString(),
        r.getBoolean("expired"));
  }

  public Page<Grant> requests(long doctor, Pageable page) {
    long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM public.access_grants WHERE doctor_id=?", Long.class, doctor);
    var rows =
        jdbc.query(
            "SELECT g.id,p.patient_code,g.status,g.revoked,g.expires_at, (g.expires_at IS NOT NULL"
                + " AND g.expires_at<=CURRENT_TIMESTAMP) AS expired FROM public.access_grants g"
                + " JOIN public.patients p ON p.id=g.patient_id WHERE g.doctor_id=? ORDER BY g.id"
                + " DESC LIMIT ? OFFSET ?",
            this::grant,
            doctor,
            page.getPageSize(),
            page.getOffset());
    return new PageImpl<>(rows, page, count);
  }

  public Page<VisiblePatient> visible(long doctor, String query, Pageable page) {
    String where =
        " FROM public.patients p JOIN public.users u ON u.id=p.user_id WHERE u.role='PATIENT' AND"
            + " u.enabled=true AND (position(lower(?) in lower(u.full_name))>0 OR position(lower(?)"
            + " in lower(p.patient_code))>0) AND EXISTS (SELECT 1 FROM public.access_grants g WHERE"
            + " g.patient_id=p.id AND g.doctor_id=? AND g.status='ACTIVE' AND g.revoked=false AND"
            + " (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP))";
    long count = jdbc.queryForObject("SELECT count(*)" + where, Long.class, query, query, doctor);
    var rows =
        jdbc.query(
            "SELECT p.id,u.full_name,p.patient_code" + where + " ORDER BY p.id LIMIT ? OFFSET ?",
            (r, n) -> new VisiblePatient(r.getLong(1), r.getString(2), r.getString(3)),
            query,
            query,
            doctor,
            page.getPageSize(),
            page.getOffset());
    return new PageImpl<>(rows, page, count);
  }
}
