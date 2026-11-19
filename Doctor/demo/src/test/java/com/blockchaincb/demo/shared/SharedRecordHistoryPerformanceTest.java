package com.blockchaincb.demo.shared;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.blockchaincb.demo.dto.SharedRecordResponse.Summary;
import com.blockchaincb.demo.exception.ApiException;
import com.blockchaincb.demo.security.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;

class SharedRecordHistoryPerformanceTest {
  @Test void checksEveryDistinctPatientOnceInStableOrderAndStillRejectsRevocation() {
    var records=mock(SharedRecordRepository.class);
    var grants=mock(SharedGrantRepository.class);
    var settings=mock(SharedIntegrationSettings.class);
    var identities=mock(DoctorIdentityService.class);
    var service=new SharedRecordService(records,grants,settings,identities);
    var actor=new DoctorPrincipal(70L,7L);var page=PageRequest.of(0,10);
    when(grants.contract()).thenReturn(new SharedGrantRepository.Contract(false));
    var result=new PageImpl<>(List.of(summary(1,30),summary(2,20),summary(3,30)));
    when(records.history(7,7,null,null,page)).thenReturn(result);
    assertSame(result,service.history(actor,null,null,page));
    var order=inOrder(grants);
    order.verify(grants).contract();
    order.verify(grants).requireReadLocked(20,7);
    order.verify(grants).requireReadLocked(30,7);
    verify(grants,times(1)).requireReadLocked(30,7);
    doThrow(new ApiException(403,"PATIENT_READ_PERMISSION_REQUIRED")).when(grants).requireReadLocked(20,7);
    assertThrows(ApiException.class,()->service.history(actor,null,null,page));
  }
  private Summary summary(long id,long patient) {
    return new Summary(id,"fixture",patient,"fixture","fixture",7,"fixture","2026-01-01T10:00","fixture","COMPLETED",1,"2026-01-01T10:00");
  }
}
