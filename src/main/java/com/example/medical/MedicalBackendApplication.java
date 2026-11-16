package com.example.medical;

import com.example.medical.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class MedicalBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedicalBackendApplication.class, args);
    }
    @Bean
    CommandLineRunner initTestPatient(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String hash = passwordEncoder.encode("123456");
            System.out.println("=== BCRYPT HASH CHO 123456: " + hash);

            userRepository.findByEmail("benhnhan1@gmail.com").ifPresentOrElse(
                    user -> {
                        user.setPasswordHash(hash);
                        user.setEnabled(true);
                        userRepository.save(user);
                        System.out.println("=== DA CAP NHAT MAT KHAU 123456 CHO benhnhan1@gmail.com ===");
                    },
                    () -> System.out.println("=== CHUA TIM THAY USER benhnhan1@gmail.com TRONG DB ===")
            );
        };
    }
}

