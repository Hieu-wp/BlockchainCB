package com.blockchaincb.demo.shared;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.blockchaincb.demo.security.*;
import com.blockchaincb.demo.storage.PrivateStorage;
import com.blockchaincb.demo.service.HashService;
import com.blockchaincb.demo.exception.ApiException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.dao.*;

class ClinicalCapabilityPostgresTest {
  @Test void connectionFailureIsNotMisreportedAsMissingMigration(){
    var db=mock(JdbcTemplate.class);
    when(db.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Void>>any()))
      .thenThrow(new DataAccessResourceFailureException("PRIVATE_CONNECTION_DETAIL"));
    var service=new ClinicalService(db,mock(SharedGrantRepository.class),mock(SharedRecordRepository.class),
      new SharedIntegrationSettings(true),mock(DoctorIdentityService.class),new HashService(),mock(PrivateStorage.class));
    var error=assertThrows(ApiException.class,service::ready);
    assertEquals("DATABASE_UNAVAILABLE",error.getCode());assertFalse(error.getMessage().contains("PRIVATE_CONNECTION_DETAIL"));
  }
  @Test void missingTableMustNotAbortTheTransactionBeforePatientInfoQuery() throws Exception {
    var db=mock(JdbcTemplate.class);var grants=mock(SharedGrantRepository.class);var reader=mock(SharedRecordRepository.class);
    var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);var columns=mock(ResultSet.class);
    when(connection.getMetaData()).thenReturn(metadata);when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
    when(metadata.getColumns(any(),eq("doctor_backend"),anyString(),any())).thenReturn(columns);
    when(columns.next()).thenReturn(false);
    when(db.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Void>>any()))
      .thenAnswer(i->((ConnectionCallback<Void>)i.getArgument(0)).doInConnection(connection));
    // PostgreSQL marks a transaction aborted after SELECT against a missing relation; H2 does not.
    var aborted=new AtomicBoolean();
    when(db.queryForList(anyString())).thenAnswer(i->{aborted.set(true);throw new BadSqlGrammarException("probe","redacted",new SQLException("missing table","42P01"));});
    when(db.queryForMap(anyString(),eq(3L))).thenAnswer(i->{
      if(aborted.get())throw new DataAccessResourceFailureException("current transaction is aborted: 25P02");
      return Map.of("patient_code","PC-TEST","full_name","Fixture Patient");
    });
    when(grants.contract()).thenReturn(new SharedGrantRepository.Contract(false));when(grants.patientAccountValid(3L)).thenReturn(true);
    var service=new ClinicalService(db,grants,reader,new SharedIntegrationSettings(true),mock(DoctorIdentityService.class),new HashService(),mock(PrivateStorage.class));
    var result=service.capability(new DoctorPrincipal(1L,10L),3L);
    assertEquals(false,result.get("canCreate"));assertEquals("SHARED_WRITE_MIGRATION_REQUIRED",result.get("reason"));
    assertEquals("PC-TEST",result.get("patientCode"));assertFalse(aborted.get());
    verify(db,never()).queryForList(anyString());
  }
}
