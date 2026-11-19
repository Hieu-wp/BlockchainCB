package com.blockchaincb.demo.shared;

import com.blockchaincb.demo.dto.SharedRecordResponse.*;
import com.blockchaincb.demo.exception.ApiException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SELECT-only reader of the inspected public schema. No legacy IDs, URL fetch or DML. */
@Repository
public class SharedRecordRepository {
  private final JdbcTemplate jdbc;
  private final SharedGrantRepository grants;

  public SharedRecordRepository(JdbcTemplate jdbc, SharedGrantRepository grants) {
    this.jdbc = jdbc;
    this.grants = grants;
  }

  private ApiException unsupported() {
    return new ApiException(503, "SHARED_RECORD_READ_NOT_READY");
  }

  public boolean available() {
    try {
      contract();
      return true;
    } catch (ApiException e) {
      return false;
    }
  }

  public void contract() {
    try {
      jdbc.execute(
          (org.springframework.jdbc.core.ConnectionCallback<Void>)
              c -> {
                var m = c.getMetaData();
                String schema = "H2".equals(m.getDatabaseProductName()) ? "PUBLIC" : "public";
                var columns =
                    Map.of(
                        "medical_records",
                            Set.of(
                                "id",
                                "record_code",
                                "patient_id",
                                "doctor_id",
                                "specialty_id",
                                "visit_at",
                                "reason",
                                "status",
                                "current_version",
                                "created_at"),
                        "record_versions",
                            Set.of(
                                "id",
                                "record_id",
                                "version_no",
                                "diagnosis",
                                "prescription",
                                "note",
                                "data_hash",
                                "tx_hash",
                                "chain_status",
                                "created_by",
                                "created_at",
                                "room_name",
                                "visit_type",
                                "secondary_diagnosis",
                                "treatment_direction",
                                "next_visit_date",
                                "next_visit_note"),
                        "prescription_items",
                            Set.of(
                                "id",
                                "record_version_id",
                                "sort_order",
                                "medicine_name",
                                "active_ingredient",
                                "dosage_instruction",
                                "quantity_text"),
                        "vital_signs",
                            Set.of(
                                "id",
                                "record_version_id",
                                "blood_pressure",
                                "heart_rate",
                                "temperature",
                                "spo2",
                                "weight",
                                "height",
                                "bmi"),
                        "record_attachments",
                            Set.of(
                                "id",
                                "record_version_id",
                                "file_name",
                                "file_type",
                                "file_size_label",
                                "uploaded_at"));
                for (var entry : columns.entrySet()) {
                  String table =
                      "PUBLIC".equals(schema)
                          ? entry.getKey().toUpperCase(Locale.ROOT)
                          : entry.getKey();
                  var seen = new HashSet<String>();
                  try (var r = m.getColumns(null, schema, table, null)) {
                    while (r.next())
                      if (table.equalsIgnoreCase(r.getString("TABLE_NAME")))
                        seen.add(r.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                  }
                  if (!seen.containsAll(entry.getValue())) throw unsupported();
                }
                var fks =
                    Map.of(
                        "medical_records.patient_id",
                        "public.patients.id",
                        "medical_records.doctor_id",
                        "public.doctors.id",
                        "medical_records.specialty_id",
                        "public.specialties.id",
                        "record_versions.record_id",
                        "public.medical_records.id",
                        "record_versions.created_by",
                        "public.users.id",
                        "prescription_items.record_version_id",
                        "public.record_versions.id",
                        "vital_signs.record_version_id",
                        "public.record_versions.id",
                        "record_attachments.record_version_id",
                        "public.record_versions.id");
                for (var entry : fks.entrySet()) {
                  String[] key = entry.getKey().split("\\.");
                  String table = "PUBLIC".equals(schema) ? key[0].toUpperCase(Locale.ROOT) : key[0];
                  if (!grants.target(m, schema, table, key[1]).equals(entry.getValue()))
                    throw unsupported();
                }
                return null;
              });
    } catch (org.springframework.dao.DataAccessException e) {
      throw new ApiException(503, "SHARED_METADATA_UNAVAILABLE");
    }
  }

  private static final String FROM =
      " FROM public.medical_records r JOIN public.patients p ON p.id=r.patient_id JOIN public.users"
          + " u ON u.id=p.user_id JOIN public.specialties s ON s.id=r.specialty_id";
  private static final String ACCESS =
      " u.role='PATIENT' AND u.enabled=true AND EXISTS (SELECT 1 FROM public.access_grants g WHERE"
          + " g.patient_id=r.patient_id AND g.doctor_id=? AND g.status='ACTIVE' AND g.revoked=false"
          + " AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP))";
  private static final String FIELDS =
      "r.id,r.record_code,r.patient_id,p.patient_code,u.full_name,r.doctor_id,s.name AS"
          + " specialty,r.visit_at,r.reason,r.status,r.current_version,r.created_at";

  static String time(ResultSet r, String field) throws SQLException {
    var t = r.getObject(field, LocalDateTime.class);
    return t == null ? null : t.toString();
  }

  private Summary summary(ResultSet r, int row) throws SQLException {
    return new Summary(
        r.getLong("id"),
        r.getString("record_code"),
        r.getLong("patient_id"),
        r.getString("patient_code"),
        r.getString("full_name"),
        r.getLong("doctor_id"),
        r.getString("specialty"),
        time(r, "visit_at"),
        r.getString("reason"),
        r.getString("status"),
        r.getInt("current_version"),
        time(r, "created_at"));
  }

  public Page<Summary> history(
      long grantDoctor, long author, String patientCode, Long patient, Pageable page) {
    String where = " WHERE " + ACCESS;
    var args = new ArrayList<Object>();
    args.add(grantDoctor);
    if (patient == null) {
      where += " AND r.doctor_id=?";
      args.add(author);
    } else {
      where += " AND r.patient_id=?";
      args.add(patient);
    }
    if (patientCode != null && !patientCode.isBlank()) {
      where += " AND p.patient_code=?";
      args.add(patientCode.trim());
    }
    long count = jdbc.queryForObject("SELECT count(*)" + FROM + where, Long.class, args.toArray());
    args.add(page.getPageSize());
    args.add(page.getOffset());
    var rows =
        jdbc.query(
            "SELECT "
                + FIELDS
                + FROM
                + where
                + " ORDER BY r.visit_at DESC,r.id DESC LIMIT ? OFFSET ?",
            this::summary,
            args.toArray());
    return new PageImpl<>(rows, page, count);
  }

  public Summary allowed(long id, long doctor) {
    var rows =
        jdbc.query(
            "SELECT " + FIELDS + FROM + " WHERE r.id=? AND " + ACCESS, this::summary, id, doctor);
    if (rows.isEmpty()) throw new ApiException(403, "PATIENT_READ_PERMISSION_REQUIRED");
    return rows.get(0);
  }

  public Page<VersionSummary> versions(long record, Pageable page) {
    long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM public.record_versions WHERE record_id=?", Long.class, record);
    var rows =
        jdbc.query(
            "SELECT id,version_no,created_by,created_at FROM public.record_versions WHERE"
                + " record_id=? ORDER BY version_no DESC LIMIT ? OFFSET ?",
            (r, n) ->
                new VersionSummary(
                    r.getLong("id"),
                    r.getInt("version_no"),
                    r.getLong("created_by"),
                    time(r, "created_at")),
            record,
            page.getPageSize(),
            page.getOffset());
    return new PageImpl<>(rows, page, count);
  }

  public Version version(long record, int number) {
    var base =
        jdbc.query(
            "SELECT id,version_no,created_by,created_at,diagnosis,prescription,note,room_name,"
                + "visit_type,secondary_diagnosis,treatment_direction,next_visit_date,"
                + "next_visit_note,data_hash,tx_hash,chain_status FROM public.record_versions"
                + " WHERE record_id=? AND version_no=?",
            (r, n) ->
                new Version(
                    r.getLong("id"),
                    r.getInt("version_no"),
                    r.getLong("created_by"),
                    time(r, "created_at"),
                    r.getString("diagnosis"),
                    r.getString("prescription"),
                    r.getString("note"),
                    r.getString("room_name"),
                    r.getString("visit_type"),
                    r.getString("secondary_diagnosis"),
                    r.getString("treatment_direction"),
                    r.getString("next_visit_date"),
                    r.getString("next_visit_note"),
                    r.getString("data_hash"),
                    r.getString("tx_hash"),
                    r.getString("chain_status"),
                    "UNVERIFIED_PUBLIC_CANONICAL",
                    List.of(),
                    null,
                    List.of(),
                    false),
            record,
            number);
    if (base.isEmpty()) throw new ApiException(404, "SHARED_VERSION_MISSING");
    if (base.size() != 1) throw new ApiException(409, "SHARED_VERSION_AMBIGUOUS");
    var v = base.get(0);
    var medicines =
        jdbc.query(
            "SELECT sort_order,medicine_name,active_ingredient,dosage_instruction,quantity_text"
                + " FROM public.prescription_items WHERE record_version_id=? ORDER BY"
                + " sort_order,id",
            (r, n) ->
                new Medicine(
                    r.getInt(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)),
            v.id());
    var vitals =
        jdbc.query(
            "SELECT blood_pressure,heart_rate,temperature,spo2,weight,height,bmi FROM"
                + " public.vital_signs WHERE record_version_id=?",
            (r, n) ->
                new Vitals(
                    r.getString(1),
                    r.getObject(2, Integer.class),
                    r.getBigDecimal(3),
                    r.getObject(4, Integer.class),
                    r.getBigDecimal(5),
                    r.getBigDecimal(6),
                    r.getBigDecimal(7)),
            v.id());
    if (vitals.size() > 1) throw new ApiException(409, "SHARED_VERSION_AMBIGUOUS");
    // No file_url is selected or exposed: private-download contract is not yet agreed.
    var files =
        jdbc.query(
            "SELECT id,file_name,file_type,file_size_label,uploaded_at FROM"
                + " public.record_attachments WHERE record_version_id=? ORDER BY id",
            (r, n) ->
                new File(
                    r.getLong(1),
                    r.getString(2),
                    r.getString(3),
                    r.getString(4),
                    time(r, "uploaded_at")),
            v.id());
    return new Version(
        v.id(),
        v.versionNumber(),
        v.createdByUserId(),
        v.createdAt(),
        v.diagnosis(),
        v.prescription(),
        v.note(),
        v.roomName(),
        v.visitType(),
        v.secondaryDiagnosis(),
        v.treatmentDirection(),
        v.nextVisitDate(),
        v.nextVisitNote(),
        v.storedDataHash(),
        v.storedTransactionHash(),
        v.storedChainStatus(),
        v.hashVerification(),
        medicines,
        vitals.isEmpty() ? null : vitals.get(0),
        files,
        false);
  }
}
