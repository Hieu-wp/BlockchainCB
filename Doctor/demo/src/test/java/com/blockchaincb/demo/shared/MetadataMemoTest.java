package com.blockchaincb.demo.shared;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class MetadataMemoTest {
  @Test void repeatedStructuralChecksReuseOnlySuccessfulResultsWithinTtl(){
    var now=new AtomicLong();var calls=new AtomicInteger();var cache=new MetadataMemo<Integer>(now::get);
    assertEquals(1,cache.get(1000,calls::incrementAndGet));
    for(int i=0;i<50;i++)assertEquals(1,cache.get(1000,calls::incrementAndGet));
    assertEquals(1,calls.get());now.set(1_000_000_001L);assertEquals(2,cache.get(1000,calls::incrementAndGet));
  }
  @Test void expiredEntryIsNotServedWhenRevalidationFails(){
    var now=new AtomicLong();var cache=new MetadataMemo<String>(now::get);
    assertEquals("initial",cache.get(1,()->"initial"));now.set(2_000_000L);
    assertThrows(IllegalStateException.class,()->cache.get(1,()->{throw new IllegalStateException("unavailable");}));
    assertEquals("recovered",cache.get(1,()->"recovered"));
  }
  @Test void disabledCacheAlwaysInspectsAndExceptionsAreNotCached(){
    var cache=new MetadataMemo<Integer>();var calls=new AtomicInteger();
    assertEquals(1,cache.get(0,calls::incrementAndGet));assertEquals(2,cache.get(0,calls::incrementAndGet));
    assertThrows(IllegalStateException.class,()->cache.get(1000,()->{throw new IllegalStateException();}));
    assertEquals(3,cache.get(1000,calls::incrementAndGet));
  }
}
