package com.blockchaincb.demo.shared;

import static org.junit.jupiter.api.Assertions.*;

import com.blockchaincb.demo.exception.ApiException;
import java.lang.reflect.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class SharedGrantContractTest {
  SingleConnectionDataSource data;
  JdbcTemplate jdbc;
  SharedGrantRepository repository;

  @BeforeEach
  void setup() {
    data =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "", true);
    jdbc = new JdbcTemplate(data);
    repository = new SharedGrantRepository(jdbc);
    jdbc.execute("CREATE TABLE public.users(id BIGINT PRIMARY KEY)");
    jdbc.execute(
        "CREATE TABLE public.doctors(id BIGINT PRIMARY KEY,user_id BIGINT REFERENCES"
            + " public.users(id))");
    jdbc.execute(
        "CREATE TABLE public.patients(id BIGINT PRIMARY KEY,user_id BIGINT REFERENCES"
            + " public.users(id),patient_code VARCHAR(128) UNIQUE)");
  }

  @AfterEach
  void close() {
    data.destroy();
  }

  void grants(String target) {
    jdbc.execute(
        "CREATE TABLE public.access_grants(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY"
            + " KEY,patient_id BIGINT REFERENCES public.patients(id),doctor_id BIGINT REFERENCES "
            + target
            + "(id),status VARCHAR(20),revoked BOOLEAN,expires_at TIMESTAMP WITH TIME ZONE)");
  }

  @Test
  void detectsDoctorProfileFk() {
    grants("public.doctors");
    assertFalse(repository.contract().doctorReferencesUser());
  }

  @Test
  void detectsUserFkInsteadOfAssumingDoctorAndUserIdsMatch() {
    grants("public.users");
    assertTrue(repository.contract().doctorReferencesUser());
  }

  @Test
  void refusesUnknownForeignKey() {
    grants("public.patients");
    assertEquals(
        "SHARED_SCHEMA_UNVERIFIED",
        assertThrows(ApiException.class, () -> repository.contract()).getCode());
  }

  @Test
  void refusesAdditionalRequiredColumnWithoutDefault() {
    grants("public.doctors");
    jdbc.execute("ALTER TABLE public.access_grants ADD mandatory_scope VARCHAR(20) NOT NULL");
    assertThrows(ApiException.class, () -> repository.contract());
  }

  @Test
  void featureIsExplicitlyDisabledAndRecordIdsCannotMix() {
    var off = new SharedIntegrationSettings(false);
    assertThrows(ApiException.class, off::requireEnabled);
    assertDoesNotThrow(off::requireLegacyRecords);
    var on = new SharedIntegrationSettings(true);
    assertDoesNotThrow(on::requireEnabled);
    assertThrows(ApiException.class, on::requireLegacyRecords);
  }

  private Object call(Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private SharedGrantRepository withBooleanMetadata(int jdbcType, String typeName) {
    var source =
        new org.springframework.jdbc.datasource.DelegatingDataSource(data) {
          @Override
          public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            var metadata = connection.getMetaData();
            var adapted =
                (DatabaseMetaData)
                    Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[] {DatabaseMetaData.class},
                        (p, m, a) -> {
                          Object result = call(m, metadata, a);
                          if (!m.getName().equals("getColumns")) return result;
                          var rows = (ResultSet) result;
                          return Proxy.newProxyInstance(
                              getClass().getClassLoader(),
                              new Class[] {ResultSet.class},
                              (rp, rm, ra) -> {
                                if (ra != null
                                    && ra.length == 1
                                    && "revoked".equalsIgnoreCase(rows.getString("COLUMN_NAME"))) {
                                  if (rm.getName().equals("getInt") && "DATA_TYPE".equals(ra[0]))
                                    return jdbcType;
                                  if (rm.getName().equals("getString") && "TYPE_NAME".equals(ra[0]))
                                    return typeName;
                                }
                                return call(rm, rows, ra);
                              });
                        });
            return (Connection)
                Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[] {Connection.class},
                    (p, m, a) ->
                        m.getName().equals("getMetaData") ? adapted : call(m, connection, a));
          }
        };
    return new SharedGrantRepository(new JdbcTemplate(source));
  }

  @Test
  void acceptsPostgresqlBoolReportedAsJdbcBit() throws Exception {
    grants("public.doctors");
    int pgBoolean = new org.postgresql.jdbc.TypeInfoCache(null, 0).getSQLType("bool");
    assertEquals(Types.BIT, pgBoolean);
    assertFalse(withBooleanMetadata(pgBoolean, "bool").contract().doctorReferencesUser());
  }

  @Test
  void rejectsPostgresqlBitStringInsteadOfTreatingItAsBoolean() {
    grants("public.doctors");
    assertThrows(ApiException.class, () -> withBooleanMetadata(Types.BIT, "bit").contract());
  }

  @Test
  void connectionFailureIsNotMisreportedAsWrongSchema() {
    var unavailable =
        new org.springframework.jdbc.datasource.AbstractDataSource() {
          @Override
          public Connection getConnection() throws SQLException {
            throw new SQLException("PRIVATE_CONNECTION_DETAIL", "08001");
          }

          @Override
          public Connection getConnection(String user, String password) throws SQLException {
            return getConnection();
          }
        };
    var failure =
        assertThrows(
            ApiException.class,
            () -> new SharedGrantRepository(new JdbcTemplate(unavailable)).contract());
    assertEquals("SHARED_METADATA_UNAVAILABLE", failure.getCode());
    assertFalse(failure.getMessage().contains("PRIVATE_CONNECTION_DETAIL"));
  }
}
