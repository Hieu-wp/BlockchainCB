package com.example.medical.controller;

import com.example.medical.entity.Specialty;
import com.example.medical.repository.SpecialtyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/specialties")
@RequiredArgsConstructor
public class SpecialtyController {

    public record SpecialtyDto(Long id, String code, String name) {}

    private final SpecialtyRepository repository;

    /** Do du lieu cho dropdown "Chuyen khoa". */
    @GetMapping
    public List<SpecialtyDto> all() {
        return repository.findAll().stream()
                .map(s -> new SpecialtyDto(s.getId(), s.getCode(), s.getName()))
                .toList();
    }
}
