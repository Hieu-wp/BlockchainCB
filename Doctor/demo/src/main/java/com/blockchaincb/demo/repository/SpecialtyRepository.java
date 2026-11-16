package com.blockchaincb.demo.repository;

import com.blockchaincb.demo.entity.Specialty;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {}
