package com.blockchaincb.demo.repository;

import com.blockchaincb.demo.entity.Attachment;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {}
