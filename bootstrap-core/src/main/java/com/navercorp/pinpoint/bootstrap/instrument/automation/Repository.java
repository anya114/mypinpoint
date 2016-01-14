package com.navercorp.pinpoint.bootstrap.instrument.automation;



import java.util.List;
import java.util.Optional;

public interface Repository<T, ID> {
  Optional<T> findOne(ID id);

  List<T> findAll();

  void delete(ID id);

  void add(ID id, T t);
}
