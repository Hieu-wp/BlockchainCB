package com.blockchaincb.demo.shared;

import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

/** Successful structural checks only. Never cache identity, grants, medical data or errors. */
public final class MetadataMemo<T> {
  private record Entry<T>(T value,long checkedAt) {}
  private volatile Entry<T> entry;
  private final LongSupplier ticker;
  public MetadataMemo(){this(System::nanoTime);}
  MetadataMemo(LongSupplier ticker){this.ticker=ticker;}
  public T get(long ttlMs,Supplier<T> inspect){
    if(ttlMs<0||ttlMs>300000)throw new IllegalArgumentException("Schema cache TTL must be 0..300000 ms");
    if(ttlMs==0){entry=null;return inspect.get();}
    var cached=entry;
    if(cached!=null && ticker.getAsLong()-cached.checkedAt()<TimeUnit.MILLISECONDS.toNanos(ttlMs))return cached.value();
    // No lock while acquiring a connection: a caller may already hold a transaction/connection.
    T result=inspect.get();
    entry=new Entry<>(result,ticker.getAsLong());return result;
  }
}
