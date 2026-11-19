package com.blockchaincb.demo.shared;
import com.blockchaincb.demo.blockchain.BlockchainController.TransactionRequest;
import com.blockchaincb.demo.security.DoctorPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated
@RequestMapping("/api/doctor/shared-records/{id}/versions/{number}")
public class SharedAnchorController {
  private final SharedAnchorService service;
  public SharedAnchorController(SharedAnchorService service){this.service=service;}
  @PostMapping("/anchor-payload") public Object prepare(@AuthenticationPrincipal DoctorPrincipal p,@PathVariable @Positive long id,@PathVariable @Positive int number){return service.prepare(p,id,number);}
  @PostMapping("/anchor-transactions") public Object submit(@AuthenticationPrincipal DoctorPrincipal p,@PathVariable @Positive long id,@PathVariable @Positive int number,@Valid @RequestBody TransactionRequest r){return service.submit(p,id,number,r.transactionHash());}
  @GetMapping("/anchor-status") public Object status(@AuthenticationPrincipal DoctorPrincipal p,@PathVariable @Positive long id,@PathVariable @Positive int number){return service.status(p,id,number);}
}
