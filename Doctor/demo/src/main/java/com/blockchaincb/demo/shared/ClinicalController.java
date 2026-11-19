package com.blockchaincb.demo.shared;
import com.blockchaincb.demo.security.DoctorPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController @RequestMapping("/api/doctor")
public class ClinicalController {
  private final ClinicalService service;
  public ClinicalController(ClinicalService service){this.service=service;}
  @GetMapping("/shared-patients/{patient}/clinical-access")
  public Map<String,Object> access(@AuthenticationPrincipal DoctorPrincipal a,@PathVariable long patient){return service.capability(a,patient);}
  @PostMapping("/shared-records") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Long> create(@AuthenticationPrincipal DoctorPrincipal a,@Valid @RequestBody ClinicalRequest q){return Map.of("id",service.create(a,q));}
  @PostMapping("/shared-records/{id}/versions") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Long> update(@AuthenticationPrincipal DoctorPrincipal a,@PathVariable long id,@Valid @RequestBody ClinicalRequest.Update q){return Map.of("id",service.update(a,id,q));}
  @GetMapping("/shared-records/{id}/versions/{number}/integrity")
  public Map<String,Object> hash(@AuthenticationPrincipal DoctorPrincipal a,@PathVariable long id,@PathVariable int number){return service.proof(a,id,number);}
  @PostMapping("/shared-patients/{patient}/uploads")
  public Map<String,Long> upload(@AuthenticationPrincipal DoctorPrincipal a,@PathVariable long patient,@RequestParam MultipartFile file)throws java.io.IOException{return Map.of("id",service.upload(a,patient,file.getOriginalFilename(),file.getBytes()));}
  @GetMapping("/shared-records/{id}/versions/{number}/attachments/{file}")
  public ResponseEntity<byte[]> download(@AuthenticationPrincipal DoctorPrincipal a,@PathVariable long id,@PathVariable int number,@PathVariable long file){var d=service.download(a,id,number,file);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(d.type())).header("Content-Disposition","attachment; filename=medical-attachment").header("X-Content-Type-Options","nosniff").body(d.bytes());}
}
